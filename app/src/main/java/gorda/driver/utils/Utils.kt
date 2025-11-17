package gorda.driver.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build

object Utils {
    fun isNewerVersion(version: Int): Boolean {
        return Build.VERSION.SDK_INT >= version
    }


    fun isAppInForeground(context: Context): Boolean {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val runningAppProcesses = activityManager.runningAppProcesses ?: return false

        for (appProcess in runningAppProcesses) {
            if (appProcess.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND &&
                appProcess.processName == context.packageName) {
                return true
            }
        }
        return false
    }
}