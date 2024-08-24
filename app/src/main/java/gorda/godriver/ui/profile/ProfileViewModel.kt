package gorda.godriver.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import gorda.godriver.models.Driver

class ProfileViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {
    private val _driver = MutableLiveData<Driver>()

    val driver: LiveData<Driver> = savedStateHandle.getLiveData(Driver.TAG)

    fun setDriver(driver: Driver) {
        _driver.postValue(driver)
        savedStateHandle[Driver.TAG] = driver
    }
}