package gorda.driver.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.Query
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.BuildConfig
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import gorda.driver.services.firebase.Auth
import gorda.driver.services.firebase.Database
import gorda.driver.services.masterData.MasterDataApiService
import gorda.driver.services.retrofit.DriverAppRequestRunner
import gorda.driver.services.retrofit.MasterDataRetrofit
import gorda.driver.ui.service.ServiceEventListener
import gorda.driver.ui.service.ServicesEventListener
import gorda.driver.utils.Constants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.Serializable

object ServiceRepository {

    fun observePendingServices(listener: ServicesEventListener): ServiceObserverHandle {
        val query = pendingServicesQuery()
        query.addValueEventListener(listener)

        return ServiceObserverHandle {
            query.removeEventListener(listener)
        }
    }

    fun observeExactService(serviceId: String, listener: ServiceEventListener): ServiceObserverHandle {
        val ref = Database.dbServices().child(serviceId)
        ref.keepSynced(true)
        listener.setRef(ref)
        ref.addValueEventListener(listener)

        return ServiceObserverHandle {
            ref.removeEventListener(listener)
            ref.keepSynced(false)
        }
    }

    fun observeCurrentService(listener: ServiceEventListener): ServiceObserverHandle {
        val driverId = Auth.getCurrentUserUUID() ?: return ServiceObserverHandle.empty()
        val pointerRef = Database.dbDriversAssigned().child(driverId)
        return observeServicePointer(pointerRef, listener)
    }

    fun observeConnectionService(listener: ServiceEventListener): ServiceObserverHandle {
        val driverId = Auth.getCurrentUserUUID() ?: return ServiceObserverHandle.empty()
        val pointerRef = Database.dbServiceConnections().child(driverId)
        return observeServicePointer(pointerRef, listener)
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

    fun validateServiceForApply(serviceId: String): Task<Service> {
        val taskCompletionSource = TaskCompletionSource<Service>()

        Database.dbServices().child(serviceId).get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    taskCompletionSource.setException(Exception("Service does not exist"))
                    return@addOnSuccessListener
                }

                val service = snapshot.getValue(Service::class.java)
                if (service == null) {
                    taskCompletionSource.setException(Exception("Service data is invalid"))
                    return@addOnSuccessListener
                }

                if (service.driver_id != null && service.driver_id!!.isNotEmpty()) {
                    taskCompletionSource.setException(Exception("Service already has a driver assigned"))
                    return@addOnSuccessListener
                }

                if (service.status != Service.STATUS_PENDING) {
                    taskCompletionSource.setException(Exception("Service is no longer available"))
                    return@addOnSuccessListener
                }

                taskCompletionSource.setResult(service)
            }
            .addOnFailureListener { e ->
                taskCompletionSource.setException(e)
            }

        return taskCompletionSource.task
    }

    fun onStatusChange(serviceId: String, listener: ValueEventListener) {
        getStatusReference(serviceId).addValueEventListener(listener)
    }

    fun getStatusReference(serviceId: String): DatabaseReference {
        return Database.dbServices().child(serviceId).child(Service.STATUS)
    }

    private fun pendingServicesQuery(): Query {
        return Database.dbServices()
            .orderByChild(Service.STATUS)
            .equalTo(Service.STATUS_PENDING)
            .limitToLast(Constants.PENDING_SERVICES_LIMIT)
    }

    private fun observeServicePointer(
        pointerRef: DatabaseReference,
        listener: ServiceEventListener
    ): ServiceObserverHandle {
        pointerRef.keepSynced(true)
        val pointerObserver = ServicePointerObserver(
            observeService = { serviceId -> observeExactService(serviceId, listener) },
            onMissing = { listener.setNull() }
        )

        val valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                pointerObserver.onPointerChanged(snapshot.getValue<String>())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(this.javaClass.toString(), error.message)
                pointerObserver.onPointerChanged(null)
            }
        }

        pointerRef.addValueEventListener(valueEventListener)

        return ServiceObserverHandle {
            pointerRef.removeEventListener(valueEventListener)
            pointerRef.keepSynced(false)
            pointerObserver.dispose()
        }
    }

    fun getHistoryFromDriver(): Task<MutableList<Service>> {
        val taskCompletionSource = TaskCompletionSource<MutableList<Service>>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val service = MasterDataRetrofit.getRetrofit()
                    .create(MasterDataApiService::class.java)
                val response = DriverAppRequestRunner.execute("/driver-app/me/history") { authorization ->
                    service.getDriverHistory(authorization)
                }

                withContext(Dispatchers.Main) {
                    val services = response.body()?.data?.services?.toMutableList() ?: mutableListOf()
                    taskCompletionSource.setResult(services)
                }
            } catch (exception: Exception) {
                Log.e(
                    ServiceRepository::class.java.toString(),
                    "History request failed baseUrl=${BuildConfig.BASE_URL} hasCurrentUser=${Auth.isUserSignedIn()}",
                    exception
                )
                withContext(Dispatchers.Main) {
                    taskCompletionSource.setException(exception)
                }
            }
        }

        return taskCompletionSource.task
    }
}
