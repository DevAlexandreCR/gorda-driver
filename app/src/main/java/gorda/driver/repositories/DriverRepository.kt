package gorda.driver.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.database.DatabaseReference.CompletionListener
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

    fun getDriver(driverId: String, listener: (driver: Driver) -> Unit): Unit {
        Database.dbDrivers().child(driverId).get()
            .addOnSuccessListener {
            it.getValue<Driver>()?.let { driver -> listener(driver)}
        }.addOnFailureListener{
                Log.e("firebase", "Error getting data", it)
            }
    }
}