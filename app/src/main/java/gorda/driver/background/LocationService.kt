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
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.location.LocationListener
import com.google.firebase.database.ChildEventListener
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ktx.getValue
import gorda.driver.R
import gorda.driver.activity.StartActivity
import gorda.driver.interfaces.LocInterface
import gorda.driver.location.LocationHandler
import gorda.driver.models.Driver
import gorda.driver.repositories.DriverRepository
import gorda.driver.repositories.ServiceRepository
import gorda.driver.ui.service.LocationBroadcastReceiver
import gorda.driver.ui.service.ServiceEventListener
import gorda.driver.ui.service.ServicesEventListener
import gorda.driver.utils.Constants
import gorda.driver.utils.Constants.Companion.LOCATION_EXTRA
import gorda.driver.utils.Utils
import java.util.Locale
import java.util.Timer
import java.util.TimerTask
import gorda.driver.models.Service as DBService

class LocationService : Service(), TextToSpeech.OnInitListener, LocationListener {

    companion object {
        const val SERVICE_ID = 100
        const val STOP_SERVICE_MSG = 1
    }

    private lateinit var lastLocation: Location
    private lateinit var messenger: Messenger
    private var stoped = false
    private var driverID: String = ""
    private var toSpeech: TextToSpeech? = null
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var playSound: PlaySound
    private lateinit var locationManager: LocationHandler
    private var haveToUpdate = 0
    private lateinit var listServices: MutableList<DBService>
    private val timer = Timer()
    private val listener: ServicesEventListener = ServicesEventListener { services ->
        listServices = services
    }
    private var nextService: gorda.driver.models.Service? = null
    private val nextServiceListener: ServiceEventListener = ServiceEventListener { service ->
        if (service == null) {
            if (nextService != null) {
                playSound.playCancelSound(nextService!!.created_at.toInt())
                nextService = null
            }
        } else {
            nextService = service
            playSound.playAssignedSound(nextService!!.created_at.toInt())
        }
    }
    private val currentServiceListener: ServiceEventListener = ServiceEventListener { service ->
        service?.let { s ->
            when (s.status) {
                DBService.STATUS_IN_PROGRESS -> {
                    val notifyId = sharedPreferences.getInt(
                        Constants.SERVICES_NOTIFICATION_ID,
                        0
                    )
                    if (notifyId != service.created_at.toInt())
                        playSound.playAssignedSound(service.created_at.toInt())
                }
                DBService.STATUS_CANCELED -> {
                    val cancelNotifyId = sharedPreferences.getInt(
                        Constants.CANCEL_SERVICES_NOTIFICATION_ID,
                        0
                    )
                    if (cancelNotifyId != service.created_at.toInt()) {
                        val chanel =
                            sharedPreferences.getString(Constants.NOTIFICATION_CANCELED, Constants.NOTIFICATION_VOICE)
                        if (chanel == Constants.NOTIFICATION_VOICE) speech(resources.getString(R.string.service_canceled))
                        else playSound.playCancelSound(service.created_at.toInt())

                        val editor: SharedPreferences.Editor = sharedPreferences.edit()
                        editor.putInt(Constants.CANCEL_SERVICES_NOTIFICATION_ID, service.created_at.toInt())
                        editor.apply()
                    }
                }
            }
        }
    }
    private val listenerNewServices: ChildEventListener = object : ChildEventListener {
        override fun onChildAdded(snapshot: DataSnapshot, previousChildName: String?) {
            if (snapshot.exists()) {
                val chanel =
                    sharedPreferences.getString(Constants.NOTIFICATIONS, Constants.NOTIFICATION_VOICE)
                snapshot.getValue<DBService>()?.let { service ->
                    if (chanel == Constants.NOTIFICATION_VOICE) speech(resources.getString(R.string.service_to) + service.start_loc.name)
                    else playSound.playNewService()
                }
            }
        }

        override fun onChildChanged(
            snapshot: DataSnapshot,
            previousChildName: String?
        ) {
        }

        override fun onChildRemoved(snapshot: DataSnapshot) {}

        override fun onChildMoved(snapshot: DataSnapshot, previousChildName: String?) {}

        override fun onCancelled(error: DatabaseError) {}
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            playSound = PlaySound(this, sharedPreferences)
            stoped = false
            intent.getStringExtra(Driver.DRIVER_KEY)?.let { id ->
                driverID = id
                ServiceRepository.isThereCurrentService(currentServiceListener)
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
        val connectedUri: Uri =
            Uri.parse(ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + R.raw.assigned_service)
        val notificationIntent = Intent(this, StartActivity::class.java)
            notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            notificationIntent.putExtra(Constants.DRIVER_ID_EXTRA, driverID)
            val pendingIntent: PendingIntent = PendingIntent.getActivity(
                    this, 0, notificationIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val builder: NotificationCompat.Builder = NotificationCompat.Builder(
            this,
            Constants.LOCATION_NOTIFICATION_CHANNEL_ID
        )
            .setOngoing(true)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(getString(R.string.status_connected))
            .setContentText(getString(R.string.text_connected))
            .setSound(connectedUri)
            .setContentIntent(pendingIntent)
        if (Utils.isNewerVersion(Build.VERSION_CODES.O)) {
            startForeground(SERVICE_ID, builder.build())
        }
        toSpeech = TextToSpeech(this, this)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@LocationService)
        mediaPlayer = MediaPlayer.create(this, R.raw.new_service)
        listServices = mutableListOf()
        locationManager = LocationHandler.getInstance(this)
        locationManager.addListener(this)
        startListenNewServices()
        startSyncServices()
        startTimer()
        startListenNextService()
    }

    fun stop() {
        locationManager.removeListener(this)
        ServiceRepository.stopListenNewServices(listenerNewServices)
        ServiceRepository.stopListenServices(listener)
        stoped = true
        timer.cancel()
        if (toSpeech != null) {
            toSpeech!!.stop()
            toSpeech!!.shutdown()
        }
        stopSelf()
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

    private fun speech(text: String) {
        val mute = sharedPreferences.getBoolean(Constants.NOTIFICATION_MUTE, false)
        if (!mute) toSpeech!!.speak(
            text.lowercase(),
            TextToSpeech.QUEUE_ADD,
            null,
            ""
        )
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val locSpanish = Locale("spa", "MEX")
            val result = toSpeech!!.setLanguage(locSpanish)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS", "The Language not supported!")
            }
        }
    }

    override fun onLocationChanged(location: Location) {
        if (!stoped) {
            haveToUpdate++
            lastLocation = location
            if (haveToUpdate == 10) {
                haveToUpdate = 0
                DriverRepository.updateLocation(driverID, object : LocInterface {
                    override var lat: Double = location.latitude
                    override var lng: Double = location.longitude
                })
            }

            val broadcast =
                Intent(LocationBroadcastReceiver.ACTION_LOCATION_UPDATES)
            broadcast.putExtra(LOCATION_EXTRA, lastLocation)
            LocalBroadcastManager.getInstance(applicationContext)
                .sendBroadcast(broadcast)
        }
    }

    private fun startTimer() {
        timer.scheduleAtFixedRate(object: TimerTask() {
            override fun run() {
                val currentTime = System.currentTimeMillis()

                val servicesAddedOutOf6Minutes = listServices.filter {
                    currentTime - (it.created_at * 1000) >= 360000
                }

                servicesAddedOutOf6Minutes.forEach { serv ->
                    val chanel =
                        sharedPreferences.getString(Constants.NOTIFICATIONS, Constants.NOTIFICATION_VOICE)
                    if (chanel == Constants.NOTIFICATION_VOICE) speech(resources.getString(R.string.service_to) + serv.start_loc.name)
                    else playSound.playNewService()
                }
            }
        }, 0, 120000)
    }

    private fun startSyncServices() {
        ServiceRepository.getPending(listener)
    }

    private fun startListenNewServices() {
        ServiceRepository.listenNewServices(listenerNewServices)
    }

    private fun startListenNextService() {
        ServiceRepository.isThereConnectionService(nextServiceListener)
    }
}