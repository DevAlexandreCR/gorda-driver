package gorda.driver.background

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.location.Location
import android.media.MediaPlayer
import android.net.Uri
import android.os.*
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ktx.getValue
import gorda.driver.R
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
import java.util.*

class LocationService: Service(), MediaPlayer.OnPreparedListener, TextToSpeech.OnInitListener  {

    companion object {
        const val SERVICE_ID = 100
        const val STOP_SERVICE_MSG = 1
    }

    lateinit var lastLocation: Location
    private lateinit var messenger: Messenger
    private var stoped = false
    private var driverID: String = ""
    private var toSpeech: TextToSpeech? = null
    private var mediaPlayer: MediaPlayer? = null
    private var sharedPreferences: SharedPreferences? = null

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
                ServiceRepository.listenNewServices(object: ChildEventListener {
                    override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
                        if (snapshot.exists()) {
                            snapshot.getValue<gorda.driver.models.Service>()?.let { service ->
                                val chanel = sharedPreferences?.getString(Constants.NOTIFICATIONS, Constants.NOTIFICATION_VOICE)
                                if (chanel == Constants.NOTIFICATION_VOICE) speech(service.start_loc.name)
                                else play()
                            }
                        }
                    }

                    override fun onChildChanged(snapshot: DataSnapshot, previousChildName: String?) {}

                    override fun onChildRemoved(snapshot: DataSnapshot) {}

                    override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

                    override fun onCancelled(error: DatabaseError) {}
                })
            }
        }
        return START_STICKY
    }
    override fun onBind(intent: Intent?): IBinder {
        messenger = Messenger(IncomingHandler())
        return messenger.binder
    }

    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onCreate() {
        super.onCreate()
        val connectedUri: Uri = Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://"+ packageName + "/" + R.raw.assigned_service)
        val pendingIntent: PendingIntent =
            Intent(this, StartActivity::class.java).let { notificationIntent ->
                notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP
                notificationIntent.putExtra(Constants.DRIVER_ID_EXTRA, driverID)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    PendingIntent.getActivity(this, 0, notificationIntent,
                        PendingIntent.FLAG_IMMUTABLE)
                } else {
                    PendingIntent.getActivity(this, 0, notificationIntent,
                        Intent.FILL_IN_ACTION)
                }
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

        toSpeech = TextToSpeech(this, this)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@LocationService)
    }

    fun stop() {
        LocationHandler.stopLocationUpdates()
        ServiceRepository.stopListenNewServices()
        stoped = true
        stopSelf()
        if (toSpeech != null) {
            toSpeech!!.stop()
            toSpeech!!.shutdown()
        }
    }

    override fun onDestroy() {
        stop()
        super.onDestroy()
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

    override fun onPrepared(mediaPlayer: MediaPlayer?) {
        mediaPlayer?.isLooping = false
        this.mediaPlayer = mediaPlayer
    }

    private fun play() {
        if (mediaPlayer != null && !mediaPlayer!!.isPlaying) mediaPlayer!!.start()
    }

    private fun speech(text: String) {
        if (!toSpeech!!.isSpeaking) toSpeech!!.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            null,
            ""
        )
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locSpanish = Locale("spa", "MEX")
            val result = toSpeech!!.setLanguage(locSpanish)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS","The Language not supported!")
            }
        }
    }
}