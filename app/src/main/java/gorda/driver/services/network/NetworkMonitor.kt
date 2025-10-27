package gorda.driver.services.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log

class NetworkMonitor(private val context: Context,
                     private val onNetworkChange: (isConnected: Boolean) -> Unit) {

    companion object {
        private const val TAG = "NetworkMonitor"
    }

    private val connectivityManager =
        this.context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isMonitoring = false
    private var currentNetworkState = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available: $network")
            if (hasInternetConnectivity(network)) {
                Log.d(TAG, "Network has internet connectivity")
                updateNetworkState(true)
            }
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: $network")
            // Check if there are any other active networks
            if (!hasAnyActiveNetwork()) {
                Log.d(TAG, "No active networks available")
                updateNetworkState(false)
            }
        }

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) {
            Log.d(TAG, "Network capabilities changed: $network")
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            updateNetworkState(hasInternet)
        }

        override fun onUnavailable() {
            Log.d(TAG, "Network unavailable")
            updateNetworkState(false)
        }

        private fun hasInternetConnectivity(network: Network): Boolean {
            val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        private fun hasAnyActiveNetwork(): Boolean {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

        private fun updateNetworkState(isConnected: Boolean) {
            if (currentNetworkState != isConnected) {
                currentNetworkState = isConnected
                Log.d(TAG, "Network state changed to: ${if (isConnected) "CONNECTED" else "DISCONNECTED"}")
                onNetworkChange.invoke(isConnected)
            }
        }
    }

    fun startMonitoring() {
        if (!isMonitoring) {
            Log.d(TAG, "Starting network monitoring")
            val networkRequest =
                NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
                    .build()

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

            // Check initial state and notify
            val initialState = isCurrentlyConnected()
            currentNetworkState = initialState
            Log.d(TAG, "Initial network state: ${if (initialState) "CONNECTED" else "DISCONNECTED"}")

            // Notify the callback with the current state to update UI properly
            onNetworkChange.invoke(initialState)

            isMonitoring = true
        }
    }

    fun stopMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Stopping network monitoring")
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isMonitoring = false
        }
    }

    private fun isCurrentlyConnected(): Boolean {
        val activeNetwork = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}