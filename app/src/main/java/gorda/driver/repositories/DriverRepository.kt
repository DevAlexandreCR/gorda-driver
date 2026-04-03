package gorda.driver.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import gorda.driver.BuildConfig
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

    fun connect(driver: DriverInterface, location: LocInterface): Task<Void> {
        return Database.dbOnlineDrivers().child(driver.id).setValue(object : DriverConnected {
            override var id: String = driver.id
            override var location: LocInterface = location
            override var version: String = BuildConfig.VERSION_NAME
        })
    }

    fun disconnect(driverId: String): Task<Void> {
        return Database.dbOnlineDrivers().child(driverId).removeValue()
    }

    fun updateLocation(driverId: String, location: LocInterface) {
        Database.dbOnlineDrivers().child(driverId).child("location").setValue(location)
    }

    fun isConnected(driverId: String, listener: (connected: Boolean) -> Unit) {
        Database.dbOnlineDrivers().child(driverId).get().addOnSuccessListener { snapshot ->
            listener(snapshot.hasChildren())
        }.addOnFailureListener {
            Log.e(TAG, it.message!!)
        }.withTimeout {
            listener(false)
            Log.e(TAG, "Timeout checking driver connection")
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
