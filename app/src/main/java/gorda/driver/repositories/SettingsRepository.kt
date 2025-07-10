package gorda.driver.repositories

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import gorda.driver.interfaces.RideFees
import gorda.driver.serializers.RideFeesDeserializer
import gorda.driver.services.firebase.Database

object SettingsRepository {
    fun getRideFees(callback: (fees: RideFees) -> Unit): Unit {
        Database.dbRideFees().addValueEventListener(
            object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    if (snapshot.exists()) {
                        val fees = RideFeesDeserializer.getRideFees(snapshot)
                        callback(fees)
                        Log.d('f'.toString(), "Ride fees fetched successfully: $fees")
                    }
                }
                override fun onCancelled(error: DatabaseError) {
                    // Handle error if needed
                }
            }
        )
    }
}
