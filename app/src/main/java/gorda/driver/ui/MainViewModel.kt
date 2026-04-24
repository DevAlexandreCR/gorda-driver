package gorda.driver.ui

import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import gorda.driver.R
import gorda.driver.exceptions.UnsupportedAppVersionException
import gorda.driver.helpers.withTimeout
import gorda.driver.interfaces.DeviceInterface
import gorda.driver.interfaces.LocInterface
import gorda.driver.interfaces.LocType
import gorda.driver.interfaces.RideFees
import gorda.driver.models.Driver
import gorda.driver.models.Service
import gorda.driver.repositories.DriverRepository
import gorda.driver.repositories.ServiceObserverHandle
import gorda.driver.repositories.ServiceRepository
import gorda.driver.services.firebase.Database
import gorda.driver.services.firebase.FirebaseInitializeApp
import gorda.driver.ui.driver.DriverStatusPublisher
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.ServiceEventListener
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.dataclasses.ServiceUpdates
import gorda.driver.utils.Constants
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private val TAG: String = MainViewModel::class.java.toString()
        private const val PRESENCE_HEARTBEAT_SECONDS = 5L
        private const val PRESENCE_WRITE_TIMEOUT_MS = 8_000L
        private const val PRESENCE_ACK_TIMEOUT_MS = 8_000L
        private const val AUTO_PRESENCE_WRITE_TIMEOUT_MS = 4_000L
        private const val AUTO_PRESENCE_ACK_TIMEOUT_MS = 4_000L
        private const val BIND_TIMEOUT_MS = 5_000L
        private const val LOCATION_TIMEOUT_MS = 15_000L
        private const val FIREBASE_SOCKET_WAIT_TIMEOUT_MS = 3_000L
        private const val FIREBASE_SOCKET_WAIT_FALLBACK_ATTEMPTS = 1

        internal fun planManualConnect(hasCachedLocation: Boolean): ManualConnectPlan {
            return if (hasCachedLocation) {
                ManualConnectPlan(
                    source = ManualConnectSource.CACHED_LOCATION,
                    usesImmediateLocation = true
                )
            } else {
                ManualConnectPlan(
                    source = ManualConnectSource.SERVICE_LOCATION,
                    usesImmediateLocation = false
                )
            }
        }

        internal fun canConfirmPresenceAck(
            expectedSessionId: String?,
            observedSessionId: String?
        ): Boolean {
            return expectedSessionId != null && expectedSessionId == observedSessionId
        }

        internal fun automaticReconnectBackoffPolicy(): ReconnectBackoffPolicy {
            return ReconnectBackoffPolicy(
                longArrayOf(0L, 500L, 1_000L, 2_000L, 5_000L, 10_000L)
            )
        }

        internal fun shouldGateAutomaticReconnectOnFirebaseSocket(
            reason: String,
            firebaseConnected: Boolean,
            phase: DriverPresencePhase
        ): Boolean {
            return reason in setOf(
                "network_restored",
                "firebase_disconnected",
                "firebase_socket_timeout"
            ) || !firebaseConnected || phase == DriverPresencePhase.WAITING_FOR_FIREBASE_SOCKET
        }

        internal fun shouldResumeAutomaticReconnectFromSocket(
            desiredOnline: Boolean,
            phase: DriverPresencePhase
        ): Boolean {
            return desiredOnline && phase == DriverPresencePhase.WAITING_FOR_FIREBASE_SOCKET
        }

        internal fun shouldScheduleReconnectFromRecoveredLocation(state: DriverPresenceState): Boolean {
            return state.desiredOnline &&
                !state.actualOnline &&
                state.firebaseConnected &&
                state.fatalStopReason == null
        }

        internal fun shouldKeepDriverFeedConnected(state: DriverPresenceState): Boolean {
            return state.actualOnline || (
                state.desiredOnline && state.phase in setOf(
                    DriverPresencePhase.WAITING_FOR_FIREBASE_SOCKET,
                    DriverPresencePhase.RECONNECTING,
                    DriverPresencePhase.WAITING_FOR_PRESENCE_ACK,
                    DriverPresencePhase.CONNECTED
                )
            )
        }
    }

    internal enum class ManualConnectSource {
        CACHED_LOCATION,
        SERVICE_LOCATION
    }

    internal data class ManualConnectPlan(
        val source: ManualConnectSource,
        val usesImmediateLocation: Boolean
    )

    sealed class DriverLoadState {
        object Idle : DriverLoadState()
        data class Loading(val driverId: String) : DriverLoadState()
        data class Loaded(val driver: Driver) : DriverLoadState()
        data class Failed(val driverId: String) : DriverLoadState()
    }

    enum class DriverPresencePhase {
        DISCONNECTED,
        PRECHECKING,
        WAITING_FOR_BIND,
        WAITING_FOR_LOCATION,
        WRITING_PRESENCE,
        WAITING_FOR_PRESENCE_ACK,
        RECONNECTING,
        WAITING_FOR_FIREBASE_SOCKET,
        CONNECTED,
        DISCONNECTING
    }

    data class DriverPresenceState(
        val desiredOnline: Boolean = false,
        val actualOnline: Boolean = false,
        val phase: DriverPresencePhase = DriverPresencePhase.DISCONNECTED,
        val hasNetwork: Boolean = true,
        val firebaseConnected: Boolean = false,
        val lastError: String? = null,
        val attemptId: Long = 0L,
        val sessionId: String? = null,
        val reconnectReason: String? = null,
        val fatalStopReason: String? = null,
        val reconnectGeneration: Long = 0L
    )

    data class FeeData(
        val totalFee: Double,
        val timeFee: Double,
        val distanceFee: Double,
        val totalDistance: Double,
        val elapsedSeconds: Long
    )

    private val _lastLocation = MutableLiveData<LocationUpdates>()
    private val _driverState = MutableStateFlow<DriverUpdates?>(null)
    val driverStatus: StateFlow<DriverUpdates?> = _driverState.asStateFlow()

    private val _isNetWorkConnected = MutableStateFlow(true)
    val isNetWorkConnected: StateFlow<Boolean> = _isNetWorkConnected.asStateFlow()

    private val _presenceState = MutableStateFlow(DriverPresenceState())
    val presenceState: StateFlow<DriverPresenceState> = _presenceState.asStateFlow()

    private val _driver: MutableLiveData<Driver?> = savedStateHandle.getLiveData(Driver.TAG)
    private val _driverLoadState = MutableLiveData<DriverLoadState>(DriverLoadState.Idle)
    private val _serviceUpdates = MutableLiveData<ServiceUpdates>()
    private val _currentService = MutableLiveData<Service?>()
    private val _nextService = MutableLiveData<Service?>()
    private val _isTripStarted = MutableLiveData(false)
    private val _rideFees = MutableLiveData<RideFees>()
    private val _isLoading = MutableLiveData(false)
    private val _errorTimeout = MutableLiveData(false)
    private val _currentFeeData = MutableLiveData<FeeData>()
    private val _errorMessageRes = MutableLiveData<Int?>()
    private val _unsupportedVersion = MutableLiveData(false)

    private var preferences: SharedPreferences? = null
    private var presenceListener: ValueEventListener? = null
    private var firebaseConnectionListener: ValueEventListener? = null
    private var observedDriverId: String? = null
    private var nextAttemptId: Long = 0L
    private var latestLocation: Location? = null
    private var lastPresenceUpdateAt: Long = 0L
    private var bindTimeoutJob: Job? = null
    private var locationTimeoutJob: Job? = null
    private var presenceWriteTimeoutJob: Job? = null
    private var presenceAckTimeoutJob: Job? = null
    private var firebaseSocketWaitJob: Job? = null
    private var currentServiceObserverHandle: ServiceObserverHandle? = null
    private var nextServiceObserverHandle: ServiceObserverHandle? = null
    private var serviceObserversActive = false
    private var currentAttemptAutomatic = false
    private var firebaseSocketFallbackAttemptsRemaining = 0
    private var pendingRecoveryObserverRefresh = false
    private var lastNetworkRestoredAtMs: Long? = null
    private var lastFirebaseSocketConnectedAtMs: Long? = null
    private var lastPresenceAckAtMs: Long? = null

    private val pendingLocationUpdates = mutableListOf<Location>()
    private val driverStatusPublisher = DriverStatusPublisher()
    private val reconnectCoordinator by lazy {
        ReconnectCoordinator(
            scope = viewModelScope,
            policy = automaticReconnectBackoffPolicy(),
            onAttempt = { attemptIndex, reason ->
                onReconnectAttempt(attemptIndex, reason)
            }
        )
    }

    private val nextServiceListener: ServiceEventListener = ServiceEventListener { service ->
        if (service == null) {
            _nextService.postValue(null)
        } else {
            driver.value?.let {
                if (it.id == service.driver_id && currentService.value?.id != service.id) {
                    _nextService.postValue(service)
                } else {
                    _nextService.postValue(null)
                }
            }
        }
    }
    private val currentServiceListener: ServiceEventListener = ServiceEventListener { service ->
        if (service == null) {
            _currentService.postValue(null)
        } else {
            _currentService.postValue(service)
        }
    }

    val lastLocation: LiveData<LocationUpdates> = _lastLocation
    var driver: LiveData<Driver?> = _driver
    val driverLoadState: LiveData<DriverLoadState> = _driverLoadState
    var serviceUpdates: LiveData<ServiceUpdates> = _serviceUpdates
    val currentService: LiveData<Service?> = _currentService
    val nextService: LiveData<Service?> = _nextService
    val isTripStarted: LiveData<Boolean> = _isTripStarted
    val rideFees: LiveData<RideFees> = _rideFees
    val isLoading: LiveData<Boolean> = _isLoading
    val errorTimeout: LiveData<Boolean> = _errorTimeout
    val currentFeeData: LiveData<FeeData> = _currentFeeData
    val errorMessageRes: LiveData<Int?> = _errorMessageRes
    val unsupportedVersion: LiveData<Boolean> = _unsupportedVersion

    init {
        startFirebaseConnectionObservation()
    }

    fun initializePreferences(preferences: SharedPreferences) {
        this.preferences = preferences
        val desiredOnline = preferences.getBoolean(Constants.DRIVER_DESIRED_ONLINE, false)
        updatePresenceState { current ->
            current.copy(desiredOnline = desiredOnline)
        }
    }

    fun setRideFees(fees: RideFees) {
        _rideFees.postValue(fees)
    }

    fun setLoading(loading: Boolean) {
        _isLoading.postValue(loading)
    }

    fun setErrorTimeout(error: Boolean) {
        _errorTimeout.postValue(error)
    }

    fun consumeErrorMessage() {
        _errorMessageRes.postValue(null)
    }

    fun consumeUnsupportedVersion() {
        _unsupportedVersion.postValue(false)
    }

    fun changeConnectTripService(connect: Boolean) {
        _isTripStarted.postValue(connect)
    }

    fun changeNetWorkStatus(isConnected: Boolean) {
        val previousState = _isNetWorkConnected.value
        Log.d(TAG, "Network status changed: $isConnected (previous: $previousState)")

        _isNetWorkConnected.value = isConnected
        updatePresenceState { current ->
            current.copy(hasNetwork = isConnected)
        }

        if (isConnected && shouldAutoRecover()) {
            lastNetworkRestoredAtMs = System.currentTimeMillis()
            logRecoveryEvent(
                "network_restored_detected",
                "desiredOnline=${_presenceState.value.desiredOnline}"
            )
            scheduleReconnectAttempt(
                reason = "network_restored",
                resetBackoff = true,
                forceReschedule = true
            )
        }
    }

    fun setServiceUpdateStartLocation(starLoc: LocType) {
        _serviceUpdates.postValue(ServiceUpdates.setStarLoc(starLoc))
    }

    fun startServiceObservers() {
        serviceObserversActive = true
        currentServiceObserverHandle?.dispose()
        nextServiceObserverHandle?.dispose()
        currentServiceObserverHandle = ServiceRepository.observeCurrentService(currentServiceListener)
        nextServiceObserverHandle = ServiceRepository.observeConnectionService(nextServiceListener)
    }

    fun stopServiceObservers() {
        serviceObserversActive = false
        currentServiceObserverHandle?.dispose()
        currentServiceObserverHandle = null
        nextServiceObserverHandle?.dispose()
        nextServiceObserverHandle = null
    }

    fun completeCurrentService() {
        _currentService.postValue(null)
    }

    fun setServiceUpdateDistTime(distance: Int, time: Int) {
        _serviceUpdates.postValue(ServiceUpdates.distanceTime(distance, time))
    }

    fun setServiceUpdateApply(service: Service) {
        driver.value?.let {
            _serviceUpdates.postValue(ServiceUpdates.setServiceApply(service, it))
            service.onStatusChange(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.getValue(String::class.java)
                    status?.let {
                        _serviceUpdates.postValue(ServiceUpdates.Status(status))
                        when (status) {
                            Service.STATUS_CANCELED,
                            Service.STATUS_IN_PROGRESS -> {
                                snapshot.key?.let { key ->
                                    _isLoading.postValue(true)
                                    ServiceRepository.validateAssignment(key).addOnCompleteListener {
                                        _isLoading.postValue(false)
                                    }.withTimeout {
                                        _isLoading.postValue(false)
                                        setErrorTimeout(true)
                                    }
                                }
                                service.getStatusReference().removeEventListener(this)
                            }
                            else -> {}
                        }
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(TAG, error.message)
                }
            })
        }
    }

    fun updateLocation(location: Location) {
        latestLocation = location
        val state = _presenceState.value
        if (!state.desiredOnline || !state.actualOnline || !state.firebaseConnected || !state.hasNetwork) {
            queuePendingLocation(location)
            if (shouldAutoRecover() && shouldScheduleReconnectFromRecoveredLocation(state)) {
                scheduleReconnectAttempt(
                    reason = "location_available",
                    resetBackoff = true,
                    forceReschedule = true
                )
            }
        } else {
            maybeSendPresenceHeartbeat(location, force = false)
        }
        _lastLocation.postValue(LocationUpdates.lastLocation(location))
    }

    fun getDriver(driverId: String) {
        _isLoading.postValue(true)
        _driver.postValue(null)
        _driverLoadState.postValue(DriverLoadState.Loading(driverId))
        DriverRepository.getDriver(driverId) { driver ->
            _isLoading.postValue(false)
            if (driver != null) {
                _driver.postValue(driver)
                _driverLoadState.postValue(DriverLoadState.Loaded(driver))
            } else {
                _driver.postValue(null)
                _driverLoadState.postValue(DriverLoadState.Failed(driverId))
            }
            savedStateHandle[Driver.TAG] = driver
        }
    }

    fun startPresenceObservation(driverId: String) {
        if (observedDriverId == driverId && presenceListener != null) {
            return
        }

        stopPresenceObservation()
        observedDriverId = driverId
        presenceListener = DriverRepository.observePresence(driverId) { presence ->
            val currentState = _presenceState.value
            val localAttemptActive = currentState.phase in setOf(
                DriverPresencePhase.PRECHECKING,
                DriverPresencePhase.WAITING_FOR_BIND,
                DriverPresencePhase.WAITING_FOR_LOCATION,
                DriverPresencePhase.WRITING_PRESENCE,
                DriverPresencePhase.WAITING_FOR_FIREBASE_SOCKET,
                DriverPresencePhase.DISCONNECTING
            )

            if (canConfirmPresenceAck(currentState.sessionId, presence?.sessionId)) {
                lastPresenceUpdateAt = presence?.lastSeenAt ?: currentUnixSeconds()
                lastPresenceAckAtMs = System.currentTimeMillis()
                reconnectCoordinator.cancelAndReset()
                presenceAckTimeoutJob?.cancel()
                firebaseSocketWaitJob?.cancel()
                _isLoading.postValue(false)
                Log.i(
                    TAG,
                    "event=presence_ack_confirmed reason=matching_session firebaseConnected=${currentState.firebaseConnected} sessionId=${currentState.sessionId}"
                )
                updatePresenceState { state ->
                    state.copy(
                        actualOnline = true,
                        phase = DriverPresencePhase.CONNECTED,
                        lastError = null,
                        reconnectReason = null,
                        fatalStopReason = null
                    )
                }
                if (pendingRecoveryObserverRefresh) {
                    pendingRecoveryObserverRefresh = false
                    bumpReconnectGeneration()
                }
                logRecoveryEvent(
                    "presence_ack_confirmed",
                    "attemptId=${currentState.attemptId} reason=${currentState.reconnectReason} " +
                        "networkRecoveredMs=${elapsedSince(lastNetworkRestoredAtMs)} " +
                        "socketReadyToAckMs=${elapsedBetween(lastFirebaseSocketConnectedAtMs, lastPresenceAckAtMs)} " +
                        "ackTimestampMs=$lastPresenceAckAtMs"
                )
                flushPendingLocationUpdates()
                return@observePresence
            }

            if (localAttemptActive) {
                Log.d(
                    TAG,
                    "Ignoring presence update while local attempt is active: ${currentState.phase}"
                )
                return@observePresence
            }

            if (!currentState.desiredOnline) {
                updatePresenceState { state ->
                    state.copy(
                        actualOnline = false,
                        phase = DriverPresencePhase.DISCONNECTED,
                        sessionId = null
                    )
                }
                return@observePresence
            }

            val reconnectReason = when {
                presence == null -> "presence_missing"
                currentState.sessionId != null && presence.sessionId != currentState.sessionId -> "session_mismatch"
                else -> "presence_unconfirmed"
            }

            transitionToReconnecting(
                reason = reconnectReason,
                forceReschedule = currentState.firebaseConnected,
                resetBackoff = true
            )
        }

        if (_presenceState.value.desiredOnline) {
            scheduleReconnectAttempt(
                reason = "driver_loaded",
                resetBackoff = true,
                forceReschedule = true
            )
        }
    }

    fun stopPresenceObservation() {
        val driverId = observedDriverId
        val listener = presenceListener
        if (driverId != null && listener != null) {
            DriverRepository.removePresenceObserver(driverId, listener)
        }
        presenceListener = null
        observedDriverId = null
        pendingRecoveryObserverRefresh = false
        cancelPresenceJobs()
        reconnectCoordinator.cancelAndReset()
    }

    fun requestConnect() {
        if (!isNetWorkConnected.value) {
            emitErrorMessage(R.string.connection_lost)
            return
        }

        precheckDriverConnectEligibility(
            emitFeedback = true,
            onAllowed = {
                beginManualPresenceAttempt(immediateLocation = latestKnownLocation())
            }
        )
    }

    fun requestDisconnect() {
        val currentDriver = driver.value ?: return
        val attemptId = nextAttemptId()
        val previousState = _presenceState.value

        cancelPresenceJobs()
        reconnectCoordinator.cancelAndReset()
        persistDesiredOnline(false)
        currentAttemptAutomatic = false
        Log.i(TAG, "event=manual_disconnect driverId=${currentDriver.id} attemptId=$attemptId")

        updatePresenceState { current ->
            current.copy(
                desiredOnline = false,
                actualOnline = false,
                phase = DriverPresencePhase.DISCONNECTING,
                lastError = null,
                attemptId = attemptId,
                sessionId = null,
                reconnectReason = null,
                fatalStopReason = null
            )
        }
        _isLoading.postValue(true)

        currentDriver.disconnect()
            .addOnSuccessListener {
                _isLoading.postValue(false)
                updatePresenceState { current ->
                    current.copy(
                        desiredOnline = false,
                        actualOnline = false,
                        phase = DriverPresencePhase.DISCONNECTED,
                        lastError = null,
                        sessionId = null,
                        reconnectReason = null,
                        fatalStopReason = null
                    )
                }
                pendingRecoveryObserverRefresh = false
            }
            .addOnFailureListener { exception ->
                _isLoading.postValue(false)
                Log.e(
                    TAG,
                    "event=manual_disconnect_failed driverId=${currentDriver.id} message=${exception.message}"
                )
                persistDesiredOnline(previousState.desiredOnline)
                _presenceState.value = previousState
                publishDriverStatus(previousState)
                if (shouldAutoRecover()) {
                    scheduleReconnectAttempt(
                        reason = "manual_disconnect_failed",
                        resetBackoff = true,
                        forceReschedule = true
                    )
                }
            }
    }

    fun onLocationServiceBound() {
        val currentState = _presenceState.value
        if (currentState.phase != DriverPresencePhase.WAITING_FOR_BIND) {
            return
        }

        bindTimeoutJob?.cancel()
        Log.i(TAG, "event=phase_transition phase=WAITING_FOR_LOCATION attemptId=${currentState.attemptId}")
        updatePresenceState { state ->
            state.copy(phase = DriverPresencePhase.WAITING_FOR_LOCATION)
        }
        scheduleLocationTimeout(currentState.attemptId)
    }

    fun onLocationServiceBindFailed() {
        val currentState = _presenceState.value
        if (
            currentState.phase != DriverPresencePhase.WAITING_FOR_BIND &&
            currentState.phase != DriverPresencePhase.WAITING_FOR_LOCATION
        ) {
            return
        }
        failManualPresenceAttempt(
            attemptId = currentState.attemptId,
            lastError = "bind_failed",
            messageRes = R.string.error_timeout
        )
    }

    fun onInitialConnectionLocation(location: Location) {
        latestLocation = location
        val currentState = _presenceState.value
        if (currentState.phase != DriverPresencePhase.WAITING_FOR_LOCATION) {
            return
        }

        locationTimeoutJob?.cancel()
        Log.i(TAG, "event=manual_connect_location source=service_location attemptId=${currentState.attemptId}")
        writePresence(location, currentState.attemptId, automatic = false)
    }

    fun flushPendingLocationUpdates() {
        val latestPendingLocation = pendingLocationUpdates.lastOrNull()
        if (latestPendingLocation != null) {
            maybeSendPresenceHeartbeat(latestPendingLocation, force = true)
        }
        pendingLocationUpdates.clear()
    }

    fun updateDriverDevice(driverID: String, device: DeviceInterface?): Task<Void> {
        return DriverRepository.updateDevice(driverID, device)
    }

    fun updateFeeData(
        totalFee: Double,
        timeFee: Double,
        distanceFee: Double,
        totalDistance: Double,
        elapsedSeconds: Long
    ) {
        val feeData = FeeData(totalFee, timeFee, distanceFee, totalDistance, elapsedSeconds)
        _currentFeeData.postValue(feeData)
    }

    fun handleDriverLoadFailed() {
        handleFatalStop(reason = "driver_load_failed")
    }

    fun handleAuthLost() {
        handleFatalStop(reason = "auth_lost")
    }

    fun getApplyRestrictionMessageRes(driver: Driver): Int? {
        return when (driver.availability?.reason) {
            "negative_balance_percentage" -> R.string.driver_apply_balance_blocked_message
            "enabled_disabled" -> R.string.driver_apply_disabled_message
            else -> if (!driver.canApply()) {
                R.string.driver_apply_disabled_message
            } else {
                null
            }
        }
    }

    private fun beginManualPresenceAttempt(immediateLocation: Location?) {
        val state = _presenceState.value
        if (isPresenceAttemptActive(state)) {
            return
        }

        val attemptId = nextAttemptId()
        val nextSessionId = UUID.randomUUID().toString()
        currentAttemptAutomatic = false
        cancelPresenceJobs()
        reconnectCoordinator.cancelAndReset()
        persistDesiredOnline(true)
        _isLoading.postValue(true)

        updatePresenceState { current ->
            current.copy(
                desiredOnline = true,
                actualOnline = false,
                phase = DriverPresencePhase.PRECHECKING,
                lastError = null,
                attemptId = attemptId,
                sessionId = nextSessionId,
                reconnectReason = null,
                fatalStopReason = null
            )
        }

        val plan = planManualConnect(immediateLocation != null)
        Log.i(
            TAG,
            "event=manual_connect_start source=${plan.source.name.lowercase()} hasImmediateLocation=${plan.usesImmediateLocation} attemptId=$attemptId"
        )

        if (immediateLocation != null) {
            writePresence(immediateLocation, attemptId, automatic = false)
            return
        }

        Log.i(TAG, "event=phase_transition phase=WAITING_FOR_BIND attemptId=$attemptId reason=service_location_required")
        updatePresenceState { current ->
            current.copy(phase = DriverPresencePhase.WAITING_FOR_BIND)
        }
        scheduleBindTimeout(attemptId)
    }

    private fun beginAutomaticReconnectAttempt(reason: String) {
        if (!shouldAutoRecover()) {
            reconnectCoordinator.cancelAndReset()
            return
        }

        val currentDriver = driver.value
        if (currentDriver == null) {
            handleFatalStop(reason = "driver_missing")
            return
        }

        val state = _presenceState.value
        if (isPresenceAttemptActive(state) || state.phase == DriverPresencePhase.WAITING_FOR_PRESENCE_ACK) {
            return
        }

        val location = latestLocation ?: pendingLocationUpdates.lastOrNull()
        if (location == null) {
            onAutomaticReconnectFailure(reason = "location_unavailable")
            return
        }

        val attemptId = nextAttemptId()
        val nextSessionId = UUID.randomUUID().toString()
        currentAttemptAutomatic = true
        cancelPresenceJobs()
        _isLoading.postValue(true)

        updatePresenceState { current ->
            current.copy(
                desiredOnline = true,
                actualOnline = false,
                phase = DriverPresencePhase.RECONNECTING,
                lastError = null,
                attemptId = attemptId,
                sessionId = nextSessionId,
                reconnectReason = reason,
                fatalStopReason = null
            )
        }

        writePresence(location, attemptId, automatic = true)
    }

    private fun precheckDriverConnectEligibility(
        emitFeedback: Boolean,
        onAllowed: (Driver) -> Unit
    ) {
        val currentDriver = driver.value ?: return
        _isLoading.postValue(true)

        DriverRepository.getDriver(currentDriver.id) { refreshedDriver ->
            if (refreshedDriver == null) {
                _isLoading.postValue(false)
                if (emitFeedback) {
                    emitErrorMessage(R.string.error_timeout)
                }
                return@getDriver
            }

            _driver.postValue(refreshedDriver)
            savedStateHandle[Driver.TAG] = refreshedDriver

            val restrictionMessageRes = getConnectRestrictionMessageRes(refreshedDriver)
            if (restrictionMessageRes != null) {
                _isLoading.postValue(false)
                persistDesiredOnline(false)
                updatePresenceState { current ->
                    current.copy(
                        desiredOnline = false,
                        actualOnline = false,
                        phase = DriverPresencePhase.DISCONNECTED,
                        lastError = refreshedDriver.availability?.reason,
                        sessionId = null,
                        reconnectReason = null,
                        fatalStopReason = refreshedDriver.availability?.reason
                    )
                }
                if (emitFeedback) {
                    emitErrorMessage(restrictionMessageRes)
                }
                Log.w(
                    TAG,
                    "event=connect_precheck_blocked driverId=${refreshedDriver.id} reason=${refreshedDriver.availability?.reason}"
                )
                return@getDriver
            }

            DriverRepository.validateDriverVersion { result ->
                result.onSuccess {
                    onAllowed(refreshedDriver)
                }.onFailure { exception ->
                    if (exception is UnsupportedAppVersionException) {
                        handleFatalStop(
                            reason = "version_unsupported",
                            unsupportedVersion = true
                        )
                    } else {
                        _isLoading.postValue(false)
                        if (emitFeedback) {
                            emitErrorMessage(R.string.error_timeout)
                        }
                    }
                }
            }
        }
    }

    private fun getConnectRestrictionMessageRes(driver: Driver): Int? {
        return when (driver.availability?.reason) {
            "negative_balance_percentage" -> R.string.driver_balance_blocked_message
            "enabled_disabled" -> R.string.driver_disabled_account_message
            else -> if (!driver.canGoOnline()) {
                R.string.driver_disabled_account_message
            } else {
                null
            }
        }
    }

    private fun writePresence(location: Location, attemptId: Long, automatic: Boolean) {
        val currentDriver = driver.value ?: return
        val sessionId = _presenceState.value.sessionId ?: UUID.randomUUID().toString()
        val lastSeenAt = currentUnixSeconds()

        latestLocation = location
        _isLoading.postValue(true)
        Log.i(TAG, "event=phase_transition phase=WRITING_PRESENCE attemptId=$attemptId automatic=$automatic")
        updatePresenceState { current ->
            current.copy(
                actualOnline = false,
                phase = DriverPresencePhase.WRITING_PRESENCE,
                sessionId = sessionId,
                lastError = null
            )
        }
        schedulePresenceWriteTimeout(attemptId, automatic)

        currentDriver.connect(
            object : LocInterface {
                override var lat: Double = location.latitude
                override var lng: Double = location.longitude
            },
            sessionId,
            lastSeenAt
        ).addOnSuccessListener {
            val currentState = _presenceState.value
            if (currentState.attemptId != attemptId) {
                return@addOnSuccessListener
            }

            presenceWriteTimeoutJob?.cancel()
            lastPresenceUpdateAt = lastSeenAt
            _isLoading.postValue(false)
            if (currentState.actualOnline && currentState.phase == DriverPresencePhase.CONNECTED) {
                return@addOnSuccessListener
            }
            Log.i(TAG, "event=phase_transition phase=WAITING_FOR_PRESENCE_ACK attemptId=$attemptId automatic=$automatic")
            updatePresenceState { current ->
                current.copy(
                    actualOnline = false,
                    phase = DriverPresencePhase.WAITING_FOR_PRESENCE_ACK,
                    lastError = null
                )
            }
            schedulePresenceAckTimeout(attemptId, automatic)
        }.addOnFailureListener { exception ->
            if (_presenceState.value.attemptId != attemptId) {
                return@addOnFailureListener
            }

            Log.e(
                TAG,
                "event=connect_write_failed driverId=${currentDriver.id} attemptId=$attemptId message=${exception.message}"
            )

            when {
                exception is UnsupportedAppVersionException -> {
                    handleFatalStop(
                        reason = "version_unsupported",
                        unsupportedVersion = true
                    )
                }
                automatic -> {
                    onAutomaticReconnectFailure(reason = "presence_write_failed")
                }
                else -> {
                    failManualPresenceAttempt(
                        attemptId = attemptId,
                        lastError = "presence_write_failed",
                        messageRes = R.string.error_timeout
                    )
                }
            }
        }
    }

    private fun maybeSendPresenceHeartbeat(location: Location, force: Boolean) {
        val currentDriver = driver.value ?: return
        val state = _presenceState.value
        if (!state.desiredOnline || !state.actualOnline || !state.hasNetwork || !state.firebaseConnected) {
            return
        }

        val now = currentUnixSeconds()
        if (!force && now - lastPresenceUpdateAt < PRESENCE_HEARTBEAT_SECONDS) {
            return
        }

        lastPresenceUpdateAt = now
        DriverRepository.updateLocation(
            currentDriver.id,
            object : LocInterface {
                override var lat: Double = location.latitude
                override var lng: Double = location.longitude
            },
            now
        ).addOnFailureListener { exception ->
            Log.e(TAG, "event=presence_heartbeat_failed message=${exception.message}")
            queuePendingLocation(location)
            transitionToReconnecting(
                reason = "presence_write_failed",
                forceReschedule = true,
                resetBackoff = true
            )
        }
    }

    private fun scheduleBindTimeout(attemptId: Long) {
        bindTimeoutJob?.cancel()
        bindTimeoutJob = viewModelScope.launch {
            delay(BIND_TIMEOUT_MS)
            Log.w(TAG, "event=presence_timeout reason=bind_timeout attemptId=$attemptId")
            failManualPresenceAttempt(
                attemptId = attemptId,
                lastError = "bind_timeout",
                messageRes = R.string.error_timeout
            )
        }
    }

    private fun scheduleLocationTimeout(attemptId: Long) {
        locationTimeoutJob?.cancel()
        locationTimeoutJob = viewModelScope.launch {
            delay(LOCATION_TIMEOUT_MS)
            Log.w(TAG, "event=presence_timeout reason=location_timeout attemptId=$attemptId")
            failManualPresenceAttempt(
                attemptId = attemptId,
                lastError = "location_timeout",
                messageRes = R.string.error_timeout
            )
        }
    }

    private fun schedulePresenceWriteTimeout(attemptId: Long, automatic: Boolean) {
        presenceWriteTimeoutJob?.cancel()
        presenceWriteTimeoutJob = viewModelScope.launch {
            delay(if (automatic) AUTO_PRESENCE_WRITE_TIMEOUT_MS else PRESENCE_WRITE_TIMEOUT_MS)
            if (automatic) {
                if (_presenceState.value.attemptId == attemptId) {
                    onAutomaticReconnectFailure(reason = "presence_write_timeout")
                }
            } else {
                failManualPresenceAttempt(
                    attemptId = attemptId,
                    lastError = "presence_write_timeout",
                    messageRes = R.string.error_timeout
                )
            }
        }
    }

    private fun schedulePresenceAckTimeout(attemptId: Long, automatic: Boolean) {
        presenceAckTimeoutJob?.cancel()
        presenceAckTimeoutJob = viewModelScope.launch {
            delay(if (automatic) AUTO_PRESENCE_ACK_TIMEOUT_MS else PRESENCE_ACK_TIMEOUT_MS)
            if (_presenceState.value.attemptId != attemptId) {
                return@launch
            }

            Log.w(TAG, "event=presence_timeout reason=presence_ack_timeout attemptId=$attemptId automatic=$automatic")
            if (automatic) {
                onAutomaticReconnectFailure(reason = "presence_ack_timeout")
            } else {
                failManualPresenceAttempt(
                    attemptId = attemptId,
                    lastError = "presence_ack_timeout",
                    messageRes = R.string.error_timeout
                )
            }
        }
    }

    private fun failManualPresenceAttempt(
        attemptId: Long,
        lastError: String,
        messageRes: Int?
    ) {
        if (_presenceState.value.attemptId != attemptId) {
            return
        }

        currentAttemptAutomatic = false
        cancelPresenceJobs()
        reconnectCoordinator.cancelAndReset()
        _isLoading.postValue(false)
        persistDesiredOnline(false)
        pendingRecoveryObserverRefresh = false
        if (messageRes != null) {
            emitErrorMessage(messageRes)
        }

        updatePresenceState { current ->
            current.copy(
                desiredOnline = false,
                actualOnline = false,
                phase = DriverPresencePhase.DISCONNECTED,
                lastError = lastError,
                sessionId = null,
                reconnectReason = null
            )
        }
    }

    private fun onAutomaticReconnectFailure(reason: String) {
        currentAttemptAutomatic = false
        cancelPresenceJobs()
        _isLoading.postValue(false)
        markObserversForRefreshAfterRecovery(_presenceState.value)
        updatePresenceState { current ->
            current.copy(
                desiredOnline = true,
                actualOnline = false,
                phase = DriverPresencePhase.RECONNECTING,
                lastError = reason,
                sessionId = null,
                reconnectReason = reason
            )
        }
        scheduleReconnectAttempt(reason = reason)
    }

    private fun handleFatalStop(
        reason: String,
        unsupportedVersion: Boolean = false
    ) {
        currentAttemptAutomatic = false
        cancelPresenceJobs()
        reconnectCoordinator.cancelAndReset()
        _isLoading.postValue(false)
        persistDesiredOnline(false)
        if (unsupportedVersion) {
            _unsupportedVersion.postValue(true)
        }
        pendingRecoveryObserverRefresh = false

        updatePresenceState { current ->
            current.copy(
                desiredOnline = false,
                actualOnline = false,
                phase = DriverPresencePhase.DISCONNECTED,
                lastError = reason,
                sessionId = null,
                reconnectReason = null,
                fatalStopReason = reason
            )
        }
    }

    private fun emitErrorMessage(messageRes: Int) {
        _errorMessageRes.postValue(messageRes)
    }

    private fun latestKnownLocation(): Location? {
        return latestLocation ?: pendingLocationUpdates.lastOrNull()
    }

    private fun persistDesiredOnline(desiredOnline: Boolean) {
        preferences?.edit()?.putBoolean(Constants.DRIVER_DESIRED_ONLINE, desiredOnline)?.apply()
    }

    private fun cancelPresenceJobs() {
        bindTimeoutJob?.cancel()
        locationTimeoutJob?.cancel()
        presenceWriteTimeoutJob?.cancel()
        presenceAckTimeoutJob?.cancel()
        firebaseSocketWaitJob?.cancel()
        bindTimeoutJob = null
        locationTimeoutJob = null
        presenceWriteTimeoutJob = null
        presenceAckTimeoutJob = null
        firebaseSocketWaitJob = null
    }

    private fun updatePresenceState(
        transform: (DriverPresenceState) -> DriverPresenceState
    ) {
        val nextState = transform(_presenceState.value)
        _presenceState.value = nextState
        publishDriverStatus(nextState)
    }

    private fun publishDriverStatus(state: DriverPresenceState) {
        viewModelScope.launch {
            driverStatusPublisher.updatesFor(state).forEach { update ->
                _driverState.emit(update)
            }
        }
    }

    private fun nextAttemptId(): Long {
        nextAttemptId += 1
        return nextAttemptId
    }

    private fun currentUnixSeconds(): Long {
        return System.currentTimeMillis() / 1000
    }

    private fun queuePendingLocation(location: Location) {
        pendingLocationUpdates.clear()
        pendingLocationUpdates.add(location)
        Log.d(TAG, "Location queued for reconnect: ${pendingLocationUpdates.size} pending")
    }

    private fun shouldAutoRecover(): Boolean {
        val state = _presenceState.value
        return state.desiredOnline && state.fatalStopReason == null && observedDriverId != null
    }

    private fun isPresenceAttemptActive(state: DriverPresenceState): Boolean {
        return state.phase in setOf(
            DriverPresencePhase.PRECHECKING,
            DriverPresencePhase.WAITING_FOR_BIND,
            DriverPresencePhase.WAITING_FOR_LOCATION,
            DriverPresencePhase.WRITING_PRESENCE,
            DriverPresencePhase.WAITING_FOR_PRESENCE_ACK,
            DriverPresencePhase.DISCONNECTING
        )
    }

    private fun scheduleReconnectAttempt(
        reason: String,
        resetBackoff: Boolean = false,
        forceReschedule: Boolean = false
    ) {
        if (!shouldAutoRecover()) {
            reconnectCoordinator.cancelAndReset()
            return
        }

        reconnectCoordinator.schedule(
            reason = reason,
            resetBackoff = resetBackoff,
            forceReschedule = forceReschedule
        )
    }

    private suspend fun onReconnectAttempt(attemptIndex: Int, reason: String) {
        if (!shouldAutoRecover()) {
            reconnectCoordinator.cancelAndReset()
            return
        }

        val currentState = _presenceState.value
        if (!currentState.hasNetwork) {
            logRecoveryEvent(
                "reconnect_deferred_no_network",
                "reason=$reason attemptIndex=$attemptIndex"
            )
            return
        }

        if (shouldGateAutomaticReconnectOnFirebaseSocket(reason, currentState.firebaseConnected, currentState.phase)) {
            waitForFirebaseSocket(reason = reason, attemptIndex = attemptIndex)
            return
        }

        Log.i(
            TAG,
            "event=reconnect_attempt reason=$reason attemptIndex=$attemptIndex desiredOnline=${currentState.desiredOnline} firebaseConnected=${currentState.firebaseConnected}"
        )
        beginAutomaticReconnectAttempt(reason)
    }

    private fun startFirebaseConnectionObservation() {
        if (firebaseConnectionListener != null) {
            return
        }

        firebaseConnectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                onFirebaseConnectionChanged(snapshot.getValue(Boolean::class.java) == true)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, "event=firebase_connection_cancelled message=${error.message}")
            }
        }

        Database.dbInfoConnected().addValueEventListener(firebaseConnectionListener!!)
    }

    private fun stopFirebaseConnectionObservation() {
        firebaseConnectionListener?.let {
            Database.dbInfoConnected().removeEventListener(it)
        }
        firebaseConnectionListener = null
    }

    private fun onFirebaseConnectionChanged(isConnected: Boolean) {
        val previousState = _presenceState.value
        if (previousState.firebaseConnected == isConnected) {
            return
        }

        Log.i(
            TAG,
            "event=firebase_socket_changed connected=$isConnected desiredOnline=${previousState.desiredOnline}"
        )
        updatePresenceState { current ->
            current.copy(firebaseConnected = isConnected)
        }

        if (!previousState.desiredOnline) {
            return
        }

        if (!isConnected) {
            lastFirebaseSocketConnectedAtMs = null
            transitionToReconnecting(
                reason = "firebase_disconnected",
                forceReschedule = false,
                resetBackoff = true
            )
            return
        }

        lastFirebaseSocketConnectedAtMs = System.currentTimeMillis()
        logRecoveryEvent(
            "firebase_socket_restored",
            "phase=${previousState.phase} reason=${previousState.reconnectReason} " +
                "networkRecoveredMs=${elapsedSince(lastNetworkRestoredAtMs)}"
        )

        if (shouldResumeAutomaticReconnectFromSocket(previousState.desiredOnline, previousState.phase)) {
            firebaseSocketWaitJob?.cancel()
            beginAutomaticReconnectAttempt(previousState.reconnectReason ?: "firebase_socket_ready")
        }
    }

    private fun transitionToReconnecting(
        reason: String,
        forceReschedule: Boolean,
        resetBackoff: Boolean
    ) {
        if (!shouldAutoRecover()) {
            return
        }

        currentAttemptAutomatic = false
        cancelPresenceJobs()
        markObserversForRefreshAfterRecovery(_presenceState.value)
        _isLoading.postValue(false)
        Log.i(TAG, "event=phase_transition phase=RECONNECTING reason=$reason")
        updatePresenceState { current ->
            current.copy(
                actualOnline = false,
                phase = DriverPresencePhase.RECONNECTING,
                lastError = reason,
                sessionId = null,
                reconnectReason = reason
            )
        }
        scheduleReconnectAttempt(
            reason = reason,
            resetBackoff = resetBackoff,
            forceReschedule = forceReschedule
        )
    }

    private fun bumpReconnectGeneration() {
        updatePresenceState { current ->
            current.copy(reconnectGeneration = current.reconnectGeneration + 1)
        }
        restartServiceObserversIfActive()
    }

    private fun waitForFirebaseSocket(reason: String, attemptIndex: Int) {
        val currentState = _presenceState.value
        if (!shouldAutoRecover()) {
            return
        }

        currentAttemptAutomatic = false
        cancelPresenceJobs()
        _isLoading.postValue(false)
        markObserversForRefreshAfterRecovery(currentState)
        firebaseSocketFallbackAttemptsRemaining = FIREBASE_SOCKET_WAIT_FALLBACK_ATTEMPTS

        Log.i(
            TAG,
            "event=phase_transition phase=WAITING_FOR_FIREBASE_SOCKET reason=$reason attemptIndex=$attemptIndex"
        )
        updatePresenceState { current ->
            current.copy(
                actualOnline = false,
                phase = DriverPresencePhase.WAITING_FOR_FIREBASE_SOCKET,
                lastError = reason,
                sessionId = null,
                reconnectReason = reason
            )
        }

        resetFirebaseTransport(reason = reason, attemptIndex = attemptIndex)
        scheduleFirebaseSocketWaitTimeout(reason = reason, attemptIndex = attemptIndex)
    }

    private fun scheduleFirebaseSocketWaitTimeout(reason: String, attemptIndex: Int) {
        firebaseSocketWaitJob?.cancel()
        firebaseSocketWaitJob = viewModelScope.launch {
            delay(FIREBASE_SOCKET_WAIT_TIMEOUT_MS)
            val currentState = _presenceState.value
            if (
                currentState.phase != DriverPresencePhase.WAITING_FOR_FIREBASE_SOCKET ||
                !currentState.desiredOnline
            ) {
                return@launch
            }

            if (currentState.hasNetwork && firebaseSocketFallbackAttemptsRemaining > 0) {
                firebaseSocketFallbackAttemptsRemaining -= 1
                logRecoveryEvent(
                    "firebase_socket_timeout_retry",
                    "reason=$reason attemptIndex=$attemptIndex retriesRemaining=$firebaseSocketFallbackAttemptsRemaining"
                )
                resetFirebaseTransport(reason = "${reason}_fallback", attemptIndex = attemptIndex)
                scheduleFirebaseSocketWaitTimeout(reason = reason, attemptIndex = attemptIndex)
                return@launch
            }

            logRecoveryEvent(
                "firebase_socket_timeout_reschedule",
                "reason=$reason attemptIndex=$attemptIndex hasNetwork=${currentState.hasNetwork}"
            )
            transitionToReconnecting(
                reason = "firebase_socket_timeout",
                forceReschedule = true,
                resetBackoff = false
            )
        }
    }

    private fun resetFirebaseTransport(reason: String, attemptIndex: Int) {
        logRecoveryEvent(
            "firebase_transport_reset",
            "reason=$reason attemptIndex=$attemptIndex"
        )
        try {
            FirebaseInitializeApp.database.goOffline()
        } catch (exception: Exception) {
            Log.e(TAG, "event=reconnect_go_offline_failed message=${exception.message}")
        }

        try {
            FirebaseInitializeApp.database.goOnline()
        } catch (exception: Exception) {
            Log.e(TAG, "event=reconnect_go_online_failed message=${exception.message}")
        }
    }

    private fun markObserversForRefreshAfterRecovery(state: DriverPresenceState) {
        if (pendingRecoveryObserverRefresh) {
            return
        }
        pendingRecoveryObserverRefresh = state.actualOnline || state.phase in setOf(
            DriverPresencePhase.RECONNECTING,
            DriverPresencePhase.WAITING_FOR_FIREBASE_SOCKET,
            DriverPresencePhase.WAITING_FOR_PRESENCE_ACK,
            DriverPresencePhase.CONNECTED
        )
    }

    private fun logRecoveryEvent(event: String, message: String) {
        Log.i(
            TAG,
            "event=$event emulator=${FirebaseInitializeApp.usesEmulators()} " +
                "databaseHost=${FirebaseInitializeApp.databaseHostPort()} $message"
        )
    }

    private fun elapsedSince(timestampMs: Long?): Long? {
        return timestampMs?.let { System.currentTimeMillis() - it }
    }

    private fun elapsedBetween(startMs: Long?, endMs: Long?): Long? {
        return if (startMs != null && endMs != null) endMs - startMs else null
    }

    private fun restartServiceObserversIfActive() {
        if (!serviceObserversActive) {
            return
        }

        currentServiceObserverHandle?.dispose()
        nextServiceObserverHandle?.dispose()
        currentServiceObserverHandle = ServiceRepository.observeCurrentService(currentServiceListener)
        nextServiceObserverHandle = ServiceRepository.observeConnectionService(nextServiceListener)
    }

    override fun onCleared() {
        reconnectCoordinator.cancelAndReset()
        stopServiceObservers()
        stopPresenceObservation()
        stopFirebaseConnectionObservation()
        cancelPresenceJobs()
        super.onCleared()
    }
}
