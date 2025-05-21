package gorda.driver.ui.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import gorda.driver.interfaces.LocationUpdateInterface

class ConnectionBroadcastReceiver(private var received: LocationUpdateInterface): BroadcastReceiver() {

    companion object {
        const val ACTION_CONNECTION = "action.connection"
    }

    override fun onReceive(context: Context?, intent: Intent?) {
        intent?.let { received.onUpdate(it) }
    }
}