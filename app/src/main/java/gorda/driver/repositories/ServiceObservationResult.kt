package gorda.driver.repositories

import com.google.firebase.database.DataSnapshot
import gorda.driver.models.Service

sealed class ServiceObservationResult {
    data class Active(val service: Service) : ServiceObservationResult()

    data class Terminal(val service: Service) : ServiceObservationResult()

    object Missing : ServiceObservationResult()

    companion object {
        fun fromSnapshot(snapshot: DataSnapshot): ServiceObservationResult {
            if (!snapshot.exists()) {
                return Missing
            }

            val service = snapshot.getValue(Service::class.java) ?: return Missing
            service.id = snapshot.key.orEmpty()

            return if (service.isTerminal()) {
                Terminal(service)
            } else {
                Active(service)
            }
        }
    }
}
