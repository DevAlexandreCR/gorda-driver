package gorda.driver.services.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkMonitorLogicTest {

    @Test
    fun internetWithoutValidatedFlagStillCountsAsTransportNetwork() {
        val status = NetworkMonitor.resolveNetworkStatus(
            hasInternet = true,
            isValidated = false,
            hasSupportedTransport = true
        )

        assertTrue(status.hasTransportNetwork)
        assertFalse(status.networkValidated)
    }

    @Test
    fun missingTransportStillCountsAsOffline() {
        val status = NetworkMonitor.resolveNetworkStatus(
            hasInternet = true,
            isValidated = true,
            hasSupportedTransport = false
        )

        assertFalse(status.hasTransportNetwork)
    }
}
