package gorda.driver.activity.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.IBinder
import androidx.core.app.NotificationCompat
import gorda.driver.R
import gorda.driver.interfaces.CustomLocationListener
import gorda.driver.location.LocationHandler
import gorda.driver.utils.Utils

class LocationService: Service() {

    companion object {
        const val STOP_SERVICE = "stop.locationService"
        const val START_SERVICE = "start.locationService"
        private const val NOTIFICATION_CHANNEL_ID = "location_service"
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            if (intent.action === START_SERVICE) {
                LocationHandler(this, object : CustomLocationListener {
                    override fun onLocationChanged(location: Location?) {
                        println("location ::: ${location.toString()}")
                    }
                }).startListeningUserLocation()
            } else {
                stopSelf()
            }
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onCreate() {
        super.onCreate()
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this,
            NOTIFICATION_CHANNEL_ID
        )
            .setOngoing(false)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
        if (Utils.isNewerVersion()) {
            val notificationManager: NotificationManager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
            val notificationChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW
            )

            notificationChannel.description = NOTIFICATION_CHANNEL_ID
            notificationChannel.setSound(null, null)
            notificationManager.createNotificationChannel(notificationChannel)
            startForeground(1, builder.build())
        }
    }

}