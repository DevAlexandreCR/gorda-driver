package gorda.driver.repositories

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.interfaces.OnStatusChangeListener
import gorda.driver.ui.service.ServicesEventListener
import gorda.driver.models.Service
import gorda.driver.services.firebase.Database
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

    fun addApplicant(id: String, driverId: String, distance: String, time: String): Task<Void> {
        return Database.dbServices().child(id).child(Service.APPLICANTS).child(driverId).setValue(object: Serializable {
            val distance = distance
            val time = time
        })
    }

    fun cancelApply(id: String, driverId: String): Task<Void> {
        return Database.dbServices().child(id).child(Service.APPLICANTS).child(driverId)
            .removeValue()
    }

    fun onStatusChange(serviceId: String, listener: OnStatusChangeListener) {
        Database.dbServices().child(serviceId).child(Service.STATUS)
            .addListenerForSingleValueEvent(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val status = snapshot.getValue<String>()
                    status?.let {
                        listener.onChange(it)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    listener.onFailure(error)
                }

            })
    }
}
