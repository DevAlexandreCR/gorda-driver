package gorda.driver.repositories

import android.util.Log
import com.google.firebase.database.DatabaseReference.CompletionListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.interfaces.DriverInterface
import gorda.driver.models.Driver
import gorda.driver.services.firebase.Database

object DriverRepository {

    fun connect(driver: DriverInterface): Unit {
        val ref = Database.dbOnlineDrivers().push()
        ref.key?.let { ref.child(it).setValue(driver) }
    }

    fun disconnect(driverId: String, listener: CompletionListener): Unit {
        Database.dbOnlineDrivers().child(driverId).removeValue(listener)
    }

    fun getDriver(driverId: String, listener: (driver: Driver) -> Unit): Unit {
        Database.dbDrivers().child(driverId).get()
            .addOnSuccessListener {
            it.getValue<Driver>()?.let { driver -> listener(driver)}
        }.addOnFailureListener{
                Log.e("firebase", "Error getting data", it)
            }
    }
}