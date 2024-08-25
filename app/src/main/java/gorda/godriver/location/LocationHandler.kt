package gorda.godriver.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import gorda.godriver.utils.Utils

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
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult) {
            val location = locationResult.lastLocation
            location?.let {
                notifyListeners(it)
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