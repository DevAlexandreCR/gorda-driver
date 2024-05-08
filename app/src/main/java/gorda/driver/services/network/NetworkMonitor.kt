package gorda.driver.services.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class NetworkMonitor(private val context: Context,
                     private val onNetWorkChange: (isConnected: Boolean) -> Unit) {

    private val connectivityManager =
        this.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isMonitoring = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val connected: Boolean = isOnline(network)
            onNetWorkChange.invoke(connected)
        }

        override fun onLost(network: Network) {
            onNetWorkChange.invoke(false)
        }

        fun isOnline(network: Network): Boolean {
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    fun startMonitoring() {
        if (!isMonitoring) {
            val networkRequest =
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build()

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            isMonitoring = true
        }
    }

    fun stopMonitoring() {
        if (isMonitoring) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isMonitoring = false
            onNetWorkChange.invoke(false)
        }
    }
}