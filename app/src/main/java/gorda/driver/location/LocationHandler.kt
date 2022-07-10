package gorda.driver.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.*
import com.google.android.gms.tasks.Task
import gorda.driver.interfaces.CustomLocationListener

object LocationHandler {
    private var fusedLocationClient: FusedLocationProviderClient? = null
    private var locationCallback: LocationCallback? = null

    const val PERMISSION_REQUEST_ACCESS_LOCATION = 100
    private const val LOCATION_REFRESH_TIME = 5000

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

    @SuppressLint("MissingPermission")
    fun startListeningUserLocation(context: Context, listener: CustomLocationListener) {
        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(context)
        val locationRequest = LocationRequest.create().apply {
            interval = LOCATION_REFRESH_TIME.toLong() * 2
            fastestInterval = LOCATION_REFRESH_TIME.toLong()
            priority = Priority.PRIORITY_HIGH_ACCURACY
        }
        locationCallback = object : LocationCallback() {
            override fun onLocationResult(p0: LocationResult) {
                for (location in p0.locations) {
                    listener.onLocationChanged(location)
                }
            }
        }
        fusedLocationClient!!.requestLocationUpdates(
            locationRequest,
            locationCallback as LocationCallback,
            Looper.getMainLooper()
        )
    }

    fun getLastLocation(): Task<Location>? {
        return fusedLocationClient?.lastLocation
    }

    fun stopLocationUpdates() {
        fusedLocationClient ?: return
        locationCallback?.let { fusedLocationClient!!.removeLocationUpdates(it) }
    }
}