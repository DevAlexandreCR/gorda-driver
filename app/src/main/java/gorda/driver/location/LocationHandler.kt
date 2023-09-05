package gorda.driver.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority

class LocationHandler private constructor(context: Context) {
    private var fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)
    private val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            LOCATION_REFRESH_TIME,
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

    init {
        startLocationUpdates()
    }

    companion object {
        @Volatile
        private var INSTANCE: LocationHandler? = null
        const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
        private const val LOCATION_REFRESH_TIME: Long = 10000

        fun getInstance(context: Context): LocationHandler {
            return INSTANCE ?: synchronized(this) {
                val instance = LocationHandler(context.applicationContext)
                INSTANCE = instance
                instance
            }
        }

        fun checkPermissions(context: Context): Boolean {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
                == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                return true
            }

            return false
        }
    }

    fun addListener(listener: LocationListener) {
        listeners.add(listener)
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