package gorda.driver.services.firebase

import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase

object Database {
    private val reference: DatabaseReference = FirebaseInitializeApp.database.getReference("/")

    fun dbServices(): DatabaseReference {
        return reference.child("services").ref
    }

    fun dbDrivers(): DatabaseReference {
        return reference.child("drivers").ref
    }

    fun dbOnlineDrivers(): DatabaseReference {
        return reference.child("online_drivers").ref
    }
}