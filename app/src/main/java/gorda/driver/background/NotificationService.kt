package gorda.driver.background

import android.annotation.SuppressLint
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import gorda.driver.repositories.TokenRepository
import gorda.driver.services.firebase.Auth
import gorda.driver.utils.Constants

class NotificationService: FirebaseMessagingService() {
    companion object {
        private const val TAG = "NotificationService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Message data payload: ${remoteMessage.data}")
        }
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            showNotification(it.title, it.body)
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String?, body: String?) {
        val channelId = Constants.MESSAGES_NOTIFICATION_CHANNEL_ID
        val notificationId = 1000

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title ?: "Notification")
            .setContentText(body ?: "")
            .setPriority(NotificationCompat.PRIORITY_HIGH)

        with(NotificationManagerCompat.from(this)) {
            notify(notificationId, builder.build())
        }
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

