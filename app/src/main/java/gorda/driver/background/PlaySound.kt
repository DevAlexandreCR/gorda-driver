package gorda.driver.background

import android.content.Context
import android.content.SharedPreferences
import android.media.MediaPlayer
import android.os.Build
import gorda.driver.R
import gorda.driver.utils.Constants

class PlaySound(private val context: Context, private val sharedPreferences: SharedPreferences) {

    private val player: MediaPlayer = MediaPlayer.create(context, R.raw.new_service)

    fun playCancelSound(notifyId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            player.reset()
            player.setDataSource(context.resources.openRawResourceFd(R.raw.cancel_service))
            player.prepare()
        }
        if (!player.isPlaying) player.start()
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putInt(Constants.CANCEL_SERVICES_NOTIFICATION_ID, notifyId)
        editor.apply()
    }

    fun playAssignedSound(notifyId: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            player.reset()
            player.setDataSource(context.resources.openRawResourceFd(R.raw.assigned_service))
            player.prepare()
        }
        if (!player.isPlaying) player.start()
        val editor: SharedPreferences.Editor = sharedPreferences.edit()
        editor.putInt(Constants.SERVICES_NOTIFICATION_ID, notifyId)
        editor.apply()
    }

    fun playNewService() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            player.reset()
            player.setDataSource(context.resources.openRawResourceFd(R.raw.new_service))
            player.prepare()
        }
        if (!player.isPlaying) player.start()
    }
}