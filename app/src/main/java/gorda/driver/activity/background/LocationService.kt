package gorda.driver.activity.background

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.*
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import gorda.driver.R
import gorda.driver.activity.ui.service.LocationBroadcastReceiver
import gorda.driver.interfaces.CustomLocationListener
import gorda.driver.interfaces.LocInterface
import gorda.driver.location.LocationHandler
import gorda.driver.models.Driver
import gorda.driver.repositories.DriverRepository
import gorda.driver.utils.Utils

class LocationService: Service() {

    companion object {
        const val STOP_SERVICE = "stop.locationService"
        const val SERVICE_ID = 100
        private const val NOTIFICATION_CHANNEL_ID = "location_service"
        const val STOP_SERVICE_MSG = 1
    }

    private var driverId: String? = null
    private var locationHandler: LocationHandler? = null
    lateinit var lastLocation: Location
    private lateinit var messenger: Messenger

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            val driverId = intent.getStringExtra(Driver.DRIVER_KEY)
            this.locationHandler = LocationHandler(this, object : CustomLocationListener {
                override fun onLocationChanged(location: Location?) {
                    if (location !== null && driverId != null) {
                        lastLocation = location
                        DriverRepository.updateLocation(driverId, object: LocInterface {
                            override var lat: Double = location.latitude
                            override var lng: Double = location.longitude
                        })
                        val broadcast = Intent(LocationBroadcastReceiver.ACTION_LOCATION_UPDATES)
                        broadcast.putExtra("location", lastLocation)
                        LocalBroadcastManager.getInstance(applicationContext).sendBroadcast(broadcast)
                    }
                }
            })
            locationHandler!!.startListeningUserLocation()
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder {
        Toast.makeText(applicationContext, "binding ok", Toast.LENGTH_SHORT).show()
        messenger = Messenger(IncomingHandler())
        return messenger.binder
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
            startForeground(SERVICE_ID, builder.build())
        }
    }

    fun stop() {
        println("stop service")
        locationHandler?.stopLocationUpdates()
        stopSelf()
    }

    fun sayHello() {
        val msg: Message = Message.obtain(null, STOP_SERVICE_MSG, 0, 0)
        messenger.send(msg)
    }

    @SuppressLint("HandlerLeak")
    inner class IncomingHandler() : Handler(Looper.getMainLooper()) {
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