package gorda.go.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.database.ktx.getValue
import gorda.go.BuildConfig
import gorda.go.interfaces.DeviceInterface
import gorda.go.interfaces.DriverConnected
import gorda.go.interfaces.DriverInterface
import gorda.go.interfaces.LocInterface
import gorda.go.models.Driver
import gorda.go.services.firebase.Database

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
        }
    }

    fun getDriver(driverId: String, listener: (driver: Driver?) -> Unit) {
        Database.dbDrivers().child(driverId).get().addOnSuccessListener { snapshot ->
            listener(snapshot.getValue<Driver?>())
        }.addOnFailureListener {
            listener(null)
            Log.e(TAG, it.message!!)
        }
    }

    fun updateDevice(driverID: String, device: DeviceInterface?): Task<Void> {
        return Database.dbDrivers().child(driverID).child("device").setValue(device)
    }
}