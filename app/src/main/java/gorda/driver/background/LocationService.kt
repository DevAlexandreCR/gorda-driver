package gorda.driver.background

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Intent
import android.location.Location
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import gorda.driver.R
import gorda.driver.activity.MainActivity
import gorda.driver.activity.StartActivity
import gorda.driver.interfaces.CustomLocationListener
import gorda.driver.interfaces.LocInterface
import gorda.driver.location.LocationHandler
import gorda.driver.models.Driver
import gorda.driver.repositories.DriverRepository
import gorda.driver.repositories.ServiceRepository
import gorda.driver.ui.service.LocationBroadcastReceiver
import gorda.driver.utils.Constants
import gorda.driver.utils.Constants.Companion.LOCATION_EXTRA
import gorda.driver.utils.Utils

class LocationService: Service(), MediaPlayer.OnPreparedListener  {

    companion object {
        const val SERVICE_ID = 100
        const val STOP_SERVICE_MSG = 1
    }

    lateinit var lastLocation: Location
    private lateinit var messenger: Messenger
    private var stoped = false
    private var driverID: String = ""

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            stoped = false
            intent.getStringExtra(Driver.DRIVER_KEY)?.let { id ->
                driverID = id
                LocationHandler.startListeningUserLocation(this, object : CustomLocationListener {
                    override fun onLocationChanged(location: Location?) {
                        if (location !== null && !stoped) {
                            lastLocation = location
                            DriverRepository.updateLocation(driverID, object: LocInterface {
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
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder {
        messenger = Messenger(IncomingHandler())
        return messenger.binder
    }

    override fun onCreate() {
        super.onCreate()
        val connectedUri: Uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"+ packageName + "/" + R.raw.assigned_service)
        val pendingIntent: PendingIntent =
            Intent(this, StartActivity::class.java).let { notificationIntent ->
                notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                notificationIntent.putExtra(Constants.DRIVER_ID_EXTRA, driverID)
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
            .setSound(connectedUri)
            .setContentIntent(pendingIntent)
        if (Utils.isNewerVersion()) {
            startForeground(SERVICE_ID, builder.build())
        }
        val player = MediaPlayer.create(this, R.raw.new_service)
        player.setOnPreparedListener(this@LocationService)
    }

    fun stop() {
        LocationHandler.stopLocationUpdates()
        ServiceRepository.stopListenNewServices()
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

    override fun onPrepared(p0: MediaPlayer?) {
        p0?.isLooping = false
        ServiceRepository.listenNewServices(object: ChildEventListener {
            override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                if (p0 != null) {
                    if (!p0.isPlaying) p0.start()
                }
            }

            override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onChildRemoved(snapshot: DataSnapshot) {}

            override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

            override fun onCancelled(error: DatabaseError) {}
        })
    }
}