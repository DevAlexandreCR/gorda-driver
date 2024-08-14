package gorda.go.ui.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import gorda.go.interfaces.LocationUpdateInterface

class LocationBroadcastReceiver(received: LocationUpdateInterface): BroadcastReceiver() {
    private var received: LocationUpdateInterface

    companion object {
        const val ACTION_LOCATION_UPDATES = "location.updates"
    }

    init {
        this.received = received
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let { received.onUpdate(it) }
    }
}