package gorda.driver.services.firebase

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import gorda.driver.repositories.TokenRepository

object Messaging {
    private const val TAG = "Messaging"
    private data class PendingTokenRegistration(
        val token: String,
        val source: String,
        val expectedDriverId: String?
    )

    private var authReadyListener: FirebaseAuth.AuthStateListener? = null
    private var pendingTokenRegistration: PendingTokenRegistration? = null

    fun init(driverId: String): Unit {
        getToken(driverId, source = "bootstrap")
        unsubscribeFromLegacyTopic()
    }

    fun registerToken(token: String, source: String, expectedDriverId: String? = null) {
        registerTokenForCurrentSession(token, source, expectedDriverId)
    }

    private fun getToken(driverId: String, source: String): Unit {
        Log.d(TAG, "Getting token for driver ID: $driverId source=$source")
        FirebaseInitializeApp.messaging.token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                registerTokenForCurrentSession(task.result, source, driverId)
            } else {
                Log.d(TAG, "Failed to get token for driver ID: $driverId source=$source", task.exception)
            }
        }.addOnFailureListener { exception ->
            Log.d(TAG, "Error getting token for driver ID: $driverId source=$source", exception)
        }
    }

    private fun registerTokenForCurrentSession(
        token: String,
        source: String,
        expectedDriverId: String?
    ) {
        val currentDriverId = Auth.getCurrentUserUUID()
        if (currentDriverId == null) {
            pendingTokenRegistration = PendingTokenRegistration(token, source, expectedDriverId)
            Log.d(
                TAG,
                "Deferring token registration source=$source expectedDriverId=${expectedDriverId ?: "n/a"} reason=auth_not_ready"
            )
            ensureAuthReadyListener()
            return
        }

        clearAuthReadyListener()
        pendingTokenRegistration = null
        TokenRepository.setCurrentToken(currentDriverId, token).addOnCompleteListener { tokenTask ->
            if (tokenTask.isSuccessful) {
                Log.d(TAG, "Token updated for driver ID: $currentDriverId source=$source")
            } else {
                Log.d(
                    TAG,
                    "Failed to update token for driver ID: $currentDriverId source=$source",
                    tokenTask.exception
                )
            }
        }
    }

    private fun ensureAuthReadyListener() {
        if (authReadyListener != null) {
            return
        }

        authReadyListener = Auth.onAuthChanges { uuid ->
            if (uuid == null) {
                return@onAuthChanges
            }

            val pendingRegistration = pendingTokenRegistration ?: return@onAuthChanges
            clearAuthReadyListener()
            pendingTokenRegistration = null
            Log.d(
                TAG,
                "Resuming deferred token registration source=${pendingRegistration.source} driverId=$uuid"
            )
            registerTokenForCurrentSession(
                token = pendingRegistration.token,
                source = pendingRegistration.source,
                expectedDriverId = pendingRegistration.expectedDriverId
            )
        }
    }

    private fun clearAuthReadyListener() {
        authReadyListener?.let(Auth::removeAuthChanges)
        authReadyListener = null
    }

    private fun unsubscribeFromLegacyTopic() {
        FirebaseInitializeApp.messaging.unsubscribeFromTopic("drivers")
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    Log.d(TAG, "Unsubscribed from legacy drivers topic")
                } else {
                    Log.d(TAG, "Failed to unsubscribe from legacy drivers topic", task.exception)
                }
            }
    }
}
