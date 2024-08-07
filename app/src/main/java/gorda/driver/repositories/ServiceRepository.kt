package gorda.driver.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import gorda.driver.services.firebase.Auth
import gorda.driver.services.firebase.Database
import gorda.driver.services.firebase.FirestoreDatabase
import gorda.driver.ui.service.ServiceEventListener
import gorda.driver.ui.service.ServicesEventListener
import java.io.Serializable
import java.util.Calendar

object ServiceRepository {

    fun getPending(listener: ServicesEventListener) {
        Database.dbServices().orderByChild(Service.STATUS).equalTo(Service.STATUS_PENDING)
            .addValueEventListener(listener)
    }

    fun listenNewServices(listener: ChildEventListener) {
        Database.dbServices().orderByChild(Service.STATUS).equalTo(Service.STATUS_PENDING)
            .addChildEventListener(listener)
    }

    fun stopListenServices(listener: ServicesEventListener) {
        Database.dbServices().orderByChild(Service.STATUS).equalTo(Service.STATUS_PENDING)
            .removeEventListener(listener)
    }

    fun startListenNextService(serviceId: String, listener: ServiceEventListener) {
        val ref: DatabaseReference = Database.dbServices().child(serviceId)
        listener.setRef(ref)
        listener.reference?.addValueEventListener(listener)
    }

    fun stopListenNextService(listener: ServiceEventListener) {
        listener.setNull()
    }

    fun stopListenNewServices(listener: ChildEventListener) {
        Database.dbServices().orderByChild(Service.STATUS).equalTo(Service.STATUS_PENDING)
            .removeEventListener(listener)
    }

    fun addListenerCurrentService(serviceId: String, listener: ServiceEventListener) {
        val ref: DatabaseReference = Database.dbServices().child(serviceId)
        listener.setRef(ref)
        listener.reference?.addValueEventListener(listener)
    }

    fun isThereCurrentService(listener: ServiceEventListener) {
        Auth.getCurrentUserUUID()?.let {
            Database.dbDriversAssigned().child(it).addValueEventListener(object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        snapshot.getValue<String>()?.let { serviceId ->
                            addListenerCurrentService(serviceId, listener)
                        }
                    } else {
                        listener.setNull()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(this.javaClass.toString(), error.message)
                    listener.setNull()
                }
            })
        }
    }

    fun isThereConnectionService(listener: ServiceEventListener) {
        Auth.getCurrentUserUUID()?.let {
            Database.dbServiceConnections().child(it).addValueEventListener(object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        snapshot.getValue<String>()?.let { serviceId ->
                            startListenNextService(serviceId, listener)
                        }
                    } else {
                        listener.setNull()
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    Log.e(this.javaClass.toString(), error.message)
                }
            })
        }
    }

    fun validateAssignment(serviceId: String): Task<Boolean> {
        val taskCompletionSource = TaskCompletionSource<Boolean>()
        Auth.getCurrentUserUUID()?.let {
            Database.dbServices().child(serviceId).child(Service.DRIVER_ID)
                .get().addOnSuccessListener { snapshot ->
                    if (snapshot.exists()) {
                        val driverId = snapshot.value
                        taskCompletionSource.setResult(driverId === it)
                    } else {
                        taskCompletionSource.setResult(false)
                    }
                }.addOnFailureListener { e-> taskCompletionSource.setException(e) }
        }

        return taskCompletionSource.task
    }

    fun updateMetadata(serviceId: String, metadata: ServiceMetadata, status: String): Task<Void> {
        val updates = hashMapOf<String, Any>(
            "metadata" to metadata,
            "status" to status
        )

        val taskMetadata = Database.dbServices().child(serviceId).updateChildren(updates)

        return Tasks.whenAll(taskMetadata)
    }

    fun addApplicant(id: String, driverId: String, distance: Int, time: Int, connection: String? = null): Task<Void> {
        return Database.dbServices().child(id).child(Service.APPLICANTS).child(driverId).setValue(object: Serializable {
            val distance = distance
            val time = time
            val connection = connection
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

    fun getHistoryFromDriver(): Task<MutableList<Service>> {
        val taskCompletionSource = TaskCompletionSource<MutableList<Service>>()

        val calendar = Calendar.getInstance()
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        val startOfDayTimestamp = calendar.timeInMillis / 1000

        Auth.getCurrentUserUUID()?.let { driverId ->
            FirestoreDatabase.fsServices()
                .whereGreaterThanOrEqualTo(Service.CREATED_AT, startOfDayTimestamp)
                .whereEqualTo(Service.DRIVER_ID, driverId)
                .get()
                .addOnSuccessListener { querySnapshot ->
                    val services = mutableListOf<Service>()
                    querySnapshot.documents.forEach { document ->
                        document.toObject(Service::class.java)?.let { service ->
                            services.add(service)
                        }
                    }
                    taskCompletionSource.setResult(services)
                }
                .addOnFailureListener { exception ->
                    taskCompletionSource.setException(exception)
                }
        } ?: run {
            taskCompletionSource.setException(IllegalStateException("User UUID is null"))
        }

        return taskCompletionSource.task
    }
}
