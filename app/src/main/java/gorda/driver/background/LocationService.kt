package gorda.driver.background

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import gorda.driver.R
import gorda.driver.activity.MainActivity
import gorda.driver.interfaces.CustomLocationListener
import gorda.driver.interfaces.LocInterface
import gorda.driver.location.LocationHandler
import gorda.driver.models.Driver
import gorda.driver.repositories.DriverRepository
import gorda.driver.ui.service.LocationBroadcastReceiver
import gorda.driver.utils.Constants
import gorda.driver.utils.Constants.Companion.LOCATION_EXTRA
import gorda.driver.utils.Utils

class LocationService: Service() {

    companion object {
        const val SERVICE_ID = 100
        const val STOP_SERVICE_MSG = 1
    }

    lateinit var lastLocation: Location
    private lateinit var messenger: Messenger
    private var stoped = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            stoped = false
            val driverId = intent.getStringExtra(Driver.DRIVER_KEY)
            LocationHandler.startListeningUserLocation(this, object : CustomLocationListener {
                override fun onLocationChanged(location: Location?) {
                    if (location !== null && driverId != null && !stoped) {
                        lastLocation = location
                        DriverRepository.updateLocation(driverId, object: LocInterface {
                            override var lat: Double = location.latitude
                            override var lng: Double = location.longitude
                        })
                        val broadcast = Intent(LocationBroadcastReceiver.ACTION_LOCATION_UPDATES)
                        broadcast.putExtra(LOCATION_EXTRA, lastLocation)
                        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(broadcast)
                    }
                }
            })
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder {
        messenger = Messenger(IncomingHandler())
        return messenger.binder
    }

    override fun onCreate() {
        super.onCreate()
        val pendingIntent: PendingIntent =
            Intent(this, MainActivity::class.java).let { notificationIntent ->
                notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                PendingIntent.getActivity(this, 0, notificationIntent,
                    PendingIntent.FLAG_IMMUTABLE)
            }
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(this,
            Constants.LOCATION_NOTIFICATION_CHANNEL_ID
        )
            .setOngoing(false)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.status_connected))
            .setContentText(getString(R.string.text_connected))
            .setContentIntent(pendingIntent)
        if (Utils.isNewerVersion()) {
            startForeground(SERVICE_ID, builder.build())
        }
    }

    fun stop() {
        LocationHandler.stopLocationUpdates()
        stoped = true
        stopSelf()
    }

    @SuppressLint("HandlerLeak")
    inner class IncomingHandler : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            when (msg.what) {
                STOP_SERVICE_MSG -> {
                    stop()
                }
                else -> super.handleMessage(msg)
            }
        }
    }
}