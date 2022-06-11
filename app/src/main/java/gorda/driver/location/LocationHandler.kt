package gorda.driver.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import gorda.driver.interfaces.CustomLocationListener

class LocationHandler(context: Context, listener: CustomLocationListener) {
    private var context: Context
    private val fusedLocationClient: FusedLocationProviderClient
    private val locationManager: LocationManager
    private val locationRequest: LocationRequest
    private val locationCallback: LocationCallback
    private val listener: CustomLocationListener

    companion object {
        const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
        private const val TAG = "LocationHandler"
        private const val LOCATION_REFRESH_TIME = 5000

        fun checkPermissions(context: Context): Boolean {
            if (ActivityCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(context,
                    Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                return true
            }

            return false
        }
    }

    init {
        this.context = context
        this.listener = listener
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this.context)
        this.locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        this.locationRequest = LocationRequest.create().apply {
            interval = LOCATION_REFRESH_TIME.toLong() * 2
            fastestInterval = LOCATION_REFRESH_TIME.toLong()
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
        this.locationCallback = object: LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                for (location in p0.locations) {
                    listener.onLocationChanged(location)
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun startListeningUserLocation() {
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }

    fun stopLocationUpdates() {
        fusedLocationClient.removeLocationUpdates(locationCallback)
    }
}