package gorda.godriver.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import gorda.godriver.R
import gorda.godriver.repositories.ServiceRepository
import gorda.godriver.ui.service.ServicesEventListener
import gorda.godriver.ui.service.dataclasses.ServiceUpdates

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