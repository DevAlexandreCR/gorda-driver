package gorda.driver.background

import android.Manifest
import android.annotation.SuppressLint
import android.app.PendingIntent
import android.app.Service
import android.content.ContentResolver
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
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
import androidx.annotation.RequiresPermission
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.preference.PreferenceManager
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import gorda.driver.R
import gorda.driver.activity.StartActivity
import gorda.driver.location.CachedLocationStore
import gorda.driver.location.LocationHandler
import gorda.driver.models.Driver
import gorda.driver.repositories.ServiceObserverHandle
import gorda.driver.repositories.ServiceRepository
import gorda.driver.services.firebase.Database
import gorda.driver.ui.service.ConnectionBroadcastReceiver
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

class LocationService : Service(), TextToSpeech.OnInitListener {

    companion object {
        const val SERVICE_ID = 100
        const val STOP_SERVICE_MSG = 1
    }

    private lateinit var lastLocation: Location
    private lateinit var messenger: Messenger
    private var starting = false
    private var stopped = false
    private var driverID: String = ""
    private var toSpeech: TextToSpeech? = null
    private lateinit var mediaPlayer: MediaPlayer
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var playSound: PlaySound
    private lateinit var locationManager: LocationHandler
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var listServices: MutableList<DBService>
    private var pendingServicesObserverHandle: ServiceObserverHandle? = null
    private var currentServiceObserverHandle: ServiceObserverHandle? = null
    private var nextServiceObserverHandle: ServiceObserverHandle? = null
    private var firebaseConnectionListener: ValueEventListener? = null
    private var lastFirebaseConnected = false
    private val announcedPendingServiceKeys = linkedSetOf<String>()
    private var hasSeededPendingServices = false
    private val timer = Timer()
    private val listener: ServicesEventListener = ServicesEventListener { services ->
        listServices = services
        syncPendingServiceAlerts(services)
    }
    private var nextService: gorda.driver.models.Service? = null
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            for (location in locationResult.locations) {
                if (!stopped) {
                    publishObservedLocation(location)
                }
            }
        }
    }
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
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent != null) {
            stopped = false
            intent.getStringExtra(Driver.DRIVER_KEY)?.let { id ->
                driverID = id
                startCurrentServiceObservation()
                startListenNextService()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder {
        messenger = Messenger(IncomingHandler())
        replayCachedLocationForBind()
        return messenger.binder
    }

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION])
    @SuppressLint("UnspecifiedImmutableFlag")
    override fun onCreate() {
        super.onCreate()
        val connectedUri: Uri =
            (ContentResolver.SCHEME_ANDROID_RESOURCE + "://" + packageName + "/" + R.raw.assigned_service).toUri()
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
        if (Utils.isNewerVersion(Build.VERSION_CODES.Q)) {
            startForeground(SERVICE_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(SERVICE_ID, builder.build())
        }
        toSpeech = TextToSpeech(this, this)
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@LocationService)
        playSound = PlaySound(this, sharedPreferences)
        mediaPlayer = MediaPlayer.create(this, R.raw.new_service)
        listServices = mutableListOf()
        this.starting = true
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                publishObservedLocation(location)
            }
        }
        locationManager = LocationHandler.getInstance(this)
        locationManager.addListener(locationCallback)
        startPendingServicesObservation()
        startTimer()
        startListenNextService()
        startFirebaseConnectionObservation()
    }

    fun stop() {
        locationManager.removeListener(locationCallback)
        stopFirebaseConnectionObservation()
        pendingServicesObserverHandle?.dispose()
        pendingServicesObserverHandle = null
        currentServiceObserverHandle?.dispose()
        currentServiceObserverHandle = null
        nextServiceObserverHandle?.dispose()
        nextServiceObserverHandle = null
        announcedPendingServiceKeys.clear()
        hasSeededPendingServices = false
        stopped = true
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

    private fun startPendingServicesObservation() {
        pendingServicesObserverHandle?.dispose()
        pendingServicesObserverHandle = ServiceRepository.observePendingServices(listener)
    }

    private fun startCurrentServiceObservation() {
        if (driverID.isBlank()) {
            return
        }
        currentServiceObserverHandle?.dispose()
        currentServiceObserverHandle = ServiceRepository.observeCurrentService(currentServiceListener)
    }

    private fun startListenNextService() {
        nextServiceObserverHandle?.dispose()
        nextServiceObserverHandle = ServiceRepository.observeConnectionService(nextServiceListener)
    }

    private fun startFirebaseConnectionObservation() {
        if (firebaseConnectionListener != null) {
            return
        }

        firebaseConnectionListener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val isConnected = snapshot.getValue(Boolean::class.java) == true
                if (isConnected && !lastFirebaseConnected) {
                    Log.i(
                        this@LocationService.javaClass.toString(),
                        "event=firebase_socket_restored observersRetained=true"
                    )
                }
                lastFirebaseConnected = isConnected
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e(this@LocationService.javaClass.toString(), error.message)
            }
        }

        Database.dbInfoConnected().addValueEventListener(firebaseConnectionListener!!)
    }

    private fun stopFirebaseConnectionObservation() {
        firebaseConnectionListener?.let {
            Database.dbInfoConnected().removeEventListener(it)
        }
        firebaseConnectionListener = null
        lastFirebaseConnected = false
    }

    private fun syncPendingServiceAlerts(services: List<DBService>) {
        if (!hasSeededPendingServices) {
            services.forEach(::announcePendingService)
            announcedPendingServiceKeys.clear()
            services.mapTo(announcedPendingServiceKeys) { it.id }
            hasSeededPendingServices = true
            return
        }

        services.forEach { service ->
            if (announcedPendingServiceKeys.add(service.id)) {
                announcePendingService(service)
            }
        }
    }

    private fun announcePendingService(service: DBService) {
        val chanel =
            sharedPreferences.getString(Constants.NOTIFICATIONS, Constants.NOTIFICATION_VOICE)
        if (chanel == Constants.NOTIFICATION_VOICE) {
            speech(resources.getString(R.string.service_to) + service.start_loc.name)
        } else {
            playSound.playNewService()
        }
    }

    private fun replayCachedLocationForBind() {
        if (stopped || !::lastLocation.isInitialized) {
            return
        }

        Log.i(
            this.javaClass.toString(),
            "event=replay_cached_location_on_bind includeBootstrap=$starting"
        )
        publishLocationUpdate(lastLocation)
        if (starting) {
            starting = false
            publishInitialConnectionLocation(lastLocation)
        }
    }

    private fun publishObservedLocation(location: Location) {
        CachedLocationStore.save(sharedPreferences, location)
        lastLocation = location
        publishLocationUpdate(location)
        if (starting) {
            starting = false
            publishInitialConnectionLocation(location)
        }
    }

    private fun publishLocationUpdate(location: Location) {
        val broadcast = Intent(LocationBroadcastReceiver.ACTION_LOCATION_UPDATES)
        broadcast.putExtra(LOCATION_EXTRA, location)
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(broadcast)
    }

    private fun publishInitialConnectionLocation(location: Location) {
        val startingIntent = Intent(ConnectionBroadcastReceiver.ACTION_CONNECTION)
        startingIntent.putExtra(LOCATION_EXTRA, location)
        LocalBroadcastManager.getInstance(applicationContext)
            .sendBroadcast(startingIntent)
    }
}
