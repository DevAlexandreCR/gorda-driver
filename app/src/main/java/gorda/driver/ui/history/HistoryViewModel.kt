package gorda.driver.ui.history

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import com.google.android.gms.tasks.Task
import gorda.driver.models.Service
import gorda.driver.repositories.ServiceRepository

class HistoryViewModel(private val savedStateHandle: SavedStateHandle) : ViewModel() {

    private var _serviceList = MutableLiveData<MutableList<Service>>()
    private var _summary = MutableLiveData<Double>()

    val serviceList: LiveData<MutableList<Service>> = _serviceList
    val summary: LiveData<Double> = _summary

    private fun setSummary(summary: Double) {
        _summary.postValue(summary)
    }

    fun getServices(): Task<MutableList<Service>> {
        return ServiceRepository.getHistoryFromDriver().addOnSuccessListener { services ->
            _serviceList.postValue(services)
            var totalAmount = 0.0
            services.forEach { service ->
                if (service.metadata.trip_fee != null) {
                    totalAmount += service.metadata.trip_fee!!
                }
            }
            setSummary(totalAmount)
        }.addOnFailureListener {
            Log.e(HistoryFragment::javaClass.toString(), "Error getting services", it)
        }
    }
}