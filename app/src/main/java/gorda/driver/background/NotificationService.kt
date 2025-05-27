package gorda.driver.background

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.media.RingtoneManager
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.preference.PreferenceManager
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import gorda.driver.R
import gorda.driver.activity.StartActivity
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
        val type = remoteMessage.data["type"] ?: "notification"
        var duration = "15"
        var title = "Notification"
        var body = "New Notification"

        if (type === "notification") {
            remoteMessage.notification?.let {
                Log.d(TAG, "Message Notification Body: ${it.body}")
                title = it.title ?: title
                body = it.body ?: body
                duration = remoteMessage.data["duration"] ?: "15"
            }
        } else {
            duration = remoteMessage.data["duration"] ?: duration
            title = remoteMessage.data["title"] ?: title
            body = remoteMessage.data["body"] ?: body
        }

        saveNotificationToPrefs(title, body, duration)

        if (Utils.isAppInForeground(this)) {
            val intent = Intent(Constants.ALERT_ACTION)
            sendBroadcast(intent)
            playNotificationSound()
        } else {
            showNotification(title, body)
        }
    }

    private fun playNotificationSound() {
        try {
            val msgUri: Uri =
                (ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + R.raw.message).toUri()
            val r = RingtoneManager.getRingtone(applicationContext, msgUri)
            r.play()
        } catch (e: Exception) {
            e.printStackTrace()
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
        val notificationIntent = Intent(this, StartActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pendingIntent: PendingIntent = PendingIntent.getActivity(
            this, 0, notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val msgUri: Uri =
            (ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + R.raw.message).toUri()

        val builder = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title ?: "Notification")
            .setContentText(body ?: "")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setSound(msgUri)
            .setContentIntent(pendingIntent)
            .setAutoCancel(false)

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

