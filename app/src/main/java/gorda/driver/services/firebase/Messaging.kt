package gorda.driver.services.firebase

import android.util.Log
import gorda.driver.repositories.TokenRepository

object Messaging {

    fun init(driverId: String): Unit {
        getToken(driverId)
        subscribeToTopic()
    }

    private fun getToken(driverId: String): Unit {
        Log.d("Messaging", "Getting token for driver ID: $driverId")
        FirebaseInitializeApp.messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                TokenRepository.setCurrentToken(driverId, token).addOnCompleteListener { tokenTask ->
                    if (tokenTask.isSuccessful) {
                        Log.d("Messaging", "Token updated for driver ID: $driverId")
                    } else {
                        Log.d("Messaging", "Failed to update token for driver ID: $driverId", tokenTask.exception)
                    }
                }
            } else {
                Log.d("Messaging", "Failed to get token for driver ID: $driverId", task.exception)
            }
        }.addOnFailureListener { exception ->
            Log.d("Messaging", "Error getting token for driver ID: $driverId", exception)
        }
    }

    private fun subscribeToTopic() {
        FirebaseInitializeApp.messaging.subscribeToTopic("drivers")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d("Messaging", "Subscribed to topic for drivers")
                } else {
                    Log.d("Messaging", "Failed to subscribe to topic for drivers", task.exception)
                }
            }
    }
}