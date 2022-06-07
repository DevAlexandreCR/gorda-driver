package gorda.driver.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference.CompletionListener
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.interfaces.DriverInterface
import gorda.driver.models.Driver
import gorda.driver.services.firebase.Database

object DriverRepository {

    fun connect(driver: DriverInterface): Task<Void> {
        return Database.dbOnlineDrivers().child(driver.id!!).setValue(driver)
    }

    fun disconnect(driver: DriverInterface): Task<Void> {
        return Database.dbOnlineDrivers().child(driver.id!!).removeValue()
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
        Database.dbDrivers().child(driverId).get()
            .addOnSuccessListener { snapshot ->
                snapshot.getValue<Driver>()?.let { driver -> listener(driver)}
        }.addOnFailureListener{ e ->
                Log.e("firebase", "Error getting data: ${e.message}", e)
            }
    }
}