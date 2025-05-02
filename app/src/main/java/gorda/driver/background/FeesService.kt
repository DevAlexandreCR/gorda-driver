package gorda.driver.background

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import androidx.preference.PreferenceManager
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import gorda.driver.R
import gorda.driver.activity.StartActivity
import gorda.driver.location.LocationHandler
import gorda.driver.repositories.ServiceRepository
import gorda.driver.ui.service.ServiceEventListener
import gorda.driver.utils.Constants
import gorda.driver.utils.Utils

class FeesService: Service() {

    private val binder = ChronometerBinder()
    private var points = ArrayList<LatLng>()
    private var startTime: Long = 0
    private var name: String = ""
    private lateinit var locationManager: LocationHandler
    private var multiplier = 1.0
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var currentServiceListener: ServiceEventListener
    private var count = 0
    private lateinit var playSound: PlaySound
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            for (location in locationResult.locations) {
                LatLng(location.latitude, location.longitude).also {
                    points.add(it)
                    count++
                    if (count == 5) {
                        savePoints()
                        count = 0
                    }
                }
            }
        }
    }

    companion object {
        const val SERVICE_ID = 101
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        playSound = PlaySound(this, sharedPreferences)
        intent?.let {
            if (it.getBooleanExtra(Constants.RESTART_TRIP, false)) {
                restoreStartTime()
                restorePoints()
                restoreMultiplier()
            } else {
                sharedPreferences.edit(commit = true) { putLong(Constants.START_TIME, startTime) }
                sharedPreferences.edit(commit = true) { remove(Constants.MULTIPLIER) }
                sharedPreferences.edit(commit = true) { remove(Constants.POINTS) }
                it.getDoubleExtra(Constants.MULTIPLIER, multiplier).also { multi ->
                    multiplier = multi
                    sharedPreferences.edit(commit = true) {
                        putString(
                            Constants.MULTIPLIER,
                            multiplier.toString()
                        )
                    }
                }
            }
            it.getStringExtra(Constants.LOCATION_EXTRA)?.also { locationName ->
                name = locationName
            }
            locationManager = LocationHandler.getInstance(this)
            locationManager.addListener(locationCallback)
            createNotification()
            listenService()
        }

        return START_STICKY
    }

    private fun listenService() {
        currentServiceListener = ServiceEventListener { service ->
            if (service != null && !service.isInProgress()) {
                sharedPreferences.edit(commit = true) { remove(Constants.START_TIME) }
                sharedPreferences.edit(commit = true) { remove(Constants.MULTIPLIER) }
                sharedPreferences.edit(commit = true) { remove(Constants.POINTS) }
                locationManager.removeListener(locationCallback)
                stopSelf()
            }
        }
        ServiceRepository.isThereCurrentService(currentServiceListener)
    }

    override fun onCreate() {
        super.onCreate()
        startTime = SystemClock.elapsedRealtime()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this@FeesService)
    }

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    private fun createNotification() {
        val notificationIntent = Intent(this, StartActivity::class.java)
        notificationIntent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
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
            .setUsesChronometer(true)
            .setContentTitle(getString(R.string.service_running))
            .setContentText(getString(R.string.service_from, name))
            .setContentIntent(pendingIntent)
        builder.priority = NotificationManager.IMPORTANCE_HIGH

        if (Utils.isNewerVersion(Build.VERSION_CODES.Q)) {
            startForeground(SERVICE_ID, builder.build(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(SERVICE_ID, builder.build())
        }
    }

    private fun savePoints() {
        val gson = Gson()
        val json = gson.toJson(points)
        sharedPreferences.edit(commit = true) { putString(Constants.POINTS, json) }
    }

    fun getBaseTime(): Long {
        return startTime
    }

    private fun restoreStartTime() {
        startTime = sharedPreferences.getLong(Constants.START_TIME, SystemClock.elapsedRealtime())
    }

    fun getElapsedSeconds(): Long {
        return (SystemClock.elapsedRealtime() - startTime) / 1000
    }

    fun getPoints(): ArrayList<LatLng> {
        return points
    }

    private fun restorePoints() {
        val gson = Gson()
        val defaultJson = gson.toJson(points)
        val json = sharedPreferences.getString(Constants.POINTS, defaultJson)
        val type = object : TypeToken<ArrayList<LatLng>>() {}.type
        points = gson.fromJson(json, type)
    }

    fun getMultiplier(): Double {
        return multiplier
    }

    private fun restoreMultiplier() {
        multiplier = sharedPreferences.getString(Constants.MULTIPLIER, "1.0").toString().toDouble()
    }

    fun setMultiplier(multi: Double) {
        multiplier = multi
        sharedPreferences.edit(commit = true) { putString(Constants.MULTIPLIER, multiplier.toString()) }
    }

    inner class ChronometerBinder: Binder() {
        fun getService(): FeesService = this@FeesService
    }
}