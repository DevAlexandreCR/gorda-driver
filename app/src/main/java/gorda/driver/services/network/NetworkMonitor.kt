package gorda.driver.services.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkMonitor(
    private val context: Context,
    private val onNetworkChange: (isConnected: Boolean) -> Unit
) {

    companion object {
        private const val TAG = "NetworkMonitor"
        private const val DEBOUNCE_DELAY = 1000L
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isMonitoring = false
    private var currentNetworkState = false
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var debounceJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available: $network")
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: $network")
            scheduleNetworkCheck(false)
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            Log.d(TAG, "Network capabilities changed: $network")
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            Log.d(TAG, "Network has internet: $hasInternet")
            scheduleNetworkCheck(hasInternet)
        }

        override fun onUnavailable() {
            Log.d(TAG, "Network unavailable")
            scheduleNetworkCheck(false)
        }
    }

    private fun scheduleNetworkCheck(hasInternet: Boolean) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_DELAY)
            val actualState = if (hasInternet) {
                hasInternet
            } else {
                // Double-check if there are any active networks
                hasAnyActiveNetwork()
            }
            updateNetworkState(actualState)
        }
    }

    private fun hasAnyActiveNetwork(): Boolean {
        return try {
            val activeNetwork = connectivityManager.activeNetwork ?: return false
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork) ?: return false

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            Log.d(TAG, "Active network check - Internet: $hasInternet, Validated: $isValidated")
            hasInternet && isValidated
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active network: ${e.message}")
            false
        }
    }

    private fun updateNetworkState(isConnected: Boolean) {
        if (currentNetworkState != isConnected) {
            currentNetworkState = isConnected
            Log.d(TAG, "Network state CHANGED to: ${if (isConnected) "CONNECTED" else "DISCONNECTED"}")

            // Manage Firebase connection state
            try {
                if (isConnected) {
                    // When network is restored, tell Firebase to go back online
                    FirebaseDatabase.getInstance().goOnline()
                    Log.d(TAG, "Firebase set to ONLINE")
                } else {
                    // When network is lost, Firebase will handle offline mode automatically
                    // but we can explicitly set it offline if needed
                    Log.d(TAG, "Network lost - Firebase will handle offline mode")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error managing Firebase state: ${e.message}")
            }

            onNetworkChange(isConnected)
        } else {
            Log.d(TAG, "Network state UNCHANGED: ${if (isConnected) "CONNECTED" else "DISCONNECTED"}")
        }
    }

    fun startMonitoring() {
        if (!isMonitoring) {
            Log.d(TAG, "Starting network monitoring")

            val networkRequest = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .addTransportType(NetworkCapabilities.TRANSPORT_ETHERNET)
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            connectivityManager.registerNetworkCallback(networkRequest, networkCallback)

            val initialState = isCurrentlyConnected()
            currentNetworkState = initialState
            Log.d(TAG, "Initial network state: ${if (initialState) "CONNECTED" else "DISCONNECTED"}")

            onNetworkChange(initialState)
            isMonitoring = true
        }
    }

    fun stopMonitoring() {
        if (isMonitoring) {
            Log.d(TAG, "Stopping network monitoring")
            debounceJob?.cancel()
            try {
                connectivityManager.unregisterNetworkCallback(networkCallback)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "NetworkCallback was already unregistered")
            }
            isMonitoring = false
        }
    }

    private fun isCurrentlyConnected(): Boolean {
        return hasAnyActiveNetwork()
    }

    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}