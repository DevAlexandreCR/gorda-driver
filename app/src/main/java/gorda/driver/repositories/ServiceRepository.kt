package gorda.driver.repositories

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import gorda.driver.models.Service
import gorda.driver.services.firebase.Database
import gorda.driver.ui.service.ServicesEventListener
import java.io.Serializable

object ServiceRepository {

    private var serviceEventListener: ServicesEventListener? = null

    fun getPending(listener: (serviceList: MutableList<Service>) -> Unit) {
        serviceEventListener = ServicesEventListener(listener)
        Database.dbServices().orderByChild(Service.STATUS).equalTo(Service.STATUS_PENDING).limitToLast(100)
            .addValueEventListener(serviceEventListener!!)
    }

    fun stopListenServices() {
        serviceEventListener?.let { listener ->
            Database.dbServices().removeEventListener(listener)
        }
    }

    fun getCurrentServices(listener: (serviceList: MutableList<Service>) -> Unit) {
        serviceEventListener = ServicesEventListener(listener)
        Database.dbServices().orderByChild(Service.STATUS).equalTo(Service.STATUS_IN_PROGRESS).limitToLast(100)
            .addValueEventListener(serviceEventListener!!)
    }

    fun update(service: Service): Task<Void> {
        return Database.dbServices().child(service.id!!).setValue(service)
    }

    fun addApplicant(id: String, driverId: String, distance: Int, time: Int): Task<Void> {
        return Database.dbServices().child(id).child(Service.APPLICANTS).child(driverId).setValue(object: Serializable {
            val distance = distance
            val time = time
        })
    }

    fun cancelApply(id: String, driverId: String): Task<Void> {
        return Database.dbServices().child(id).child(Service.APPLICANTS).child(driverId)
            .removeValue()
    }

    fun onStatusChange(serviceId: String, listener: ValueEventListener) {
        getStatusReference(serviceId).addValueEventListener(listener)
    }

    fun getStatusReference(serviceId: String): DatabaseReference {
        return Database.dbServices().child(serviceId).child(Service.STATUS)
    }
}
