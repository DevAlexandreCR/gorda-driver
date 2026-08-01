package gorda.driver.ui.home

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import gorda.driver.R
import gorda.driver.repositories.ServiceRepository
import gorda.driver.services.firebase.Auth
import gorda.driver.ui.service.ServicesEventListener
import gorda.driver.ui.service.dataclasses.ServiceUpdates

class HomeViewModel : ViewModel() {

    private val _text = MutableLiveData<Int>().apply {
        value = R.string.services_list
    }
    private val listener: ServicesEventListener = ServicesEventListener(
        driverIdProvider = { Auth.getCurrentUserUUID() }
    ) { services ->
        this._serviceList.postValue(ServiceUpdates.setList(services))
    }
    private val pendingFeedSubscriptionController = PendingFeedSubscriptionController {
        ServiceRepository.observePendingServices(listener)
    }

    private var _serviceList = MutableLiveData<ServiceUpdates>()
    private val _selfServiceEntryVisible = MutableLiveData(false)

    val serviceList: LiveData<ServiceUpdates> = _serviceList
    val text: LiveData<Int> = _text
    val selfServiceEntryVisible: LiveData<Boolean> = _selfServiceEntryVisible

    companion object {
        /**
         * Entry point (add-driver-self-service D4): visible only while the driver session
         * is connected and eligible per `DriverAvailability.canGoOnline`. The API remains
         * the authoritative eligibility check on creation (task 4.3+).
         */
        fun isSelfServiceEntryVisible(connected: Boolean, canGoOnline: Boolean): Boolean {
            return connected && canGoOnline
        }
    }

    fun updateSelfServiceEligibility(connected: Boolean, canGoOnline: Boolean) {
        _selfServiceEntryVisible.value = isSelfServiceEntryVisible(connected, canGoOnline)
    }

    fun startListenServices() {
        pendingFeedSubscriptionController.start()
    }

    fun stopListenServices() {
        this._serviceList.postValue(ServiceUpdates.stopListen())
        pendingFeedSubscriptionController.stop()
    }

    fun restartListenServices() {
        pendingFeedSubscriptionController.restart()
    }

    override fun onCleared() {
        pendingFeedSubscriptionController.stop()
        super.onCleared()
    }
}
