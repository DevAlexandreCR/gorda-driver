package gorda.driver.background

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
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
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import gorda.driver.R
import gorda.driver.activity.StartActivity
import gorda.driver.interfaces.RideFees
import gorda.driver.utils.Constants
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class FeesService: Service() {

    companion object {
        const val ORIGIN = "ORIGIN"
        const val CURRENT_FEES = "CURRENT_FEES"
        const val FEE_MULTIPLIER = "FEE_MULTIPLIER"
        const val RESUME_RIDE = "RESUME_RIDE"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "FeesServiceChannel"
    }

    private val binder = ChronometerBinder()
    private var points = ArrayList<LatLng>()
    private var startTime: Long = 0
    private var name: String = ""
    private var multiplier = 1.0
    private lateinit var sharedPreferences: SharedPreferences
    private var rideFees: RideFees = RideFees()
    private var totalDistance = 0.0

    inner class ChronometerBinder : Binder() {
        fun getService(): FeesService = this@FeesService
    }

    override fun onBind(intent: Intent): IBinder {
        return binder
    }

    override fun onCreate() {
        super.onCreate()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
        rideFees = RideFees()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            name = it.getStringExtra(ORIGIN) ?: ""
            multiplier = it.getDoubleExtra(FEE_MULTIPLIER, 1.0)
            val resumeRide = it.getBooleanExtra(RESUME_RIDE, false)

            val feesJson = sharedPreferences.getString(CURRENT_FEES, null)
            if (!feesJson.isNullOrEmpty()) {
                try {
                    val gson = Gson()
                    rideFees = gson.fromJson(feesJson, RideFees::class.java) ?: RideFees()
                } catch (e: Exception) {
                    rideFees = RideFees()
                }
            }

            if (resumeRide) {
                restoreRideData()
            } else {
                startTime = SystemClock.elapsedRealtime()
                saveStartTime()
            }
        }

        startForeground()
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_ID,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = CHANNEL_ID
                setShowBadge(false)
            }

            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForeground() {
        val notificationIntent = Intent(this, StartActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_running))
            .setContentText(getString(R.string.service_from, name))
            .setSmallIcon(R.drawable.ic_baseline_info_24)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun saveStartTime() {
        sharedPreferences.edit(commit = true) {
            putLong(Constants.START_TIME, startTime)
        }
    }

    private fun restoreRideData() {
        startTime = sharedPreferences.getLong(Constants.START_TIME, SystemClock.elapsedRealtime())
        multiplier = sharedPreferences.getString(Constants.MULTIPLIER, "1.0")?.toDoubleOrNull() ?: 1.0

        val pointsJson = sharedPreferences.getString(Constants.POINTS, null)
        if (!pointsJson.isNullOrEmpty()) {
            val gson = Gson()
            val type = object : TypeToken<ArrayList<LatLng>>() {}.type
            points = gson.fromJson(pointsJson, type) ?: ArrayList()
        }
    }

    fun getBaseTime(): Long = startTime

    fun setMultiplier(newMultiplier: Double) {
        this.multiplier = newMultiplier
        sharedPreferences.edit(commit = true) {
            putString(Constants.MULTIPLIER, newMultiplier.toString())
        }
    }

    fun getMultiplier(): Double = multiplier

    fun getTotalFee(): Double {
        val timeFee = getTimeFee()
        val distanceFee = getDistanceFee()
        val baseFee = rideFees.feesBase
        val total = (baseFee + timeFee + distanceFee + rideFees.priceAddFee) * multiplier
        return maxOf(total, rideFees.priceMinFee)
    }

    fun getTimeFee(): Double {
        val currentTime = SystemClock.elapsedRealtime()
        val elapsedMinutes = ((currentTime - startTime) / 1000.0 / 60.0)
        return elapsedMinutes * rideFees.priceMin
    }

    fun getDistanceFee(): Double {
        calculateTotalDistance()
        return totalDistance * rideFees.priceKm
    }

    fun getTotalDistance(): Double {
        calculateTotalDistance()
        return totalDistance
    }

    private fun calculateTotalDistance() {
        var distance = 0.0
        for (i in 1 until points.size) {
            distance += calculateDistance(points[i-1], points[i])
        }
        totalDistance = distance
    }

    private fun calculateDistance(start: LatLng, end: LatLng): Double {
        val earthRadius = 6371.0 // km
        val dLat = Math.toRadians(end.latitude - start.latitude)
        val dLon = Math.toRadians(end.longitude - start.longitude)
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(start.latitude)) * cos(Math.toRadians(end.latitude)) *
                sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    fun getPoints(): ArrayList<LatLng> = points

    override fun onDestroy() {
        super.onDestroy()
    }
}