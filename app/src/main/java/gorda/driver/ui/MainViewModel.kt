package gorda.driver.ui

import android.content.SharedPreferences
import android.location.Location
import android.util.Log
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import gorda.driver.R
import gorda.driver.exceptions.UnsupportedAppVersionException
import gorda.driver.helpers.withTimeout
import gorda.driver.interfaces.DeviceInterface
import gorda.driver.interfaces.LocInterface
import gorda.driver.interfaces.LocType
import gorda.driver.interfaces.RideFees
import gorda.driver.location.CachedLocationStore
import gorda.driver.models.Driver
import gorda.driver.models.Service
import gorda.driver.repositories.DriverRepository
import gorda.driver.repositories.ServiceObservationResult
import gorda.driver.repositories.ServiceObserverHandle
import gorda.driver.repositories.ServiceRepository
import gorda.driver.services.firebase.PresenceManager
import gorda.driver.services.masterData.MasterDataApiService
import gorda.driver.services.masterData.RosterVehicle
import gorda.driver.services.masterData.SelfServiceCancelRejectionReason
import gorda.driver.services.masterData.SelfServiceErrorBody
import gorda.driver.services.retrofit.DriverAppRequestException
import gorda.driver.services.retrofit.DriverAppRequestRunner
import gorda.driver.services.retrofit.MasterDataRetrofit
import gorda.driver.services.selfservice.SelfServiceTripManager
import gorda.driver.ui.driver.DriverStatusPublisher
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.ServiceEventListener
import gorda.driver.ui.service.current.SelfServiceProvisionalTrip
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.dataclasses.ServiceUpdates
import gorda.driver.utils.Constants
import gorda.driver.utils.RideRecoveryStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private val TAG: String = MainViewModel::class.java.toString()
        internal const val CACHED_LOCATION_MAX_AGE_MS = 5 * 60 * 1000L

        internal data class CachedLocationRestoreDecision(
            val shouldRestore: Boolean,
            val emitsUiLocation: Boolean
        )

        internal fun cachedLocationRestoreDecision(
            capturedAtEpochMs: Long?,
            nowEpochMs: Long,
            maxAgeMs: Long = CACHED_LOCATION_MAX_AGE_MS
        ): CachedLocationRestoreDecision {
            val ageMs = capturedAtEpochMs?.let { nowEpochMs - it }
            val shouldRestore = capturedAtEpochMs != null &&
                ageMs != null &&
                ageMs in 0..maxAgeMs
            return CachedLocationRestoreDecision(
                shouldRestore = shouldRestore,
                emitsUiLocation = shouldRestore
            )
        }
    }

    sealed class DriverLoadState {
        object Idle : DriverLoadState()
        data class Loading(val driverId: String) : DriverLoadState()
        data class Loaded(val driver: Driver) : DriverLoadState()
        data class Failed(val driverId: String) : DriverLoadState()
    }

    data class DriverPresenceState(
        val desiredOnline: Boolean = false,
        val actualOnline: Boolean = false,
        val connecting: Boolean = false,
        val hasNetwork: Boolean = true,
        val fatalStopReason: String? = null
    )

    data class FeeData(
        val totalFee: Double,
        val timeFee: Double,
        val distanceFee: Double,
        val totalDistance: Double,
        val elapsedSeconds: Long
    )

    /** Outcome of [cancelSelfServiceTrip] (add-driver-self-service design D6, task 4.5). */
    sealed class SelfServiceCancelResult {
        object Success : SelfServiceCancelResult()
        data class Rejected(@StringRes val messageRes: Int) : SelfServiceCancelResult()
        object Failed : SelfServiceCancelResult()
    }

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
    private val _vehicleChangedBannerPlate = MutableLiveData<String?>()
    private val _vehicleForBanner = MutableStateFlow<RosterVehicle?>(null)
    private val _forceDisconnectReason = MutableLiveData<String?>()
    private val _connectSwitchResetEvent = MutableLiveData<Unit?>()
    private val _selfServiceRejection = MutableLiveData<Int?>()

    private var preferences: SharedPreferences? = null
    private var observedDriverId: String? = null
    private var latestLocation: Location? = null
    private var lastTerminalCurrentServiceId: String? = null

    private val presenceManager = PresenceManager()
    private val selfServiceTripManager = SelfServiceTripManager()
    private val driverStatusPublisher = DriverStatusPublisher()
    private var presenceCollectorJob: Job? = null
    private var selfServiceSyncJob: Job? = null
    private var pendingConnectVehicle: RosterVehicle? = null

    /** Set by the vehicle picker flow (task 17/18). Null until a vehicle is selected. */
    var selectedVehicleId: String? = null

    private var currentServiceObserverHandle: ServiceObserverHandle? = null
    private var nextServiceObserverHandle: ServiceObserverHandle? = null
    private var serviceObserversActive = false

    private val nextServiceListener: ServiceEventListener =
        ServiceEventListener(::handleNextServiceObservation)
    private val currentServiceListener: ServiceEventListener =
        ServiceEventListener(::handleCurrentServiceObservation)

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
    val vehicleChangedBannerPlate: LiveData<String?> = _vehicleChangedBannerPlate
    val vehicleForBanner: StateFlow<RosterVehicle?> = _vehicleForBanner.asStateFlow()
    val forceDisconnectReason: LiveData<String?> = _forceDisconnectReason
    val connectSwitchResetEvent: LiveData<Unit?> = _connectSwitchResetEvent

    /** Non-null when a queued self-service creation was terminally rejected (add-driver-self-service D5). */
    val selfServiceRejection: LiveData<Int?> = _selfServiceRejection

    init {
        presenceCollectorJob = viewModelScope.launch {
            presenceManager.state.collect(::onPresenceManagerState)
        }
    }

    fun initializePreferences(preferences: SharedPreferences) {
        this.preferences = preferences
        val desiredOnline = preferences.getBoolean(Constants.DRIVER_DESIRED_ONLINE, false)
        updatePresenceState { it.copy(desiredOnline = desiredOnline) }
        selectedVehicleId = preferences.getString(Constants.DRIVER_SELECTED_VEHICLE_ID, null)
        restoreCachedLocationFromPreferences(preferences)
        restorePendingSelfServiceTrip(preferences)
    }

    /**
     * Starts the queued creation for a self-service trip whose `FeesService` metering already
     * began locally (add-driver-self-service design D5, step 1). Persists the provisional trip so
     * it survives process death, then attempts an immediate sync; if that fails transiently the
     * trip stays queued and later retries fire from network/presence/location signals below.
     */
    fun startSelfServiceTrip(
        localId: String,
        startedAt: Long,
        multiplier: Double,
        gpsFix: Location?
    ) {
        val trip = SelfServiceProvisionalTrip(
            localId = localId,
            startedAt = startedAt,
            multiplier = multiplier,
            gpsLat = gpsFix?.latitude ?: latestLocation?.latitude,
            gpsLng = gpsFix?.longitude ?: latestLocation?.longitude
        )
        selfServiceTripManager.enqueue(trip)
        preferences?.let { RideRecoveryStore.persistSelfServiceProvisionalTrip(it, trip) }
        triggerSelfServiceSync()
    }

    fun hasPendingSelfServiceTrip(): Boolean = selfServiceTripManager.hasPendingTrip()

    fun getPendingSelfServiceTrip(): SelfServiceProvisionalTrip? = selfServiceTripManager.getPendingTrip()

    /**
     * The driver ended a self-service trip whose creation hasn't synced yet (design D5, step 2;
     * task 4.4). Marks the queued trip terminal so the very next sync sends a single deferred
     * payload carrying both the creation and this terminal data — never a create followed by a
     * separate terminate. [route]/[tripDistance]/[tripFee] mirror what a normal termination
     * writes (`CurrentServiceFragment.beginEndTrip`), including the zero-fee-is-null rule.
     */
    fun endUnsyncedSelfServiceTrip(endedAt: Long, route: String, tripDistance: Int, tripFee: Int?) {
        selfServiceTripManager.markTerminated(endedAt, route, tripDistance, tripFee)
        persistPendingSelfServiceTrip()
        triggerSelfServiceSync()
    }

    /**
     * Marks the queued (not-yet-synced) trip canceled so the next sync sends a deferred
     * `status="canceled"` payload (add-driver-self-service design D5's "Deferred cancellations
     * are audited, not trusted"; task 4.5's windowed cancel UI). Reached from
     * `CurrentServiceFragment` while there is no RTDB-backed [Service] yet.
     */
    fun cancelUnsyncedSelfServiceTrip() {
        selfServiceTripManager.markCanceled()
        persistPendingSelfServiceTrip()
        triggerSelfServiceSync()
    }

    /**
     * Calls the server-authoritative windowed cancel for an already-synced self-service trip
     * (add-driver-self-service design D6, task 4.5). The app only offers the action while its own
     * cached-snapshot window check passes, but the server re-validates `origin`, ownership,
     * `status`, and the elapsed window against its own `start_trip_at` — a `cancel_window_elapsed`
     * (or any other typed) rejection means the trip is still genuinely running, so [onResult]
     * lets the caller reconcile the UI instead of assuming the cancellation went through.
     */
    fun cancelSelfServiceTrip(serviceId: String, onResult: (SelfServiceCancelResult) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val result = try {
                DriverAppRequestRunner.execute("/driver-app/me/services/$serviceId/cancel") { authorization ->
                    MasterDataRetrofit.getRetrofit()
                        .create(MasterDataApiService::class.java)
                        .cancelSelfService(authorization = authorization, serviceId = serviceId)
                }
                SelfServiceCancelResult.Success
            } catch (e: DriverAppRequestException) {
                Log.w(TAG, "cancelSelfServiceTrip rejected: ${e.errorBody}")
                SelfServiceCancelResult.Rejected(
                    messageResForCancelRejection(parseCancelRejectionReason(e.errorBody))
                )
            } catch (e: Exception) {
                Log.w(TAG, "cancelSelfServiceTrip failed: ${e.message}")
                SelfServiceCancelResult.Failed
            }
            withContext(Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    private fun parseCancelRejectionReason(errorBody: String?): String? {
        if (errorBody == null) return null
        return try {
            Gson().fromJson(errorBody, SelfServiceErrorBody::class.java)?.error
        } catch (_: Exception) {
            null
        }
    }

    @StringRes
    private fun messageResForCancelRejection(reason: String?): Int = when (reason) {
        SelfServiceCancelRejectionReason.CANCEL_WINDOW_ELAPSED -> R.string.self_service_cancel_rejected_window_elapsed
        else -> R.string.self_service_cancel_rejected_generic
    }

    private fun persistPendingSelfServiceTrip() {
        val trip = selfServiceTripManager.getPendingTrip() ?: return
        preferences?.let { RideRecoveryStore.persistSelfServiceProvisionalTrip(it, trip) }
    }

    fun consumeSelfServiceRejection() {
        _selfServiceRejection.postValue(null)
    }

    private fun restorePendingSelfServiceTrip(preferences: SharedPreferences) {
        val trip = RideRecoveryStore.getSelfServiceProvisionalTrip(preferences) ?: return
        selfServiceTripManager.restore(trip)
        triggerSelfServiceSync()
    }

    /**
     * Attempts one sync of the pending self-service trip, if any. Safe to call opportunistically
     * (network/presence reconnect, fresh location, cold start): no-ops when there's nothing
     * pending or a sync is already in flight (`SelfServiceTripManager` de-dupes internally).
     */
    private fun triggerSelfServiceSync() {
        if (!selfServiceTripManager.hasPendingTrip()) return
        if (selfServiceSyncJob?.isActive == true) return

        selfServiceSyncJob = viewModelScope.launch(Dispatchers.IO) {
            val result = selfServiceTripManager.trySync()
            withContext(Dispatchers.Main) {
                when (result) {
                    is SelfServiceTripManager.Result.Created -> onSelfServiceTripCreated(result.trip, result.service)
                    is SelfServiceTripManager.Result.Rejected -> onSelfServiceTripRejected(result.messageRes)
                    SelfServiceTripManager.Result.Retryable,
                    SelfServiceTripManager.Result.NoPendingTrip,
                    SelfServiceTripManager.Result.AlreadySyncing -> {
                        // Stays queued; the next network/presence/location signal retries.
                    }
                }
            }
        }
    }

    /**
     * Reconciles a synced self-service trip, online or deferred (design D5, steps 2-3; task 4.4).
     * Re-keys the recovery record so `CurrentServiceFragment` doesn't treat the (possibly already
     * stopped) `FeesService` as stale, then publishes the service exactly like any other
     * driver-assigned one. For a still-running trip this is `in_progress` and the fragment binds
     * to it normally; for a deferred (terminal) payload it already comes back `terminated`/
     * `canceled` — the fragment's existing "service is not in progress" handling resets local
     * state without attempting a second terminate, since the API already applied it server-side.
     * The RTDB `drivers_assigned/{driverId}` listener will shortly confirm the same service;
     * posting it here makes the rebind immediate instead of depending on listener timing.
     */
    private fun onSelfServiceTripCreated(trip: SelfServiceProvisionalTrip, service: Service) {
        preferences?.let { prefs ->
            RideRecoveryStore.rebindTrackedServiceId(prefs, trip.localId, service.id)
            RideRecoveryStore.clearSelfServiceProvisionalTrip(prefs)
        }
        _currentService.postValue(service)
    }

    private fun onSelfServiceTripRejected(messageRes: Int) {
        preferences?.let { RideRecoveryStore.clearSelfServiceProvisionalTrip(it) }
        _selfServiceRejection.postValue(messageRes)
    }

    @JvmName("updateSelectedVehicleId")
    fun setSelectedVehicleId(vehicleId: String?) {
        selectedVehicleId = vehicleId
        preferences?.edit()?.putString(Constants.DRIVER_SELECTED_VEHICLE_ID, vehicleId)?.apply()
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

    fun consumeVehicleChangedBanner() {
        _vehicleChangedBannerPlate.postValue(null)
    }

    fun handleForcedDisconnect(reason: String) {
        requestDisconnect()
        _forceDisconnectReason.postValue(reason)
    }

    fun consumeForceDisconnectReason() {
        _forceDisconnectReason.postValue(null)
    }

    fun consumeConnectSwitchResetEvent() {
        _connectSwitchResetEvent.postValue(null)
    }

    /**
     * Called from MainActivity when the driver confirms "Yes" on the vehicle-changed dialog.
     * Resumes the connect flow using the already-refreshed vehicle.
     */
    fun proceedWithConnect() {
        val vehicle = pendingConnectVehicle ?: return
        pendingConnectVehicle = null
        doConnect(vehicle)
    }

    fun abandonPendingConnect() {
        pendingConnectVehicle = null
        emitConnectSwitchResetEvent()
    }

    fun changeConnectTripService(connect: Boolean) {
        _isTripStarted.postValue(connect)
    }

    fun changeNetWorkStatus(hasTransportNetwork: Boolean, networkValidated: Boolean) {
        Log.d(TAG, "network: transport=$hasTransportNetwork validated=$networkValidated")
        _isNetWorkConnected.value = hasTransportNetwork
        updatePresenceState { it.copy(hasNetwork = hasTransportNetwork) }
        if (hasTransportNetwork) {
            presenceManager.onAndroidNetworkAvailable()
            triggerSelfServiceSync()
        } else {
            presenceManager.onAndroidNetworkLost()
        }
    }

    fun setServiceUpdateStartLocation(starLoc: LocType) {
        _serviceUpdates.postValue(ServiceUpdates.setStarLoc(starLoc))
    }

    fun startServiceObservers() {
        serviceObserversActive = true
        restartServiceObserversIfActive()
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

    internal fun handleCurrentServiceObservation(result: ServiceObservationResult) {
        val resolution = ServiceObservationReducer.reduceCurrent(
            result = result,
            lastTerminalServiceId = lastTerminalCurrentServiceId
        )
        lastTerminalCurrentServiceId = resolution.terminalServiceId
        if (resolution.shouldEmitCanceledFeedback) {
            emitErrorMessage(R.string.service_canceled)
        }
        _currentService.postValue(resolution.currentService)
    }

    internal fun handleNextServiceObservation(result: ServiceObservationResult) {
        _nextService.postValue(
            ServiceObservationReducer.reduceNext(
                result = result,
                driverId = driver.value?.id,
                currentServiceId = currentService.value?.id
            )
        )
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
        preferences?.let { CachedLocationStore.save(it, location) }
        _lastLocation.postValue(LocationUpdates.liveLocation(location))

        selfServiceTripManager.updateLocationIfMissing(location.latitude, location.longitude)
        triggerSelfServiceSync()

        val state = _presenceState.value
        if (!state.desiredOnline) return
        val driverId = driver.value?.id ?: observedDriverId ?: return
        val vid = selectedVehicleId
        if (vid == null) {
            Log.w(TAG, "updateLocation: no vehicle selected — skipping presence start")
            return
        }

        val locInterface = location.toLocInterface()
        presenceManager.start(driverId, vid, locInterface)
        presenceManager.updateLocation(locInterface)
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
        observedDriverId = driverId
        if (_presenceState.value.desiredOnline) {
            val vid = selectedVehicleId
            if (vid != null) {
                latestLocation?.let { loc ->
                    presenceManager.start(driverId, vid, loc.toLocInterface())
                }
            } else {
                Log.w(TAG, "startPresenceObservation: no vehicle selected — skipping presence start")
            }
        }
    }

    fun stopPresenceObservation() {
        observedDriverId = null
        presenceManager.stop()
    }

    fun forceReconnect() {
        presenceManager.forceReconnect()
    }

    fun requestConnect() {
        if (!_isNetWorkConnected.value) {
            emitConnectSwitchResetEvent()
            emitErrorMessage(R.string.connection_lost)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val freshVehicle = refreshSelectedVehicle()
            if (freshVehicle == null) {
                emitConnectSwitchResetEvent()
                emitErrorMessage(R.string.error_no_vehicle_selected)
                return@launch
            }

            val cachedVehicleId = selectedVehicleId
            if (cachedVehicleId != null && freshVehicle.id != cachedVehicleId) {
                // Vehicle changed — pause and ask the driver to confirm
                pendingConnectVehicle = freshVehicle
                _vehicleChangedBannerPlate.postValue(freshVehicle.plate)
                return@launch
            }

            // Update cache with the freshly resolved vehicle
            setSelectedVehicleId(freshVehicle.id)
            doConnect(freshVehicle)
        }
    }

    private fun doConnect(vehicle: RosterVehicle) {
        _vehicleForBanner.value = vehicle
        precheckDriverConnectEligibility(
            emitFeedback = true,
            onAllowed = { refreshedDriver ->
                persistDesiredOnline(true)
                updatePresenceState { it.copy(desiredOnline = true, fatalStopReason = null) }
                val loc = latestLocation
                if (loc != null) {
                    presenceManager.start(refreshedDriver.id, vehicle.id, loc.toLocInterface())
                }
                // If no location yet, LocationService's first fix will trigger updateLocation()
                // which calls presenceManager.start() (idempotent).
            }
        )
    }

    private suspend fun refreshSelectedVehicle(): RosterVehicle? {
        return try {
            val response = DriverAppRequestRunner.execute("/driver-app/me/vehicles") { authorization ->
                MasterDataRetrofit.getRetrofit()
                    .create(MasterDataApiService::class.java)
                    .getVehicles(authorization = authorization)
            }
            val vehicles = response.body()?.data?.vehicles ?: emptyList()
            vehicles.firstOrNull { it.is_selected }
        } catch (e: Exception) {
            Log.w(TAG, "refreshSelectedVehicle failed: ${e.message}")
            null
        }
    }

    fun requestDisconnect() {
        driver.value ?: return
        persistDesiredOnline(false)
        updatePresenceState {
            it.copy(
                desiredOnline = false,
                actualOnline = false,
                connecting = false,
                fatalStopReason = null
            )
        }
        presenceManager.stop()
        // Fire-and-forget: local state is already cleared. If the API call fails,
        // the server-side onDisconnect().removeValue() armed at connect time will clean up.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                DriverAppRequestRunner.execute("/driver-app/me/disconnect") { authorization ->
                    MasterDataRetrofit.getRetrofit()
                        .create(MasterDataApiService::class.java)
                        .disconnect(authorization = authorization)
                }
            } catch (e: Exception) {
                Log.w(TAG, "API disconnect failed: ${e.message}")
            }
        }
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

    private fun precheckDriverConnectEligibility(
        emitFeedback: Boolean,
        onAllowed: (Driver) -> Unit
    ) {
        val currentDriver = driver.value ?: run {
            emitConnectSwitchResetEvent()
            return
        }
        _isLoading.postValue(true)

        DriverRepository.getDriver(currentDriver.id) { refreshedDriver ->
            if (refreshedDriver == null) {
                _isLoading.postValue(false)
                emitConnectSwitchResetEvent()
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
                emitConnectSwitchResetEvent()
                updatePresenceState { current ->
                    current.copy(
                        desiredOnline = false,
                        actualOnline = false,
                        connecting = false,
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
                    _isLoading.postValue(false)
                    onAllowed(refreshedDriver)
                }.onFailure { exception ->
                    _isLoading.postValue(false)
                    if (exception is UnsupportedAppVersionException) {
                        emitConnectSwitchResetEvent()
                        handleFatalStop(
                            reason = "version_unsupported",
                            unsupportedVersion = true
                        )
                    } else {
                        emitConnectSwitchResetEvent()
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

    private fun connectRejectedMessageRes(reason: String): Int = when (reason) {
        PresenceManager.REASON_VEHICLE_IN_USE -> R.string.error_vehicle_in_use
        PresenceManager.REASON_DRIVER_ALREADY_CONNECTED -> R.string.error_driver_already_connected
        PresenceManager.REASON_VEHICLE_DISABLED -> R.string.error_vehicle_disabled
        PresenceManager.REASON_VEHICLE_NOT_SELECTABLE -> R.string.error_vehicle_not_selectable
        else -> R.string.error_timeout
    }

    private fun onPresenceManagerState(state: PresenceManager.State) {
        when (state) {
            PresenceManager.State.Connected -> updatePresenceState {
                it.copy(actualOnline = true, connecting = false)
            }
            PresenceManager.State.Connecting -> updatePresenceState {
                it.copy(actualOnline = false, connecting = true)
            }
            PresenceManager.State.Offline -> updatePresenceState {
                it.copy(actualOnline = false, connecting = true, hasNetwork = false)
            }
            PresenceManager.State.Idle -> updatePresenceState {
                it.copy(actualOnline = false, connecting = false)
            }
            is PresenceManager.State.Fatal -> {
                if (state.reason == PresenceManager.REASON_VERSION_UNSUPPORTED) {
                    handleFatalStop(reason = "version_unsupported", unsupportedVersion = true)
                } else {
                    handleFatalStop(reason = state.reason)
                }
            }
            is PresenceManager.State.ConnectRejected -> {
                handleFatalStop(reason = state.reason)
                emitErrorMessage(connectRejectedMessageRes(state.reason))
            }
        }
    }

    private fun handleFatalStop(reason: String, unsupportedVersion: Boolean = false) {
        persistDesiredOnline(false)
        presenceManager.stop()
        if (unsupportedVersion) {
            _unsupportedVersion.postValue(true)
        }
        updatePresenceState {
            it.copy(
                desiredOnline = false,
                actualOnline = false,
                connecting = false,
                fatalStopReason = reason
            )
        }
    }

    private fun emitErrorMessage(messageRes: Int) {
        _errorMessageRes.postValue(messageRes)
    }

    private fun emitConnectSwitchResetEvent() {
        _connectSwitchResetEvent.postValue(Unit)
    }

    private fun persistDesiredOnline(desiredOnline: Boolean) {
        preferences?.edit()?.putBoolean(Constants.DRIVER_DESIRED_ONLINE, desiredOnline)?.apply()
    }

    private fun updatePresenceState(transform: (DriverPresenceState) -> DriverPresenceState) {
        val previous = _presenceState.value
        val next = transform(previous)
        _presenceState.value = next
        publishDriverStatus(next)
        if (!previous.actualOnline && next.actualOnline) {
            // Presence just came back (e.g. after a driver_not_connected rejection) — retry the
            // queued self-service creation, if any (add-driver-self-service design D5).
            triggerSelfServiceSync()
        }
    }

    private fun publishDriverStatus(state: DriverPresenceState) {
        viewModelScope.launch {
            driverStatusPublisher.updatesFor(state).forEach { _driverState.emit(it) }
        }
    }

    private fun restoreCachedLocationFromPreferences(preferences: SharedPreferences) {
        val snapshot = CachedLocationStore.read(preferences) ?: return
        val decision = cachedLocationRestoreDecision(
            capturedAtEpochMs = snapshot.capturedAtEpochMs,
            nowEpochMs = System.currentTimeMillis()
        )

        if (!decision.shouldRestore) {
            CachedLocationStore.clear(preferences)
            return
        }

        latestLocation = snapshot.toLocation()
        if (decision.emitsUiLocation) {
            _lastLocation.postValue(
                LocationUpdates.cachedLocation(
                    location = latestLocation!!,
                    capturedAtEpochMs = snapshot.capturedAtEpochMs
                )
            )
        }
    }

    private fun restartServiceObserversIfActive() {
        if (!serviceObserversActive) return
        currentServiceObserverHandle?.dispose()
        nextServiceObserverHandle?.dispose()
        currentServiceObserverHandle = ServiceRepository.observeCurrentService(currentServiceListener)
        nextServiceObserverHandle = ServiceRepository.observeConnectionService(nextServiceListener)
    }

    private fun Location.toLocInterface(): LocInterface {
        val src = this
        return object : LocInterface {
            override var lat: Double = src.latitude
            override var lng: Double = src.longitude
        }
    }

    override fun onCleared() {
        presenceCollectorJob?.cancel()
        presenceCollectorJob = null
        presenceManager.dispose()
        stopServiceObservers()
        super.onCleared()
    }
}
