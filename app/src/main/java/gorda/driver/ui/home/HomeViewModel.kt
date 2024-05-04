package gorda.driver.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import gorda.driver.R
import gorda.driver.repositories.ServiceRepository
import gorda.driver.ui.service.ServicesEventListener
import gorda.driver.ui.service.dataclasses.ServiceUpdates

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<Int>().apply {
        value = R.string.services_list
    }
    private val listener: ServicesEventListener = ServicesEventListener { services ->
        this._serviceList.postValue(ServiceUpdates.setList(services))
    }

    private var _serviceList = MutableLiveData<ServiceUpdates>()

    val serviceList: LiveData<ServiceUpdates> = _serviceList
    val text: LiveData<Int> = _text

    fun startListenServices() {
        ServiceRepository.getPending(listener)
    }

    fun stopListenServices() {
        this._serviceList.postValue(ServiceUpdates.stopListen())
        ServiceRepository.stopListenServices(listener)
    }
}