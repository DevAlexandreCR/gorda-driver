package gorda.driver.background

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import gorda.driver.repositories.TokenRepository
import gorda.driver.services.firebase.Auth
import gorda.driver.utils.Constants
import gorda.driver.utils.Utils
import org.json.JSONArray
import org.json.JSONObject

class NotificationService: FirebaseMessagingService() {
    companion object {
        private const val TAG = "NotificationService"
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        Log.d(TAG, "Message data payload: ${remoteMessage.data}")
        var duration = "15"
        if (remoteMessage.data.isNotEmpty()) {
            duration = remoteMessage.data["duration"] ?: duration
        }
        remoteMessage.notification?.let {
            Log.d(TAG, "Message Notification Body: ${it.body}")
            saveNotificationToPrefs(it.title.toString(), it.body.toString(), duration)
            if (Utils.isAppInForeground(this)) {
                val intent = Intent(Constants.ALERT_ACTION)
                sendBroadcast(intent)
            } else {
                showNotification(it.title, it.body)
            }
        }
    }

    private fun saveNotificationToPrefs(title: String, body: String, duration: String) {
        val prefs: SharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        val notificationsJson = prefs.getString(Constants.ALERT_ACTION, "[]")
        val notificationsArray = JSONArray(notificationsJson)
        val notificationObj = JSONObject().apply {
            put("title", title)
            put("body", body)
            put("timestamp", System.currentTimeMillis())
            put("duration", duration)
        }
        notificationsArray.put(notificationObj)
        prefs.edit(true) { putString(Constants.ALERT_ACTION, notificationsArray.toString()) }
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

