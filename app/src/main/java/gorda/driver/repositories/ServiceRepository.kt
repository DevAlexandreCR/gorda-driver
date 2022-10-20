package gorda.driver.repositories

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.*
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import gorda.driver.services.firebase.Database
import gorda.driver.ui.service.ServicesEventListener
import java.io.Serializable

object ServiceRepository {

    private var serviceEventListener: ServicesEventListener? = null
    private var newServiceEventListener: ChildEventListener? = null

    fun getPending(listener: (serviceList: MutableList<Service>) -> Unit) {
        serviceEventListener = ServicesEventListener("", listener)
        Database.dbServices().orderByChild(Service.STATUS).equalTo(Service.STATUS_PENDING).limitToLast(100)
            .addValueEventListener(serviceEventListener!!)
    }

    fun listenNewServices(listener: ChildEventListener) {
        newServiceEventListener = listener
        Database.dbServices().orderByChild(Service.STATUS).equalTo(Service.STATUS_PENDING)
            .limitToLast(10).addChildEventListener(newServiceEventListener!!)
    }

    fun stopListenServices() {
        serviceEventListener?.let { listener ->
            Database.dbServices().removeEventListener(listener)
        }
    }

    fun stopListenNewServices() {
        newServiceEventListener?.let { listener ->
            Database.dbServices().removeEventListener(listener)
        }
    }

    fun getCurrentServices(driverID: String, listener: (serviceList: MutableList<Service>) -> Unit) {
        serviceEventListener = ServicesEventListener(driverID, listener)
        Database.dbServices().orderByChild(Service.DRIVER_ID).equalTo(driverID).limitToLast(1000)
            .addValueEventListener(serviceEventListener!!)
    }

    fun updateMetadata(serviceId: String, metadata: ServiceMetadata, status: String): Task<Void> {
        val taskMetadata = Database.dbServices().child(serviceId).child("metadata").setValue(metadata)
        val taskStatus = Database.dbServices().child(serviceId).child("status").setValue(status)

        return Tasks.whenAll(taskMetadata, taskStatus)
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
