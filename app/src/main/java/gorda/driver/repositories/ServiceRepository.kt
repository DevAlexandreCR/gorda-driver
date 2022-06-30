package gorda.driver.repositories

import com.google.android.gms.tasks.Task
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.ui.service.ServicesEventListener
import gorda.driver.models.Service
import gorda.driver.services.firebase.Database
import java.io.Serializable

object ServiceRepository {

    private var serviceEventListener: ServicesEventListener? = null

    fun getPending(listener: (serviceList: MutableList<Service>) -> Unit) {
        serviceEventListener = ServicesEventListener(listener)
        Database.dbServices().orderByChild("status").equalTo(Service.STATUS_PENDING).limitToLast(100)
            .addValueEventListener(serviceEventListener!!)
    }

    fun stopListenServices() {
        serviceEventListener?.let { listener ->
            Database.dbServices().removeEventListener(listener)
        }
    }

    fun getCurrentServices(listener: (serviceList: MutableList<Service>) -> Unit) {
        serviceEventListener = ServicesEventListener(listener)
        Database.dbServices().orderByChild("status").equalTo(Service.STATUS_IN_PROGRESS).limitToLast(100)
            .addValueEventListener(serviceEventListener!!)
    }

    fun update(service: Service): Task<Void> {
        return Database.dbServices().child(service.id!!).setValue(service)
    }

    fun addApplicant(id: String, driverId: String, distance: String, time: String): Task<Void> {
        return Database.dbServices().child(id).child("applicants").child(driverId).setValue(object: Serializable {
            val distance = distance
            val time = time
        })
    }
}
