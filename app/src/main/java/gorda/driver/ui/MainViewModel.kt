package gorda.driver.ui

import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.interfaces.LocInterface
import gorda.driver.interfaces.LocType
import gorda.driver.models.Driver
import gorda.driver.models.Service
import gorda.driver.repositories.DriverRepository
import gorda.driver.repositories.ServiceRepository
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.dataclasses.ServiceUpdates

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private val TAG: String = MainViewModel::class.java.toString()
    }

    private val _lastLocation = MutableLiveData<LocationUpdates>()
    private val _driverState = MutableLiveData<DriverUpdates>()
    private val _driver = savedStateHandle.getLiveData<Driver>(Driver.TAG)
    private val _serviceUpdates = MutableLiveData<ServiceUpdates>()
    private val _currentService = MutableLiveData<Service?>()
    private val _isNetWorkConnected = MutableLiveData(true)

    val lastLocation: LiveData<LocationUpdates> = _lastLocation
    var driverStatus: LiveData<DriverUpdates> = _driverState
    var driver: LiveData<Driver> = _driver
    var serviceUpdates: LiveData<ServiceUpdates> = _serviceUpdates
    val currentService: LiveData<Service?> = _currentService
    val isNetWorkConnected: LiveData<Boolean> = _isNetWorkConnected

    fun changeNetWorkStatus(isConnected: Boolean) {
        _isNetWorkConnected.postValue(isConnected)
    }

    fun setServiceUpdateStartLocation(starLoc: LocType) {
        _serviceUpdates.postValue(ServiceUpdates.setStarLoc(starLoc))
    }

    fun isThereCurrentService() {
        ServiceRepository.isThereCurrentService { service ->
            _currentService.postValue(service)
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
                                service.getStatusReference().removeEventListener(this)
                            }
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
        DriverRepository.getDriver(driverId) { driver ->
            _driver.postValue(driver)
            savedStateHandle[Driver.TAG] = driver
        }
    }

    fun isConnected(driverId: String) {
        _driverState.postValue(DriverUpdates.connecting(true))
        DriverRepository.isConnected(driverId) {
            _driverState.postValue(DriverUpdates.connecting(false))
            _driverState.postValue(DriverUpdates.setConnected(it))
        }
    }

    fun connect(driver: Driver) {
        _driverState.postValue(DriverUpdates.connecting(true))

        driver.connect(object: LocInterface {
            override var lat: Double = 2.4448143
            override var lng: Double = -76.6147395
        }).addOnSuccessListener {
            _driverState.postValue(DriverUpdates.connecting(false))
            _driverState.postValue(DriverUpdates.setConnected(true))
        }.addOnFailureListener { e ->
            _driverState.postValue(DriverUpdates.setConnected(false))
            _driverState.postValue(DriverUpdates.connecting(false))
            e.message?.let { message -> Log.e(TAG, message) }
        }
    }

    fun disconnect(driver: Driver) {
        driver.disconnect().addOnSuccessListener {
            _driverState.postValue(DriverUpdates.setConnected(false))
        }.addOnFailureListener { e ->
            _driverState.postValue(DriverUpdates.setConnected(true))
            e.message?.let { message -> Log.e(TAG, message) }
        }
    }

    fun setConnectedLocal(connected: Boolean) {
        _driverState.postValue(DriverUpdates.setConnected(connected))
    }
}