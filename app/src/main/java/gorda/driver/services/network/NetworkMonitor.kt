package gorda.driver.services.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class NetworkMonitor(
    private val context: Context,
    private val onNetworkChange: (status: NetworkStatus) -> Unit
) {

    data class NetworkStatus(
        val hasTransportNetwork: Boolean,
        val networkValidated: Boolean
    )

    companion object {
        private const val TAG = "NetworkMonitor"
        private const val DEBOUNCE_DELAY = 1000L

        internal fun resolveNetworkStatus(
            hasInternet: Boolean,
            isValidated: Boolean,
            hasSupportedTransport: Boolean
        ): NetworkStatus {
            return NetworkStatus(
                hasTransportNetwork = hasInternet && hasSupportedTransport,
                networkValidated = isValidated
            )
        }
    }

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    private var isMonitoring = false
    private var currentNetworkState = NetworkStatus(
        hasTransportNetwork = false,
        networkValidated = false
    )
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var debounceJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            Log.d(TAG, "Network available: $network")
        }

        override fun onLost(network: Network) {
            Log.d(TAG, "Network lost: $network")
            scheduleNetworkCheck(
                NetworkStatus(
                    hasTransportNetwork = false,
                    networkValidated = false
                )
            )
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            Log.d(TAG, "Network capabilities changed: $network")
            val status = networkStatusFromCapabilities(networkCapabilities)
            Log.d(
                TAG,
                "Network status - transport=${status.hasTransportNetwork} validated=${status.networkValidated}"
            )
            scheduleNetworkCheck(status)
        }

        override fun onUnavailable() {
            Log.d(TAG, "Network unavailable")
            scheduleNetworkCheck(
                NetworkStatus(
                    hasTransportNetwork = false,
                    networkValidated = false
                )
            )
        }
    }

    private fun scheduleNetworkCheck(status: NetworkStatus) {
        debounceJob?.cancel()
        debounceJob = scope.launch {
            delay(DEBOUNCE_DELAY)
            val actualState = if (status.hasTransportNetwork) {
                status
            } else {
                currentActiveNetworkStatus()
            }
            updateNetworkState(actualState)
        }
    }

    internal fun networkStatusFromCapabilities(
        capabilities: NetworkCapabilities?
    ): NetworkStatus {
        if (capabilities == null) {
            return NetworkStatus(
                hasTransportNetwork = false,
                networkValidated = false
            )
        }

        val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        val hasSupportedTransport = capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
            capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)

        return resolveNetworkStatus(
            hasInternet = hasInternet,
            isValidated = isValidated,
            hasSupportedTransport = hasSupportedTransport
        )
    }

    private fun currentActiveNetworkStatus(): NetworkStatus {
        return try {
            val activeNetwork = connectivityManager.activeNetwork
                ?: return NetworkStatus(
                    hasTransportNetwork = false,
                    networkValidated = false
                )
            val capabilities = connectivityManager.getNetworkCapabilities(activeNetwork)
            val status = networkStatusFromCapabilities(capabilities)

            Log.d(
                TAG,
                "Active network check - transport=${status.hasTransportNetwork}, validated=${status.networkValidated}"
            )
            status
        } catch (e: Exception) {
            Log.e(TAG, "Error checking active network: ${e.message}")
            NetworkStatus(
                hasTransportNetwork = false,
                networkValidated = false
            )
        }
    }

    private fun updateNetworkState(status: NetworkStatus) {
        if (currentNetworkState != status) {
            currentNetworkState = status
            Log.d(
                TAG,
                "Network state CHANGED to: transport=${status.hasTransportNetwork} validated=${status.networkValidated}"
            )
            onNetworkChange(status)
        } else {
            Log.d(
                TAG,
                "Network state UNCHANGED: transport=${status.hasTransportNetwork} validated=${status.networkValidated}"
            )
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

            val initialState = currentActiveNetworkStatus()
            currentNetworkState = initialState
            Log.d(
                TAG,
                "Initial network state: transport=${initialState.hasTransportNetwork} validated=${initialState.networkValidated}"
            )

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
    fun cleanup() {
        stopMonitoring()
        scope.cancel()
    }
}
