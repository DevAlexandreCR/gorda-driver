package gorda.driver

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class GordaDriverApp : Application() {
    override fun onCreate() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
        super.onCreate()
    }
}
