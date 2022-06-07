package gorda.driver.activity.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import gorda.driver.models.Service
import gorda.driver.repositories.ServiceRepository

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<String>().apply {
        value = "This is home Fragmento"
    }

    private val _serviceList = MutableLiveData<MutableList<Service>>().apply {
        ServiceRepository.getPending {
            value = it
        }
    }

    val serviceList: LiveData<MutableList<Service>> = _serviceList
    val text: LiveData<String> = _text
}