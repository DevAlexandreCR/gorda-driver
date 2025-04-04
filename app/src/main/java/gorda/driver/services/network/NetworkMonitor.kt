package gorda.driver.services.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest

class NetworkMonitor(private val context: Context,
                     private val onNetworkChange: (isConnected: Boolean) -> Unit) {

    private val connectivityManager =
        this.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isMonitoring = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            if (isMobileDataConnected(network)) {
                onNetworkChange.invoke(true)
            }
        }

        override fun onLost(network: Network) {
            onNetworkChange.invoke(false)
        }

        private fun isMobileDataConnected(network: Network): Boolean {
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    fun startMonitoring() {
        if (!isMonitoring) {
            val networkRequest =
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .build()

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)
            isMonitoring = true
        }
    }

    fun stopMonitoring() {
        if (isMonitoring) {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isMonitoring = false
            onNetworkChange.invoke(false)
        }
    }
}