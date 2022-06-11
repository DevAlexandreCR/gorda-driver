package gorda.driver.activity.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import gorda.driver.R
import gorda.driver.models.Service
import gorda.driver.repositories.ServiceRepository

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<Int>().apply {
        value = R.string.services_list
    }

    private val _serviceList = MutableLiveData<MutableList<Service>>().apply {
        ServiceRepository.getPending {
            value = it
        }
    }

    val serviceList: LiveData<MutableList<Service>> = _serviceList
    val text: LiveData<Int> = _text
}