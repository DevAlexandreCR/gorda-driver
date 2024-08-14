package gorda.go.background

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import gorda.go.repositories.TokenRepository
import gorda.go.services.firebase.Auth

class NotificationService: FirebaseMessagingService() {
    companion object {
        private const val TAG = "NotificationService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        // TODO(developer): Handle FCM messages here.
        // Not getting messages here? See why this may be: https://goo.gl/39bRNJ
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Check if message contains a data payload.
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
        }

        // Check if message contains a notification payload.
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
        }

        // Also if you intend on generating your own notifications as a result of a received FCM
        // message, here is where that should be initiated. See sendNotification method below.
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Auth.getCurrentUserUUID().let { uid ->
            if (uid is String) TokenRepository.setCurrentToken(uid, token).addOnCompleteListener {
                if (it.isSuccessful) {
                    Log.d(TAG, "token updated user id $uid")
                }
            }
        }
    }
}