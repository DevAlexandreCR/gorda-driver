package gorda.godriver.utils

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message

object Utils {
    fun isNewerVersion(version: Int): Boolean {
        return Build.VERSION.SDK_INT >= version
    }

    fun sendEvent(text: String, sentryLevel: SentryLevel = SentryLevel.ERROR) {
        val event = SentryEvent().apply {
            message = Message().apply {
                message = text
            }
            level = sentryLevel
        }
        Sentry.captureEvent(event)
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