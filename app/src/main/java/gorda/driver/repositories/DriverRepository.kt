package gorda.driver.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.BuildConfig
import gorda.driver.interfaces.DeviceInterface
import gorda.driver.interfaces.DriverConnected
import gorda.driver.interfaces.DriverInterface
import gorda.driver.interfaces.LocInterface
import gorda.driver.models.Driver
import gorda.driver.services.firebase.Database

object DriverRepository {

    val TAG = DriverRepository::class.java.toString()

    fun connect(driver: DriverInterface, location: LocInterface): Task<Void> {
        return Database.dbOnlineDrivers().child(driver.id!!).setValue(object : DriverConnected {
            override var id: String = driver.id!!
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
        Database.dbOnlineDrivers().child(driverId).addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listener(snapshot.hasChildren())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(this.javaClass.toString(), error.message)
            }
        })
    }

    fun getDriver(driverId: String, listener: (driver: Driver) -> Unit) {
        Database.dbDrivers().child(driverId).addListenerForSingleValueEvent(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                snapshot.getValue<Driver>()?.let {
                    listener(it)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(TAG, error.message)
            }
        })
    }

    fun updateDevice(driverID: String, device: DeviceInterface?): Task<Void> {
        return Database.dbDrivers().child(driverID).child("device").setValue(device)
    }
}