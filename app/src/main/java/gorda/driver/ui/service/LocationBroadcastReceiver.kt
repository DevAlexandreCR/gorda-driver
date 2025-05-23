package gorda.driver.ui.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import gorda.driver.interfaces.LocationUpdateInterface

class LocationBroadcastReceiver(private var received: LocationUpdateInterface): BroadcastReceiver() {

    companion object {
        const val ACTION_LOCATION_UPDATES = "location.updates"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let { received.onUpdate(it) }
    }
}