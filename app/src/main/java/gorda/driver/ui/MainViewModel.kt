package gorda.driver.ui

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
import com.google.firebase.database.ktx.getValue
import gorda.driver.helpers.withTimeout
import gorda.driver.interfaces.DeviceInterface
import gorda.driver.interfaces.LocType
import gorda.driver.interfaces.RideFees
import gorda.driver.models.Driver
import gorda.driver.models.Service
import gorda.driver.repositories.DriverRepository
import gorda.driver.repositories.ServiceRepository
import gorda.driver.repositories.SettingsRepository
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.ServiceEventListener
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.dataclasses.ServiceUpdates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private val TAG: String = MainViewModel::class.java.toString()
    }

    private val _lastLocation = MutableLiveData<LocationUpdates>()

    // Migrated to StateFlow
    private val _driverState = MutableStateFlow<DriverUpdates?>(null)
    val driverStatus: StateFlow<DriverUpdates?> = _driverState.asStateFlow()

    private val _isNetWorkConnected = MutableStateFlow(true)
    val isNetWorkConnected: StateFlow<Boolean> = _isNetWorkConnected.asStateFlow()

    // Track if driver was connected before losing internet (for auto-reconnection)
    private val _wasConnectedBeforeDisconnect = MutableStateFlow(false)
    val wasConnectedBeforeDisconnect: StateFlow<Boolean> = _wasConnectedBeforeDisconnect.asStateFlow()

    private val _shouldAttemptReconnect = MutableStateFlow(false)
    val shouldAttemptReconnect: StateFlow<Boolean> = _shouldAttemptReconnect.asStateFlow()

    // Keep remaining LiveData
    private val _driver: MutableLiveData<Driver?> = savedStateHandle.getLiveData(Driver.TAG)
    private val _serviceUpdates = MutableLiveData<ServiceUpdates>()
    private val _currentService = MutableLiveData<Service?>()
    private val _nextService = MutableLiveData<Service?>()
    private val _isTripStarted = MutableLiveData(false)
    private val _rideFees = MutableLiveData<RideFees>()
    private val _isLoading = MutableLiveData(false)
    private val _errorTimeout = MutableLiveData(false)

    // Add fee tracking LiveData
    private val _currentFeeData = MutableLiveData<FeeData>()

    data class FeeData(
        val totalFee: Double,
        val timeFee: Double,
        val distanceFee: Double,
        val totalDistance: Double,
        val elapsedSeconds: Long
    )

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
    var serviceUpdates: LiveData<ServiceUpdates> = _serviceUpdates
    val currentService: LiveData<Service?> = _currentService
    val nextService: LiveData<Service?> = _nextService
    val isTripStarted: LiveData<Boolean> = _isTripStarted
    val rideFees: LiveData<RideFees> = _rideFees
    val isLoading: LiveData<Boolean> = _isLoading
    val errorTimeout: LiveData<Boolean> = _errorTimeout
    val currentFeeData: LiveData<FeeData> = _currentFeeData

    fun getRideFees() {
        SettingsRepository.getRideFees {
            _rideFees.postValue(it)
        }
    }

    fun setLoading(loading: Boolean) {
        _isLoading.postValue(loading)
    }

    fun setErrorTimeout(error: Boolean) {
        _errorTimeout.postValue(error)
    }

    fun changeConnectTripService(connect: Boolean) {
        _isTripStarted.postValue(connect)
    }

    fun changeNetWorkStatus(isConnected: Boolean) {
        viewModelScope.launch {
            val previousState = _isNetWorkConnected.value
            val currentDriverState = _driverState.value

            Log.d(TAG, "Network status changed: $isConnected (previous: $previousState)")

            _isNetWorkConnected.emit(isConnected)

            // If network was lost, remember if we were connected
            if (!isConnected && previousState) {
                if (currentDriverState is DriverUpdates.IsConnected && currentDriverState.connected) {
                    Log.d(TAG, "Network lost while driver was connected, saving state for reconnection")
                    _wasConnectedBeforeDisconnect.emit(true)
                    _shouldAttemptReconnect.emit(true)
                }
            }

            // If network is recovered and we should reconnect
            if (isConnected && !previousState) {
                Log.d(TAG, "Network recovered! shouldAttemptReconnect=${_shouldAttemptReconnect.value}")
                if (_shouldAttemptReconnect.value) {
                    Log.d(TAG, "Triggering automatic reconnection...")
                    // Force emit to ensure MainActivity detects the change
                    _shouldAttemptReconnect.emit(false)
                    _shouldAttemptReconnect.emit(true)
                }
            }
        }
    }

    fun setServiceUpdateStartLocation(starLoc: LocType) {
        _serviceUpdates.postValue(ServiceUpdates.setStarLoc(starLoc))
    }

    fun isThereCurrentService() {
        ServiceRepository.isThereCurrentService(currentServiceListener)
    }

    fun isThereConnectionService() {
        ServiceRepository.isThereConnectionService(nextServiceListener)
    }

    fun stopNextServiceListener() {
        _nextService.value?.let { _ ->
            ServiceRepository.stopListenNextService(nextServiceListener)
        }
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
        _lastLocation.postValue(LocationUpdates.lastLocation(location))
    }

    fun getDriver(driverId: String) {
        _isLoading.postValue(true)
        DriverRepository.getDriver(driverId) { driver ->
            _isLoading.postValue(false)
            _driver.postValue(driver)
            savedStateHandle[Driver.TAG] = driver
        }
    }

    fun isConnected(driverId: String) {
        viewModelScope.launch {
            _driverState.emit(DriverUpdates.connecting(true))
            _isLoading.postValue(true)
            DriverRepository.isConnected(driverId) { connected ->
                _isLoading.postValue(false)
                viewModelScope.launch {
                    _driverState.emit(DriverUpdates.connecting(false))
                    _driverState.emit(DriverUpdates.setConnected(connected))
                }
            }
        }
    }

    fun connecting() {
        viewModelScope.launch {
            _driverState.emit(DriverUpdates.connecting(true))
        }
        _isLoading.postValue(true)
    }

    fun connected() {
        viewModelScope.launch {
            _driverState.emit(DriverUpdates.connecting(false))
            _driverState.emit(DriverUpdates.setConnected(true))
            _shouldAttemptReconnect.emit(false)
            _wasConnectedBeforeDisconnect.emit(false)
        }
        _isLoading.postValue(false)
    }

    fun updateDriverDevice(driverID: String, device: DeviceInterface?): Task<Void> {
        return DriverRepository.updateDevice(driverID, device)
    }

    fun disconnect(driver: Driver) {
        viewModelScope.launch {
            _driverState.emit(DriverUpdates.connecting(true))
        }
        _isLoading.postValue(true)
        driver.disconnect().addOnSuccessListener {
            viewModelScope.launch {
                _driverState.emit(DriverUpdates.connecting(false))
                _driverState.emit(DriverUpdates.setConnected(false))
                _wasConnectedBeforeDisconnect.emit(false)
                _shouldAttemptReconnect.emit(false)
            }
            _isLoading.postValue(false)
        }.addOnFailureListener { e ->
            viewModelScope.launch {
                _driverState.emit(DriverUpdates.setConnected(true))
                _driverState.emit(DriverUpdates.connecting(false))
            }
            _isLoading.postValue(false)
            e.message?.let { message -> Log.e(TAG, message) }
        }.withTimeout {
            viewModelScope.launch {
                _driverState.emit(DriverUpdates.setConnected(true))
                _driverState.emit(DriverUpdates.connecting(false))
            }
            this.setErrorTimeout(true)
        }
    }

    fun setConnectedLocal(connected: Boolean) {
        viewModelScope.launch {
            _driverState.emit(DriverUpdates.setConnected(connected))
            if (!connected) {
                _wasConnectedBeforeDisconnect.emit(false)
                _shouldAttemptReconnect.emit(false)
            }
        }
    }

    fun setConnecting(connecting: Boolean) {
        viewModelScope.launch {
            _driverState.emit(DriverUpdates.connecting(connecting))
        }
    }

    fun clearReconnectionFlag() {
        viewModelScope.launch {
            _shouldAttemptReconnect.emit(false)
        }
    }

    // Add method to update fee data from the service
    fun updateFeeData(totalFee: Double, timeFee: Double, distanceFee: Double, totalDistance: Double, elapsedSeconds: Long) {
        val feeData = FeeData(totalFee, timeFee, distanceFee, totalDistance, elapsedSeconds)
        _currentFeeData.postValue(feeData)
    }
}