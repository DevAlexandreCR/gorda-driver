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
         * Entry point (add-driver-self-service D4; fix-driver-fee-service-zombie-ticker D6):
         * visible only while the driver session is connected, eligible per
         * `DriverAvailability.canGoOnline`, and has no active trip. The API remains
         * the authoritative eligibility check on creation (task 4.3+).
         */
        fun isSelfServiceEntryVisible(
            connected: Boolean,
            canGoOnline: Boolean,
            hasActiveTrip: Boolean
        ): Boolean {
            return connected && canGoOnline && !hasActiveTrip
        }

        /**
         * Start-admission gate (design D6): a self-service trip cannot start while the driver
         * has an active trip (an assigned/in-progress service, or an unsynced self-service trip).
         */
        fun resolveSelfServiceStart(
            hasCurrentService: Boolean,
            hasPendingSelfServiceTrip: Boolean
        ): SelfServiceStartResolution {
            return if (hasCurrentService || hasPendingSelfServiceTrip) {
                SelfServiceStartResolution.BLOCK_ACTIVE_TRIP
            } else {
                SelfServiceStartResolution.ALLOW
            }
        }
    }

    enum class SelfServiceStartResolution {
        ALLOW,
        BLOCK_ACTIVE_TRIP
    }

    fun updateSelfServiceEligibility(connected: Boolean, canGoOnline: Boolean, hasActiveTrip: Boolean) {
        _selfServiceEntryVisible.value = isSelfServiceEntryVisible(connected, canGoOnline, hasActiveTrip)
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
