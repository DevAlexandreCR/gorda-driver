package gorda.driver.helpers

import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Task

fun <T> Task<T>.withTimeout(timeout: Long = 3000, onTimeout: () -> Unit) {
    val handler = Handler(Looper.getMainLooper())
    val timeoutRunnable = Runnable {
        if (!this.isComplete) {
            onTimeout.invoke()
        }
    }

    handler.postDelayed(timeoutRunnable, timeout)

    this.addOnCompleteListener {
        handler.removeCallbacks(timeoutRunnable)
    }
}