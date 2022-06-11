package gorda.driver.activity.ui

import android.location.Location
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import gorda.driver.activity.ui.service.LocationUpdates

class MainViewModel : ViewModel() {
    private val _lastLocation = MutableLiveData<LocationUpdates>()

    val lastLocation: LiveData<LocationUpdates> = _lastLocation

    fun updateLocation(location: Location) {
        _lastLocation.postValue(LocationUpdates.lastLocation(location))
    }
}