package gorda.driver.background

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
import gorda.driver.utils.Constants
import gorda.driver.utils.Utils

class FeesService: Service(), LocationListener {

    private val binder = ChronometerBinder()
    private var startTime: Long = 0

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        intent?.getDoubleArrayExtra(Constants.START_TRIP)?.let { location ->
            val startLat = location[0]
            val startLng = location[1]
            startTime = SystemClock.elapsedRealtime()
            val builder: NotificationCompat.Builder = NotificationCompat.Builder(
                this,
                Constants.LOCATION_NOTIFICATION_CHANNEL_ID
            )
                .setOngoing(false)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle(getString(R.string.status_connected))
                .setContentText(getString(R.string.text_connected))
            if (Utils.isNewerVersion(Build.VERSION_CODES.O)) {
                startForeground(LocationService.SERVICE_ID, builder.build())
            }
        }

        return START_STICKY
    }
    override fun onBind(p0: Intent?): IBinder? {
        TODO("Not yet implemented")
    }

    override fun onLocationChanged(p0: Location) {
        TODO("Not yet implemented")
    }

    fun getElapsedTime(): Long {
        return SystemClock.elapsedRealtime() - startTime
    }

    inner class ChronometerBinder: Binder() {
        fun getService(): FeesService = this@FeesService
    }
}