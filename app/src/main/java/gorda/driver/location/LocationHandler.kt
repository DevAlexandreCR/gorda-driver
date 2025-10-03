package gorda.driver.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import gorda.driver.utils.Utils
import kotlin.math.abs

class LocationHandler private constructor(context: Context) {
    private var fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_HIGH_ACCURACY,
        LOCATION_REFRESH_TIME
    )
        .setMinUpdateIntervalMillis(LOCATION_FASTEST_REFRESH_TIME)
        .setMinUpdateDistanceMeters(LOCATION_MIN_METERS)
        .build()

    private val listeners = mutableListOf<LocationCallback>()

    private var lastValidLocation: Location? = null
    private var lastLocationTime: Long = 0
    private var consecutiveInvalidLocations = 0

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            location?.let {
                if (isValidLocation(it)) {
                    lastValidLocation = it
                    lastLocationTime = System.currentTimeMillis()
                    consecutiveInvalidLocations = 0
                    notifyListeners(it)
                } else {
                    consecutiveInvalidLocations++
                    if (consecutiveInvalidLocations >= MAX_CONSECUTIVE_INVALID_LOCATIONS) {
                        lastValidLocation = it
                        lastLocationTime = System.currentTimeMillis()
                        consecutiveInvalidLocations = 0
                        notifyListeners(it)
                    }
                }
            }
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: LocationHandler? = null
        const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
        private const val LOCATION_REFRESH_TIME: Long = 2000
        private const val LOCATION_FASTEST_REFRESH_TIME: Long = 1000
        private const val LOCATION_MIN_METERS: Float = 2.0f
        private const val MAX_SPEED_KMH = 200.0
        private const val MAX_ACCURACY_METERS = 100.0
        private const val MIN_TIME_BETWEEN_UPDATES = 500L
        private const val MAX_DISTANCE_JUMP_METERS = 500.0
        private const val MAX_CONSECUTIVE_INVALID_LOCATIONS = 5

        fun getInstance(context: Context): LocationHandler {
            return INSTANCE ?: synchronized(this) {
                val instance = LocationHandler(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        fun checkPermissions(context: Context): Boolean {
            return if (Utils.isNewerVersion(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)) {
                (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.POST_NOTIFICATIONS
                        ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.FOREGROUND_SERVICE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                    )
            } else if (Utils.isNewerVersion(Build.VERSION_CODES.TIRAMISU)) {
                (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED &&
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.POST_NOTIFICATIONS
                    ) == PackageManager.PERMISSION_GRANTED
                )
            } else {
                (
                    ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.ACCESS_FINE_LOCATION
                    ) == PackageManager.PERMISSION_GRANTED
                )
            }
        }
    }

    private fun isValidLocation(location: Location): Boolean {
        val currentTime = System.currentTimeMillis()

        if (!location.hasAccuracy() || location.accuracy > MAX_ACCURACY_METERS) {
            return false
        }

        val lastLocation = lastValidLocation
        if (lastLocation == null) {
            return true
        }

        val timeDiff = currentTime - lastLocationTime
        if (timeDiff < MIN_TIME_BETWEEN_UPDATES) {
            return false
        }

        val distance = lastLocation.distanceTo(location)

        if (distance > MAX_DISTANCE_JUMP_METERS) {
            return false
        }

        val speedMPS = distance / (timeDiff / 1000.0)
        val speedKMH = speedMPS * 3.6

        if (speedKMH > MAX_SPEED_KMH) {
            return false
        }

        if (location.hasAltitude() && lastLocation.hasAltitude()) {
            val altitudeDiff = abs(location.altitude - lastLocation.altitude)
            if (altitudeDiff > 200.0) {
                return false
            }
        }

        return true
    }

    fun addListener(listener: LocationCallback) {
        listeners.add(listener)
        if (listeners.size == 1) {
            startLocationUpdates()
        }
    }

    fun removeListener(listener: LocationCallback) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            stopLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }


    private fun stopLocationUpdates() {
        locationCallback.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    private fun notifyListeners(location: Location) {
        listeners.forEach {
            it.onLocationResult(LocationResult.create(listOf(location)))
        }
    }
}