package gorda.driver.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.*
import com.google.firebase.database.ktx.getValue
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import gorda.driver.services.firebase.Auth
import gorda.driver.services.firebase.Database
import gorda.driver.ui.service.ServicesEventListener
import java.io.Serializable

object ServiceRepository {

    private var serviceEventListener: ServicesEventListener? = null
    private var newServiceEventListener: ChildEventListener? = null
    private var currentServiceEventListener: ValueEventListener? = null

    fun getPending(listener: (serviceList: MutableList<Service>) -> Unit) {
        serviceEventListener = ServicesEventListener(listener)
        Database.dbServices().orderByChild(Service.STATUS).equalTo(Service.STATUS_PENDING)
            .addValueEventListener(serviceEventListener!!)
    }

    fun listenNewServices(listener: ChildEventListener) {
        newServiceEventListener = listener
        Database.dbServices().orderByChild(Service.STATUS).equalTo(Service.STATUS_PENDING)
            .addChildEventListener(newServiceEventListener!!)
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

    fun addListenerCurrentService(serviceId: String, listener: (service: Service?) -> Unit) {
        currentServiceEventListener = object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    snapshot.getValue<Service>()?.let { service ->
                        if (service.status === Service.STATUS_TERMINATED || service.status === Service.STATUS_CANCELED) {
                            stopListenerCurrentService(service.id)
                            listener(null)
                        }
                        listener(service)
                    }
                }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e(this.javaClass.toString(), error.message)
            }
        }
        currentServiceEventListener?.let {
            Database.dbServices().child(serviceId).addValueEventListener(it)
        }
    }

    fun stopListenerCurrentService(serviceId: String) {
        currentServiceEventListener?.let {
            Database.dbServices().child(serviceId).removeEventListener(it)
        }
    }

    fun isThereCurrentService(listener: (service: Service?) -> Unit) {
        Auth.getCurrentUserUUID()?.let {
            Database.dbDriversAssigned().child(it)
                .addValueEventListener(object: ValueEventListener {
                    override fun onDataChange(snapshot: DataSnapshot) {
                        if (snapshot.exists()) {
                                snapshot.getValue<String>()?.let { serviceId ->
                                    addListenerCurrentService(serviceId, listener)
                                }
                        } else {
                            listener(null)
                        }
                    }
                    override fun onCancelled(error: DatabaseError) {
                        Log.e(this.javaClass.toString(), error.message)
                    }
                })
        }
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
