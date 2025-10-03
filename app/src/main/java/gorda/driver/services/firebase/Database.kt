package gorda.driver.services.firebase

import com.google.firebase.database.DatabaseReference

object Database {
    private val reference: DatabaseReference = FirebaseInitializeApp.database.getReference("/")

    fun dbServices(): DatabaseReference {
        val ref =  reference.child("services").ref
        ref.keepSynced(true)
        return ref
    }

    fun dbDriversAssigned(): DatabaseReference {
        val ref =  reference.child("drivers_assigned").ref
        ref.keepSynced(true)
        return ref
    }

    fun dbServiceConnections(): DatabaseReference {
        val ref = reference.child("service_connections").ref
        ref.keepSynced(true)
        return ref
    }

    fun dbDrivers(): DatabaseReference {
        return reference.child("drivers").ref
    }

    fun dbTokens(): DatabaseReference {
        return reference.child("tokens").ref
    }

    fun dbOnlineDrivers(): DatabaseReference {
        return reference.child("online_drivers").ref
    }

    fun dbRideFees(): DatabaseReference {
        return reference.child("settings").child("ride_fees").ref
    }
}