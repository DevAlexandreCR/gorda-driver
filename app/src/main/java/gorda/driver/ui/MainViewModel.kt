package gorda.driver.ui

import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
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
import gorda.driver.serializers.RideFeesDeserializer
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.ServiceEventListener
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.dataclasses.ServiceUpdates

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private val TAG: String = MainViewModel::class.java.toString()
    }

    private val _lastLocation = MutableLiveData<LocationUpdates>()
    private val _driverState = MutableLiveData<DriverUpdates>()
    private val _driver: MutableLiveData<Driver?> = savedStateHandle.getLiveData(Driver.TAG)
    private val _serviceUpdates = MutableLiveData<ServiceUpdates>()
    private val _currentService = MutableLiveData<Service?>()
    private val _nextService = MutableLiveData<Service?>()
    private val _isNetWorkConnected = MutableLiveData(true)
    private val _isTripStarted = MutableLiveData(false)
    private val _rideFees = MutableLiveData<RideFees>()
    private val _isLoading = MutableLiveData(false)
    private val _errorTimeout = MutableLiveData(false)
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
            driver.value?.let {
                if (it.id == service.driver_id) {
                    _currentService.postValue(service)
                } else {
                    _currentService.postValue(null)
                }
            }
        }
    }

    val lastLocation: LiveData<LocationUpdates> = _lastLocation
    var driverStatus: LiveData<DriverUpdates> = _driverState
    var driver: LiveData<Driver?> = _driver
    var serviceUpdates: LiveData<ServiceUpdates> = _serviceUpdates
    val currentService: LiveData<Service?> = _currentService
    val nextService: LiveData<Service?> = _nextService
    val isNetWorkConnected: LiveData<Boolean> = _isNetWorkConnected
    val isTripStarted: LiveData<Boolean> = _isTripStarted
    val rideFees: LiveData<RideFees> = _rideFees
    val isLoading: LiveData<Boolean> = _isLoading
    val errorTimeout: LiveData<Boolean> = _errorTimeout

    fun getRideFees() {
        SettingsRepository.getRideFees().addOnSuccessListener { snapshot ->
            _rideFees.postValue(RideFeesDeserializer.getRideFees(snapshot))
        }
        .addOnFailureListener { _ ->
            _rideFees.postValue(RideFees())
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
        _isNetWorkConnected.postValue(isConnected)
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
        _driverState.postValue(DriverUpdates.connecting(true))
        _isLoading.postValue(true)
        DriverRepository.isConnected(driverId) {
            _isLoading.postValue(false)
            _driverState.postValue(DriverUpdates.connecting(false))
            _driverState.postValue(DriverUpdates.setConnected(it))
        }
    }

    fun connecting() {
        _driverState.postValue(DriverUpdates.connecting(true))
        _isLoading.postValue(true)
    }

    fun connected() {
        _driverState.postValue(DriverUpdates.connecting(false))
        _driverState.postValue(DriverUpdates.setConnected(true))
        _isLoading.postValue(false)
    }

    fun updateDriverDevice(driverID: String, device: DeviceInterface?): Task<Void> {
        return DriverRepository.updateDevice(driverID, device)
    }

    fun disconnect(driver: Driver) {
        _driverState.postValue(DriverUpdates.connecting(true))
        _isLoading.postValue(true)
        driver.disconnect().addOnSuccessListener {
            _driverState.postValue(DriverUpdates.connecting(false))
            _driverState.postValue(DriverUpdates.setConnected(false))
            _isLoading.postValue(false)
        }.addOnFailureListener { e ->
            _driverState.postValue(DriverUpdates.setConnected(true))
            _driverState.postValue(DriverUpdates.connecting(false))
            _isLoading.postValue(false)
            e.message?.let { message -> Log.e(TAG, message) }
        }.withTimeout {
            _driverState.postValue(DriverUpdates.setConnected(true))
            _driverState.postValue(DriverUpdates.connecting(false))
            this.setErrorTimeout(true)
        }
    }

    fun setConnectedLocal(connected: Boolean) {
        _driverState.postValue(DriverUpdates.setConnected(connected))
    }

    fun setConnecting(connecting: Boolean) {
        _driverState.postValue(DriverUpdates.connecting(connecting))
    }
}