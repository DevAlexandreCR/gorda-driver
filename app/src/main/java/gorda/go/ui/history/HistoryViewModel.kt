package gorda.go.ui.history

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import gorda.go.models.Service
import gorda.go.repositories.ServiceRepository

class HistoryViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private var _serviceList = MutableLiveData<MutableList<Service>>()

    val serviceList: LiveData<MutableList<Service>> = _serviceList

    fun getServices(): Task<MutableList<Service>> {
        return ServiceRepository.getHistoryFromDriver().addOnSuccessListener { services ->
            _serviceList.postValue(services)
        }.addOnFailureListener {
            Log.e(HistoryFragment::javaClass.toString(), "Error getting services", it)
        }
    }
}