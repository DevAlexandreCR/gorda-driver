package gorda.driver.ui.history

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import gorda.driver.R
import gorda.driver.models.Service
import gorda.driver.repositories.ServiceRepository

class HistoryViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private val _text = MutableLiveData<Int>().apply {
        value = R.string.services_list
    }

    private var _serviceList = MutableLiveData<MutableList<Service>>()

    val serviceList: LiveData<MutableList<Service>> = _serviceList
    val text: LiveData<Int> = _text

    fun getServices() {
        ServiceRepository.getHistoryFromDriver().addOnSuccessListener { services ->
            _serviceList.postValue(services)
        }.addOnFailureListener {
            Log.e(HistoryFragment::javaClass.toString(), "Error getting services", it)
        }
    }
}