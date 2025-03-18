package gorda.driver.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.database.ktx.getValue
import gorda.driver.BuildConfig
import gorda.driver.helpers.withTimeout
import gorda.driver.interfaces.DeviceInterface
import gorda.driver.interfaces.DriverConnected
import gorda.driver.interfaces.DriverInterface
import gorda.driver.interfaces.LocInterface
import gorda.driver.models.Driver
import gorda.driver.services.firebase.Database

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
        Database.dbDrivers().child(driverId).get().addOnSuccessListener { snapshot ->
            listener(snapshot.getValue<Driver?>())
        }.addOnFailureListener {
            listener(null)
            Log.e(TAG, it.message!!)
        }.withTimeout {
            listener(null)
            Log.e(TAG, "Timeout getting driver")
        }
    }

    fun updateDevice(driverID: String, device: DeviceInterface?): Task<Void> {
        return Database.dbDrivers().child(driverID).child("device").setValue(device)
    }
}