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
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.R
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
import gorda.driver.ui.driver.DriverStatusPublisher
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.ServiceEventListener
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.dataclasses.ServiceUpdates
import gorda.driver.utils.Constants
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private val TAG: String = MainViewModel::class.java.toString()
        private const val PRESENCE_HEARTBEAT_SECONDS = 5L
    }

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
        CONNECTED,
        DISCONNECTING
    }

    data class DriverPresenceState(
        val desiredOnline: Boolean = false,
        val actualOnline: Boolean = false,
        val phase: DriverPresencePhase = DriverPresencePhase.DISCONNECTED,
        val hasNetwork: Boolean = true,
        val lastError: String? = null,
        val attemptId: Long = 0L,
        val sessionId: String? = null
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
    private var observedDriverId: String? = null
    private var nextAttemptId: Long = 0L
    private var latestLocation: Location? = null
    private var lastPresenceUpdateAt: Long = 0L
    private var preserveDesiredOnlineOnFailure = false
    private var bindTimeoutJob: Job? = null
    private var locationTimeoutJob: Job? = null
    private var presenceWriteTimeoutJob: Job? = null
    private var currentServiceObserverHandle: ServiceObserverHandle? = null
    private var nextServiceObserverHandle: ServiceObserverHandle? = null

    private val pendingLocationUpdates = mutableListOf<Location>()
    private val driverStatusPublisher = DriverStatusPublisher()

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
    }

    fun setServiceUpdateStartLocation(starLoc: LocType) {
        _serviceUpdates.postValue(ServiceUpdates.setStarLoc(starLoc))
    }

    fun startServiceObservers() {
        stopServiceObservers()
        currentServiceObserverHandle = ServiceRepository.observeCurrentService(currentServiceListener)
        nextServiceObserverHandle = ServiceRepository.observeConnectionService(nextServiceListener)
    }

    fun stopServiceObservers() {
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
                    val status = snapshot.getValue<String>()
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
        if (!isNetWorkConnected.value) {
            pendingLocationUpdates.add(location)
            Log.d(TAG, "Location queued (offline mode): ${pendingLocationUpdates.size} pending")
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
                DriverPresencePhase.DISCONNECTING
            )

            if (
                presence?.sessionId != null &&
                currentState.sessionId != null &&
                presence.sessionId == currentState.sessionId
            ) {
                lastPresenceUpdateAt = presence.lastSeenAt ?: currentUnixSeconds()
                updatePresenceState { state ->
                    state.copy(
                        actualOnline = true,
                        phase = DriverPresencePhase.CONNECTED,
                        lastError = null
                    )
                }
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

            if (presence == null) {
                val previousOnline = currentState.actualOnline
                updatePresenceState { state ->
                    state.copy(
                        actualOnline = false,
                        phase = DriverPresencePhase.DISCONNECTED
                    )
                }

                if (
                    previousOnline &&
                    currentState.desiredOnline &&
                    currentState.hasNetwork
                ) {
                    reconnectFirebaseIfNeeded(latestLocation)
                }
                return@observePresence
            }

            lastPresenceUpdateAt = presence.lastSeenAt ?: currentUnixSeconds()
            updatePresenceState { state ->
                state.copy(
                    actualOnline = true,
                    phase = DriverPresencePhase.CONNECTED,
                    sessionId = presence.sessionId ?: state.sessionId,
                    lastError = null
                )
            }
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
    }

    fun requestConnect() {
        if (!isNetWorkConnected.value) {
            emitErrorMessage(R.string.connection_lost)
            return
        }

        precheckDriverConnectEligibility(
            emitFeedback = true,
            onAllowed = {
                beginPresenceAttempt(immediateLocation = null, keepDesiredOnlineOnFailure = false)
            }
        )
    }

    fun requestDisconnect() {
        val currentDriver = driver.value ?: return
        val attemptId = nextAttemptId()

        cancelPresenceJobs()
        preserveDesiredOnlineOnFailure = false
        persistDesiredOnline(false)
        Log.i(TAG, "event=manual_disconnect driverId=${currentDriver.id} attemptId=$attemptId")

        updatePresenceState { current ->
            current.copy(
                desiredOnline = false,
                actualOnline = false,
                phase = DriverPresencePhase.DISCONNECTING,
                lastError = null,
                attemptId = attemptId,
                sessionId = null
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
                        sessionId = null
                    )
                }
            }
            .addOnFailureListener { exception ->
                _isLoading.postValue(false)
                Log.e(TAG, "event=manual_disconnect_failed driverId=${currentDriver.id} message=${exception.message}")
                updatePresenceState { current ->
                    current.copy(
                        desiredOnline = true,
                        actualOnline = true,
                        phase = DriverPresencePhase.CONNECTED,
                        lastError = "manual_disconnect_failed"
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
        failPresenceAttempt(
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
        writePresence(location, currentState.attemptId)
    }

    fun reconnectFirebaseIfNeeded(lastKnownLocation: Location?) {
        viewModelScope.launch {
            try {
                FirebaseDatabase.getInstance().goOnline()
                Log.i(TAG, "event=network_recovered_reconnect desiredOnline=${_presenceState.value.desiredOnline}")
                delay(500)
            } catch (exception: Exception) {
                Log.e(TAG, "Error reconnecting Firebase: ${exception.message}")
            }

            val state = _presenceState.value
            if (!state.desiredOnline || !state.hasNetwork) {
                return@launch
            }

            precheckDriverConnectEligibility(
                emitFeedback = false,
                onAllowed = {
                    beginPresenceAttempt(lastKnownLocation ?: latestLocation, keepDesiredOnlineOnFailure = true)
                }
            )
        }
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

    private fun beginPresenceAttempt(
        immediateLocation: Location?,
        keepDesiredOnlineOnFailure: Boolean
    ) {
        val currentDriver = driver.value ?: return
        val state = _presenceState.value
        val localAttemptActive = state.phase in setOf(
            DriverPresencePhase.PRECHECKING,
            DriverPresencePhase.WAITING_FOR_BIND,
            DriverPresencePhase.WAITING_FOR_LOCATION,
            DriverPresencePhase.WRITING_PRESENCE,
            DriverPresencePhase.DISCONNECTING
        )

        if (localAttemptActive) {
            return
        }

        val attemptId = nextAttemptId()
        val nextSessionId = UUID.randomUUID().toString()

        preserveDesiredOnlineOnFailure = keepDesiredOnlineOnFailure
        persistDesiredOnline(true)
        cancelPresenceJobs()
        _isLoading.postValue(true)

        updatePresenceState { current ->
            current.copy(
                desiredOnline = true,
                phase = DriverPresencePhase.PRECHECKING,
                lastError = null,
                attemptId = attemptId,
                sessionId = nextSessionId
            )
        }

        DriverRepository.validateDriverVersion { result ->
            if (_presenceState.value.attemptId != attemptId) {
                return@validateDriverVersion
            }

            result.onSuccess {
                if (immediateLocation != null) {
                    writePresence(immediateLocation, attemptId)
                } else {
                    updatePresenceState { current ->
                        current.copy(phase = DriverPresencePhase.WAITING_FOR_BIND)
                    }
                    scheduleBindTimeout(attemptId)
                }
            }.onFailure { exception ->
                if (exception is gorda.driver.exceptions.UnsupportedAppVersionException) {
                    Log.w(TAG, "event=version_unsupported attemptId=$attemptId")
                    _unsupportedVersion.postValue(true)
                    persistDesiredOnline(false)
                    _isLoading.postValue(false)
                    updatePresenceState { current ->
                        current.copy(
                            desiredOnline = false,
                            actualOnline = false,
                            phase = DriverPresencePhase.DISCONNECTED,
                            lastError = "version_unsupported",
                            sessionId = null
                        )
                    }
                } else {
                    failPresenceAttempt(
                        attemptId = attemptId,
                        lastError = "version_precheck_failed",
                        messageRes = if (keepDesiredOnlineOnFailure) {
                            R.string.error_reconnection_failed
                        } else {
                            R.string.error_timeout
                        }
                    )
                }
            }
        }

        if (immediateLocation != null) {
            Log.i(TAG, "event=network_recovered_reconnect driverId=${currentDriver.id} attemptId=$attemptId")
        }
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
                        sessionId = null
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

            onAllowed(refreshedDriver)
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

    private fun writePresence(location: Location, attemptId: Long) {
        val currentDriver = driver.value ?: return
        val sessionId = _presenceState.value.sessionId ?: UUID.randomUUID().toString()
        val lastSeenAt = currentUnixSeconds()

        latestLocation = location
        _isLoading.postValue(true)
        updatePresenceState { current ->
            current.copy(
                phase = DriverPresencePhase.WRITING_PRESENCE,
                sessionId = sessionId,
                lastError = null
            )
        }
        schedulePresenceWriteTimeout(attemptId)

        currentDriver.connect(
            object : LocInterface {
                override var lat: Double = location.latitude
                override var lng: Double = location.longitude
            },
            sessionId,
            lastSeenAt
        ).addOnSuccessListener {
            if (_presenceState.value.attemptId != attemptId) {
                return@addOnSuccessListener
            }

            presenceWriteTimeoutJob?.cancel()
            lastPresenceUpdateAt = lastSeenAt
            _isLoading.postValue(false)
            updatePresenceState { current ->
                current.copy(
                    actualOnline = true,
                    phase = DriverPresencePhase.CONNECTED,
                    lastError = null
                )
            }
            flushPendingLocationUpdates()
        }.addOnFailureListener { exception ->
            if (_presenceState.value.attemptId != attemptId) {
                return@addOnFailureListener
            }

            Log.e(
                TAG,
                "event=connect_write_failed driverId=${currentDriver.id} attemptId=$attemptId message=${exception.message}"
            )
            if (exception is gorda.driver.exceptions.UnsupportedAppVersionException) {
                Log.w(TAG, "event=version_unsupported attemptId=$attemptId")
                _unsupportedVersion.postValue(true)
            }

            failPresenceAttempt(
                attemptId = attemptId,
                lastError = if (exception is gorda.driver.exceptions.UnsupportedAppVersionException) {
                    "version_unsupported"
                } else {
                    "connect_write_failed"
                },
                messageRes = if (preserveDesiredOnlineOnFailure) {
                    R.string.error_reconnection_failed
                } else {
                    R.string.error_timeout
                },
                unsupportedVersion = exception is gorda.driver.exceptions.UnsupportedAppVersionException
            )
        }
    }

    private fun maybeSendPresenceHeartbeat(location: Location, force: Boolean) {
        val currentDriver = driver.value ?: return
        val state = _presenceState.value
        if (!state.desiredOnline || !state.actualOnline || !state.hasNetwork) {
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
            Log.e(TAG, "Failed to update heartbeat: ${exception.message}")
        }
    }

    private fun scheduleBindTimeout(attemptId: Long) {
        bindTimeoutJob?.cancel()
        bindTimeoutJob = viewModelScope.launch {
            delay(5000)
            failPresenceAttempt(
                attemptId = attemptId,
                lastError = "bind_timeout",
                messageRes = R.string.error_timeout
            )
        }
    }

    private fun scheduleLocationTimeout(attemptId: Long) {
        locationTimeoutJob?.cancel()
        locationTimeoutJob = viewModelScope.launch {
            delay(15000)
            failPresenceAttempt(
                attemptId = attemptId,
                lastError = "location_timeout",
                messageRes = if (preserveDesiredOnlineOnFailure) {
                    R.string.error_reconnection_failed
                } else {
                    R.string.error_timeout
                }
            )
        }
    }

    private fun schedulePresenceWriteTimeout(attemptId: Long) {
        presenceWriteTimeoutJob?.cancel()
        presenceWriteTimeoutJob = viewModelScope.launch {
            delay(8000)
            failPresenceAttempt(
                attemptId = attemptId,
                lastError = "presence_write_timeout",
                messageRes = if (preserveDesiredOnlineOnFailure) {
                    R.string.error_reconnection_failed
                } else {
                    R.string.error_timeout
                }
            )
        }
    }

    private fun failPresenceAttempt(
        attemptId: Long,
        lastError: String,
        messageRes: Int?,
        unsupportedVersion: Boolean = false
    ) {
        if (_presenceState.value.attemptId != attemptId) {
            return
        }

        cancelPresenceJobs()
        _isLoading.postValue(false)
        if (messageRes != null) {
            emitErrorMessage(messageRes)
        }
        if (unsupportedVersion) {
            persistDesiredOnline(false)
        } else if (!preserveDesiredOnlineOnFailure) {
            persistDesiredOnline(false)
        }

        updatePresenceState { current ->
            current.copy(
                desiredOnline = if (unsupportedVersion) {
                    false
                } else {
                    current.desiredOnline && preserveDesiredOnlineOnFailure
                },
                actualOnline = false,
                phase = DriverPresencePhase.DISCONNECTED,
                lastError = lastError,
                sessionId = null
            )
        }
    }

    private fun emitErrorMessage(messageRes: Int) {
        _errorMessageRes.postValue(messageRes)
    }

    private fun persistDesiredOnline(desiredOnline: Boolean) {
        preferences?.edit()?.putBoolean(Constants.DRIVER_DESIRED_ONLINE, desiredOnline)?.apply()
    }

    private fun cancelPresenceJobs() {
        bindTimeoutJob?.cancel()
        locationTimeoutJob?.cancel()
        presenceWriteTimeoutJob?.cancel()
        bindTimeoutJob = null
        locationTimeoutJob = null
        presenceWriteTimeoutJob = null
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

    override fun onCleared() {
        stopServiceObservers()
        stopPresenceObservation()
        cancelPresenceJobs()
        super.onCleared()
    }
}
