package gorda.driver.utils

import android.os.Build
import io.sentry.Sentry
import io.sentry.SentryEvent
import io.sentry.SentryLevel
import io.sentry.protocol.Message

object Utils {
    fun isNewerVersion(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
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
}