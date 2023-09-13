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
import gorda.driver.R
import gorda.driver.activity.StartActivity
import gorda.driver.utils.Constants
import gorda.driver.utils.Utils

class FeesService: Service(), LocationListener {

    private val binder = ChronometerBinder()
    private var startTime: Long = 0
    companion object {
        const val SERVICE_ID = 101
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.getDoubleArrayExtra(Constants.START_TRIP)?.let { location ->
            val startLat = location[0]
            val startLng = location[1]
        }

        return START_STICKY
    }

    override fun onCreate() {
        super.onCreate()
        startTime = SystemClock.elapsedRealtime()
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
            .setContentTitle("Trip")
            .setContentText("12345")
            .setContentIntent(pendingIntent)
        if (Utils.isNewerVersion(Build.VERSION_CODES.N)) {
            builder.priority = NotificationManager.IMPORTANCE_HIGH
        }
        if (Utils.isNewerVersion(Build.VERSION_CODES.O)) {
            startForeground(SERVICE_ID, builder.build())
        }
    }

    override fun onBind(p0: Intent?): IBinder {
        return binder
    }

    override fun onLocationChanged(p0: Location) {
        TODO("Not yet implemented")
    }

    fun getElapsedTime(): Long {
        return startTime
    }

    inner class ChronometerBinder: Binder() {
        fun getService(): FeesService = this@FeesService
    }
}