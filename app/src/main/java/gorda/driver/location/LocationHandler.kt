package gorda.driver.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Build
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import gorda.driver.utils.Utils

class LocationHandler private constructor(context: Context) {
    private var fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationRequest = LocationRequest.Builder(
        Priority.PRIORITY_BALANCED_POWER_ACCURACY,
        LOCATION_REFRESH_TIME
    )
        .build()

    private val listeners = mutableListOf<LocationListener>()
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
        private const val LOCATION_REFRESH_TIME: Long = 1000

        fun getInstance(context: Context): LocationHandler {
            return INSTANCE ?: synchronized(this) {
                val instance = LocationHandler(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        fun checkPermissions(context: Context): Boolean {
            return if (Utils.isNewerVersion(Build.VERSION_CODES.TIRAMISU)) {
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

    fun addListener(listener: LocationListener) {
        listeners.add(listener)
        if (listeners.size == 1) {
            startLocationUpdates()
        }
    }

    fun removeListener(listener: LocationListener) {
        listeners.remove(listener)
        if (listeners.isEmpty()) {
            stopLocationUpdates()
        }
    }

    private fun startLocationUpdates() {
        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, null)
        } catch (e: SecurityException) {
            e.printStackTrace()
        }
    }


    private fun stopLocationUpdates() {
        locationCallback.let { fusedLocationClient.removeLocationUpdates(it) }
    }

    private fun notifyListeners(location: Location) {
        for (listener in listeners) {
            listener.onLocationChanged(location)
        }
    }
}