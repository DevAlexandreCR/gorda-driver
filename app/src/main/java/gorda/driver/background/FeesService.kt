package gorda.driver.background

import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.LocationListener
import com.google.android.gms.maps.model.LatLng
import gorda.driver.R
import gorda.driver.activity.StartActivity
import gorda.driver.utils.Constants
import gorda.driver.utils.Utils

class FeesService: Service(), LocationListener {

    private val binder = ChronometerBinder()
    private val points = ArrayList<LatLng>()
    private var startTime: Long = 0
    private var name: String = ""
    companion object {
        const val SERVICE_ID = 101
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getDoubleArrayExtra(Constants.START_TRIP)?.also { location ->
            val startLat = location[0]
            val startLng = location[1]
            LatLng(startLat, startLng).also { points.add(it) }
        }
        intent?.getStringExtra(Constants.LOCATION_EXTRA)?.also { locationName ->
            name = locationName
        }

        createNotification()

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startTime = SystemClock.elapsedRealtime()
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
        if (Utils.isNewerVersion(Build.VERSION_CODES.N)) {
            builder.priority = NotificationManager.IMPORTANCE_HIGH
        }
        if (Utils.isNewerVersion(Build.VERSION_CODES.O)) {
            startForeground(SERVICE_ID, builder.build())
        }
    }

    override fun onLocationChanged(location: Location) {
        LatLng(location.latitude, location.longitude).also { points.add(it) }
    }

    fun getBaseTime(): Long {
        return startTime
    }

    fun getElapsedTime(): Long {
        return SystemClock.elapsedRealtime() - startTime
    }

    fun getPoints(): ArrayList<LatLng> {
        return points
    }

    inner class ChronometerBinder: Binder() {
        fun getService(): FeesService = this@FeesService
    }
}