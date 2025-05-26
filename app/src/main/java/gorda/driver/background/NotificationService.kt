package gorda.driver.background

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.Intent
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import gorda.driver.activity.MainActivity
import gorda.driver.repositories.TokenRepository
import gorda.driver.services.firebase.Auth
import gorda.driver.utils.Constants
import gorda.driver.utils.Utils

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
            if (Utils.isAppInForeground(this)) {
                // Send broadcast to MainActivity to show alert
                val intent = Intent(Constants.ALERT_ACTION)
                intent.putExtra("title", it.title)
                intent.putExtra("body", it.body)
                sendBroadcast(intent)
            } else {
                showNotification(it.title, it.body)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun showNotification(title: String?, body: String?) {
        val channelId = Constants.MESSAGES_NOTIFICATION_CHANNEL_ID
        val notificationId = 1000

        // Main tap: open MainActivity with extras
        val mainIntent = Intent(this, MainActivity::class.java).apply {
            putExtra("title", title)
            putExtra("body", body)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val mainPendingIntent = PendingIntent.getActivity(
            this,
            0,
            mainIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title ?: "Notification")
            .setContentText(body ?: "")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(mainPendingIntent)
            .setAutoCancel(true)

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

