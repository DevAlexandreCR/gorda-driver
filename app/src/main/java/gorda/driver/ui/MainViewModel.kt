package gorda.driver.ui

import android.location.Location
import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.LocationUpdates
import gorda.driver.models.Driver
import gorda.driver.repositories.DriverRepository
import gorda.driver.services.firebase.Auth

class MainViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    companion object {
        private val TAG: String = MainViewModel::class.java.toString()
    }
    private val _lastLocation = MutableLiveData<LocationUpdates>()
    private val _driverState = MutableLiveData<DriverUpdates>()
    private val _driver = MutableLiveData<Driver>()

    val lastLocation: LiveData<LocationUpdates> = _lastLocation
    var driverStatus: LiveData<DriverUpdates> = _driverState
    var driver: LiveData<Driver> = savedStateHandle.getLiveData(Driver::class.java.toString())

    fun updateLocation(location: Location) {
        _lastLocation.postValue(LocationUpdates.lastLocation(location))
    }

    fun getDriver(driverId: String) {
        DriverRepository.getDriver(driverId) { driver ->
            _driver.postValue(driver)
            savedStateHandle[Driver::class.java.toString()] = driver
        }
    }

    fun setAuth() {
        _driverState.postValue(DriverUpdates.setUUID(Auth.getCurrentUserUUID()))
        Auth.onAuthChanges { uuid ->
            _driverState.postValue(DriverUpdates.setUUID(uuid))
        }
    }

    fun isConnected(driverId: String) {
        DriverRepository.isConnected(driverId) {
            _driverState.postValue(DriverUpdates.setConnected(it))
        }
    }

    fun connect(driver: Driver) {
        _driverState.postValue(DriverUpdates.connecting(true))
        driver.connect().addOnSuccessListener {
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

    fun connecting(connecting: Boolean) {
        _driverState.postValue(DriverUpdates.connecting(connecting))
    }
}