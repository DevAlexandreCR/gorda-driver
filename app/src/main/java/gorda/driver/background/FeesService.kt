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
import android.os.Handler
import android.os.IBinder
import android.os.Looper
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
import gorda.driver.interfaces.RideFees
import gorda.driver.location.LocationHandler
import gorda.driver.maps.Map
import gorda.driver.utils.Constants

class FeesService: Service() {

    companion object {
        const val ORIGIN = "ORIGIN"
        const val CURRENT_FEES = "CURRENT_FEES"
        const val FEE_MULTIPLIER = "FEE_MULTIPLIER"
        const val RESUME_RIDE = "RESUME_RIDE"
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "FeesServiceChannel"
        private const val UPDATE_INTERVAL = 1000L // Update every second
    }

    private val binder = ChronometerBinder()
    private var points = ArrayList<LatLng>()
    private var startTime: Long = 0
    private var name: String = ""
    private var multiplier = 1.0
    private lateinit var sharedPreferences: SharedPreferences
    private var rideFees: RideFees = RideFees()
    private var totalDistance = 0.0
    private lateinit var locationHandler: LocationHandler

    // Add periodic update mechanism
    private val updateHandler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var feeUpdateCallback: ((Double, Double, Double, Double, Long) -> Unit)? = null

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
        locationHandler = LocationHandler.getInstance(this)
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
                points.clear()
                saveStartTime()
            }
            startLocationTracking()
        }

        startForeground()
        startPeriodicUpdates()
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
        val savedMultiplier = sharedPreferences.getString(Constants.MULTIPLIER, "1.0")?.toDoubleOrNull() ?: 1.0
        multiplier = if (savedMultiplier < 1.0) 1.0 else savedMultiplier

        val pointsJson = sharedPreferences.getString(Constants.POINTS, null)
        if (!pointsJson.isNullOrEmpty()) {
            val gson = Gson()
            val type = object : TypeToken<ArrayList<LatLng>>() {}.type
            points = gson.fromJson(pointsJson, type) ?: ArrayList()

            if (points.isNotEmpty()) {
                calculateTotalDistance()
                android.util.Log.d("FeesService", "Restored ride data: ${points.size} points, distance: $totalDistance km")
            }
        }
    }

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            super.onLocationResult(locationResult)
            locationResult.lastLocation?.let { location ->
                addLocationPoint(LatLng(location.latitude, location.longitude))
            }
        }
    }

    private fun startLocationTracking() {
        locationHandler.addListener(locationCallback)
    }

    private fun stopLocationTracking() {
        locationHandler.removeListener(locationCallback)
    }

    private fun addLocationPoint(latLng: LatLng) {
        points.add(latLng)
        savePoints()
        calculateTotalDistance()
    }

    private fun savePoints() {
        val gson = Gson()
        val pointsJson = gson.toJson(points)
        sharedPreferences.edit(commit = true) {
            putString(Constants.POINTS, pointsJson)
        }
    }

    fun getBaseTime(): Long = startTime

    fun setMultiplier(newMultiplier: Double) {
        this.multiplier = if (newMultiplier < 1.0) 1.0 else newMultiplier
        sharedPreferences.edit(commit = true) {
            putString(Constants.MULTIPLIER, multiplier.toString())
        }
    }

    fun getMultiplier(): Double = multiplier

    fun getTotalFee(): Double {
        val timeFee = getTimeFee()
        val distanceFee = getDistanceFee()
        val baseFee = rideFees.feesBase
        val total = (baseFee + timeFee + distanceFee + rideFees.priceAddFee) * multiplier
        val finalFee = maxOf(total, rideFees.priceMinFee)

        return finalFee
    }

    fun getTimeFee(): Double {
        val currentTime = SystemClock.elapsedRealtime()
        val elapsedMinutes = ((currentTime - startTime) / 1000.0 / 60.0)
        return elapsedMinutes * rideFees.priceMin
    }

    fun getDistanceFee(): Double {
        return (totalDistance / 1000) * rideFees.priceKm
    }

    fun getTotalDistance(): Double = totalDistance

    private fun calculateTotalDistance() {
        var distance = 0.0
        if (points.size >= 2) {
            for (i in 1 until points.size) {
                distance += Map.calculateDistance(points[i-1], points[i])
            }
        }
        totalDistance = distance
    }

    fun getPoints(): ArrayList<LatLng> = points

    override fun onDestroy() {
        stopLocationTracking()
        stopPeriodicUpdates()
        super.onDestroy()
    }

    fun getElapsedSeconds(): Long {
        return (SystemClock.elapsedRealtime() - startTime) / 1000
    }

    private fun startPeriodicUpdates() {
        updateRunnable = object : Runnable {
            override fun run() {
                // Calculate and update fees periodically
                val totalFee = getTotalFee()
                val timeFee = getTimeFee()
                val distanceFee = getDistanceFee()
                val currentTotalDistance = getTotalDistance()
                val elapsedSeconds = getElapsedSeconds()

                // Invoke the callback if set - pass totalDistance instead of baseFee
                feeUpdateCallback?.invoke(totalFee, timeFee, distanceFee, currentTotalDistance, elapsedSeconds)

                // Schedule the next update
                updateHandler.postDelayed(this, UPDATE_INTERVAL)
            }
        }

        updateHandler.post(updateRunnable!!)
    }

    private fun stopPeriodicUpdates() {
        updateRunnable?.let {
            updateHandler.removeCallbacks(it)
        }
    }

    fun setFeeUpdateCallback(callback: (Double, Double, Double, Double, Long) -> Unit) {
        this.feeUpdateCallback = callback
    }
}