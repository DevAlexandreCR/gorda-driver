package gorda.driver.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import gorda.driver.R
import gorda.driver.ui.service.ServiceUpdates
import gorda.driver.repositories.ServiceRepository

class HomeViewModel() : ViewModel() {

    private val _text = MutableLiveData<Int>().apply {
        value = R.string.services_list
    }

    private var _serviceList = MutableLiveData<ServiceUpdates>()

    val serviceList: LiveData<ServiceUpdates> = _serviceList
    val text: LiveData<Int> = _text

    fun startListenServices() {
        ServiceRepository.getPending { services ->
            this._serviceList.postValue(ServiceUpdates.setList(services))
        }
    }

    fun stopListenServices() {
        ServiceRepository.stopListenServices()
        this._serviceList.postValue(ServiceUpdates.stopListen())
    }
}