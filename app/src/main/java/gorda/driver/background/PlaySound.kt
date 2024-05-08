package gorda.driver.background

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import gorda.driver.R
import gorda.driver.utils.Constants
import io.sentry.Sentry

class PlaySound(private val context: Context, private val sharedPreferences: SharedPreferences) {

    private val player: MediaPlayer = MediaPlayer.create(context, R.raw.new_service)

    fun playCancelSound(notifyId: Int) {
        val mute = sharedPreferences.getBoolean(Constants.NOTIFICATION_MUTE, false)
        if (mute) return

        val chanel =
            sharedPreferences.getString(Constants.NOTIFICATION_CANCELED, Constants.NOTIFICATION_TONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            player.reset()
            player.setDataSource(context.resources.openRawResourceFd(R.raw.cancel_service))
            player.prepare()
        }
        if (!player.isPlaying && chanel == Constants.NOTIFICATION_TONE) player.start()

        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putInt(Constants.CANCEL_SERVICES_NOTIFICATION_ID, notifyId)
        editor.apply()
    }

    fun playAssignedSound(notifyId: Int) {
        val mute = sharedPreferences.getBoolean(Constants.NOTIFICATION_MUTE, false)
        if (mute) return

        val chanel =
            sharedPreferences.getString(Constants.NOTIFICATION_ASSIGNED, Constants.NOTIFICATION_TONE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            player.reset()
            player.setDataSource(context.resources.openRawResourceFd(R.raw.assigned_service))
            player.prepare()
        }
        if (!player.isPlaying && chanel == Constants.NOTIFICATION_TONE) player.start()

        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putInt(Constants.SERVICES_NOTIFICATION_ID, notifyId)
        editor.apply()
    }

    fun playNewService() {
        val mute = sharedPreferences.getBoolean(Constants.NOTIFICATION_MUTE, false)
        if (mute) return

        try {
            val source = sharedPreferences.getString(Constants.NOTIFICATION_RINGTONE,
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION).toString())
            player.reset()
            player.setDataSource(context, Uri.parse(source))
            player.prepare()
            if (!player.isPlaying) player.start()
        } catch (e: Exception)  {
            Sentry.captureException(e)
        }

    }
}