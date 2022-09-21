package gorda.driver.utils

import android.os.Build

object Utils {
    fun isNewerVersion(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
    }
}