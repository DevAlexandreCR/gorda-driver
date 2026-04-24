package gorda.driver.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import gorda.driver.BuildConfig
import gorda.driver.exceptions.UnsupportedAppVersionException
import gorda.driver.helpers.withTimeout
import gorda.driver.interfaces.Device
import gorda.driver.interfaces.DeviceInterface
import gorda.driver.interfaces.DriverConnected
import gorda.driver.interfaces.DriverInterface
import gorda.driver.interfaces.LocInterface
import gorda.driver.models.Driver
import gorda.driver.services.firebase.Database
import gorda.driver.services.masterData.DevicePayload
import gorda.driver.services.masterData.MasterDataApiService
import gorda.driver.services.retrofit.MasterDataRetrofit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object DriverRepository {

    val TAG = DriverRepository::class.java.toString()

    data class DriverPresenceSnapshot(
        val exists: Boolean,
        val sessionId: String?,
        val lastSeenAt: Long?
    )

    fun connect(
        driver: DriverInterface,
        location: LocInterface,
        sessionId: String,
        lastSeenAt: Long
    ): Task<Void> {
        val taskSource = TaskCompletionSource<Void>()
        val driverReference = Database.dbOnlineDrivers().child(driver.id)

        driverReference.onDisconnect().removeValue { disconnectError, _ ->
            when {
                disconnectError == null -> {
                    driverReference.setValue(object : DriverConnected {
                        override var id: String = driver.id
                        override var location: LocInterface = location
                        override var version: String = BuildConfig.VERSION_NAME
                        override var versionCode: Int = BuildConfig.VERSION_CODE
                        override var last_seen_at: Long = lastSeenAt
                        override var session_id: String = sessionId
                    }) { error, _ ->
                        when {
                            error == null -> taskSource.setResult(null)
                            error.code == DatabaseError.PERMISSION_DENIED -> {
                                taskSource.setException(UnsupportedAppVersionException())
                            }
                            else -> taskSource.setException(error.toException())
                        }
                    }
                }
                disconnectError.code == DatabaseError.PERMISSION_DENIED -> {
                    taskSource.setException(UnsupportedAppVersionException())
                }
                else -> taskSource.setException(disconnectError.toException())
            }
        }

        return taskSource.task
    }

    fun disconnect(driverId: String): Task<Void> {
        val taskSource = TaskCompletionSource<Void>()
        val driverReference = Database.dbOnlineDrivers().child(driverId)

        driverReference.onDisconnect().cancel { cancelError, _ ->
            when {
                cancelError == null -> {
                    driverReference.removeValue()
                        .addOnSuccessListener { taskSource.setResult(null) }
                        .addOnFailureListener { taskSource.setException(it) }
                }
                cancelError.code == DatabaseError.PERMISSION_DENIED -> {
                    taskSource.setException(UnsupportedAppVersionException())
                }
                else -> taskSource.setException(cancelError.toException())
            }
        }

        return taskSource.task
    }

    fun updateLocation(driverId: String, location: LocInterface, lastSeenAt: Long): Task<Void> {
        return Database.dbOnlineDrivers().child(driverId).updateChildren(
            mapOf(
                "location" to location,
                "last_seen_at" to lastSeenAt
            )
        )
    }

    fun observePresence(
        driverId: String,
        listener: (presence: DriverPresenceSnapshot?) -> Unit
    ): ValueEventListener {
        val valueEventListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (!snapshot.exists()) {
                    listener(null)
                    return
                }

                val sessionId = snapshot.child("session_id").getValue(String::class.java)
                val lastSeenAt = snapshot.child("last_seen_at").getValue(Long::class.java)
                listener(
                    DriverPresenceSnapshot(
                        exists = true,
                        sessionId = sessionId,
                        lastSeenAt = lastSeenAt
                    )
                )
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, error.message)
                listener(null)
            }
        }

        Database.dbOnlineDrivers().child(driverId).addValueEventListener(valueEventListener)
        return valueEventListener
    }

    fun removePresenceObserver(driverId: String, listener: ValueEventListener) {
        Database.dbOnlineDrivers().child(driverId).removeEventListener(listener)
    }

    fun validateDriverVersion(listener: (result: Result<Int>) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = MasterDataRetrofit.getRetrofit()
                    .create(MasterDataApiService::class.java)
                    .getVersionPolicy()

                withContext(Dispatchers.Main) {
                    if (!response.isSuccessful) {
                        listener(Result.failure(IllegalStateException(response.message())))
                        return@withContext
                    }

                    val minVersionCode = response.body()?.data?.versionPolicy?.driver?.minVersionCode
                    if (minVersionCode == null) {
                        listener(Result.failure(IllegalStateException("Missing driver version policy")))
                        return@withContext
                    }

                    if (BuildConfig.VERSION_CODE < minVersionCode) {
                        listener(Result.failure(UnsupportedAppVersionException()))
                    } else {
                        listener(Result.success(minVersionCode))
                    }
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    listener(Result.failure(exception))
                }
            }
        }
    }

    fun getDriver(driverId: String, listener: (driver: Driver?) -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = MasterDataRetrofit.getRetrofit()
                    .create(MasterDataApiService::class.java)
                    .getDriver(driverId)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        listener(response.body()?.data?.driver)
                    } else {
                        listener(null)
                        Log.e(TAG, response.message())
                    }
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    listener(null)
                    Log.e(TAG, exception.message ?: "Unknown error")
                }
            }
        }
    }

    fun getDriverTask(driverId: String): Task<Driver> {
        val taskSource = TaskCompletionSource<Driver>()

        getDriver(driverId) { driver ->
            if (driver != null) {
                taskSource.setResult(driver)
            } else {
                taskSource.setException(IllegalStateException("Unable to refresh driver"))
            }
        }

        return taskSource.task
    }

    fun updateDevice(driverID: String, device: DeviceInterface?): Task<Void> {
        val taskSource = TaskCompletionSource<Void>()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = MasterDataRetrofit.getRetrofit()
                    .create(MasterDataApiService::class.java)
                    .updateDevice(
                        driverID,
                        DevicePayload(
                            device = device?.let {
                                Device(id = it.id, name = it.name)
                            }
                        )
                    )

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        taskSource.setResult(null)
                    } else {
                        taskSource.setException(IllegalStateException(response.message()))
                    }
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    taskSource.setException(exception)
                }
            }
        }

        return taskSource.task
    }
}
