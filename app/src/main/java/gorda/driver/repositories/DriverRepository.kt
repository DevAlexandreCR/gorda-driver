package gorda.driver.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.interfaces.DriverInterface
import gorda.driver.interfaces.LocInterface
import gorda.driver.models.Driver
import gorda.driver.services.firebase.Database
import java.io.Serializable

object DriverRepository {

    val TAG = DriverRepository::class.java.toString()

    fun connect(driver: DriverInterface): Task<Void> {
        return Database.dbOnlineDrivers().child(driver.id!!).setValue(object : Serializable {
            val id = driver.id
        })
    }

    fun disconnect(driver: DriverInterface): Task<Void> {
        return Database.dbOnlineDrivers().child(driver.id!!).removeValue()
    }

    fun updateLocation(driverId: String, location: LocInterface): Unit {
        Database.dbOnlineDrivers().child(driverId).child("location").setValue(location)
    }

    fun isConnected(driverId: String, listener: (connected: Boolean) -> Unit): Unit{
        Database.dbOnlineDrivers().child(driverId).addListenerForSingleValueEvent(object: ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                listener(snapshot.hasChildren())
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(this.javaClass.toString(), error.message)
            }
        })
    }

    fun getDriver(driverId: String, listener: (driver: Driver) -> Unit): Unit {
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
}