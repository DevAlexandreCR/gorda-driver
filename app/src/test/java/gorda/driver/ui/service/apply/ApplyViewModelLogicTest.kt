package gorda.driver.ui.service.apply

import gorda.driver.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApplyViewModelLogicTest {

    @Test
    fun offlinePresenceBlocksApply() {
        val status = ApplyViewModel.connectionStatusForApply(
            MainViewModel.DriverPresenceState(
                hasTransportNetwork = false,
                firebaseConnected = true,
                actualOnline = true
            )
        )

        assertEquals(ApplyViewModel.ApplyConnectionStatus.OFFLINE, status)
    }

    @Test
    fun confirmedOnlinePresenceAllowsApplyWithoutFirebaseSocket() {
        val status = ApplyViewModel.connectionStatusForApply(
            MainViewModel.DriverPresenceState(
                hasTransportNetwork = true,
                firebaseConnected = false,
                actualOnline = true
            )
        )

        assertEquals(ApplyViewModel.ApplyConnectionStatus.READY, status)
        assertTrue(
            ApplyViewModel.isReadyToApply(
                MainViewModel.DriverPresenceState(
                    hasTransportNetwork = true,
                    firebaseConnected = false,
                    actualOnline = true
                )
            )
        )
    }

    @Test
    fun reconnectingPresenceBlocksApply() {
        val status = ApplyViewModel.connectionStatusForApply(
            MainViewModel.DriverPresenceState(
                hasTransportNetwork = true,
                firebaseConnected = true,
                actualOnline = false,
                phase = MainViewModel.DriverPresencePhase.RECONNECTING
            )
        )

        assertEquals(ApplyViewModel.ApplyConnectionStatus.RECONNECTING, status)
    }

    @Test
    fun connectedPresenceAllowsApply() {
        val status = ApplyViewModel.connectionStatusForApply(
            MainViewModel.DriverPresenceState(
                hasTransportNetwork = true,
                firebaseConnected = true,
                actualOnline = true,
                phase = MainViewModel.DriverPresencePhase.CONNECTED
            )
        )

        assertEquals(ApplyViewModel.ApplyConnectionStatus.READY, status)
        assertTrue(
            ApplyViewModel.isReadyToApply(
                MainViewModel.DriverPresenceState(
                    hasTransportNetwork = true,
                    firebaseConnected = true,
                    actualOnline = true
                )
            )
        )
    }

    @Test
    fun routeEstimateUsesCoordinatesWithoutMapFragment() {
        val estimate = ApplyViewModel.estimateRoute(
            originLat = 4.6482837,
            originLng = -74.2478944,
            destinationLat = 4.653332,
            destinationLng = -74.242706
        )

        assertTrue(estimate.distanceMeters > 0)
        assertEquals((estimate.distanceMeters / 5f).toInt(), estimate.timeSeconds)
    }

    @Test
    fun routeEstimateReturnsZeroForSameCoordinates() {
        val estimate = ApplyViewModel.estimateRoute(
            originLat = 4.6482837,
            originLng = -74.2478944,
            destinationLat = 4.6482837,
            destinationLng = -74.2478944
        )

        assertEquals(0, estimate.distanceMeters)
        assertEquals(0, estimate.timeSeconds)
    }
}
