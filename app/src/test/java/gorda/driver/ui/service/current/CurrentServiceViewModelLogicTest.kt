package gorda.driver.ui.service.current

import gorda.driver.interfaces.RideFees
import gorda.driver.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class CurrentServiceViewModelLogicTest {

    @Test
    fun offlinePresenceBlocksServiceActions() {
        val status = CurrentServiceViewModel.connectionStatusForServiceAction(
            MainViewModel.DriverPresenceState(
                hasNetwork = false,
                firebaseConnected = true,
                actualOnline = true
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionConnectionStatus.OFFLINE, status)
    }

    @Test
    fun firebaseDisconnectedBlocksServiceActions() {
        val status = CurrentServiceViewModel.connectionStatusForServiceAction(
            MainViewModel.DriverPresenceState(
                hasNetwork = true,
                firebaseConnected = false,
                actualOnline = true
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionConnectionStatus.FIREBASE_DISCONNECTED, status)
    }

    @Test
    fun reconnectingPresenceBlocksServiceActions() {
        val status = CurrentServiceViewModel.connectionStatusForServiceAction(
            MainViewModel.DriverPresenceState(
                hasNetwork = true,
                firebaseConnected = true,
                actualOnline = false
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionConnectionStatus.RECONNECTING, status)
    }

    @Test
    fun connectedPresenceAllowsServiceActions() {
        val state = MainViewModel.DriverPresenceState(
            hasNetwork = true,
            firebaseConnected = true,
            actualOnline = true
        )

        assertEquals(
            CurrentServiceViewModel.ServiceActionConnectionStatus.READY,
            CurrentServiceViewModel.connectionStatusForServiceAction(state)
        )
        assertEquals(true, CurrentServiceViewModel.isReadyForServiceAction(state))
    }

    @Test
    fun startUsesLiveRideFeesWhenAvailable() {
        val liveFees = RideFees(priceKm = 4500.0)

        val resolution = CurrentServiceViewModel.resolveStartRideFees(
            liveFees = liveFees,
            inMemoryFees = RideFees(),
            storedFees = null,
            currentMultiplier = 1.0,
            storedMultiplier = null
        )

        assertEquals(CurrentServiceViewModel.StartRideFeesSource.LIVE, resolution.source)
        assertEquals(liveFees, resolution.fees)
    }

    @Test
    fun startFallsBackToCachedRideFeesWhenRemoteFails() {
        val cachedFees = RideFees(priceKm = 4200.0, feeMultiplier = 1.3)

        val resolution = CurrentServiceViewModel.resolveStartRideFees(
            liveFees = null,
            inMemoryFees = cachedFees,
            storedFees = null,
            currentMultiplier = 1.5,
            storedMultiplier = null
        )

        assertEquals(CurrentServiceViewModel.StartRideFeesSource.FALLBACK, resolution.source)
        assertNotNull(resolution.fees)
        assertEquals(1.5, resolution.fees?.feeMultiplier)
    }

    @Test
    fun startFailsWhenNoRideFeesSourceExists() {
        val resolution = CurrentServiceViewModel.resolveStartRideFees(
            liveFees = null,
            inMemoryFees = RideFees(),
            storedFees = null,
            currentMultiplier = null,
            storedMultiplier = null
        )

        assertEquals(CurrentServiceViewModel.StartRideFeesSource.UNAVAILABLE, resolution.source)
        assertEquals(null, resolution.fees)
    }

    @Test
    fun startFallsBackToStoredRideFeesSnapshotWhenMemoryIsEmpty() {
        val storedFees = RideFees(priceKm = 3100.0, feeMultiplier = 1.2)

        val resolution = CurrentServiceViewModel.resolveStartRideFees(
            liveFees = null,
            inMemoryFees = RideFees(),
            storedFees = storedFees,
            currentMultiplier = null,
            storedMultiplier = 1.7
        )

        assertEquals(CurrentServiceViewModel.StartRideFeesSource.FALLBACK, resolution.source)
        assertNotNull(resolution.fees)
        assertEquals(1.7, resolution.fees?.feeMultiplier)
    }

    @Test
    fun endTripOnlyFinishesLocallyAfterConfirmedSuccess() {
        assertEquals(
            CurrentServiceViewModel.EndTripLocalOutcome.FINISH_LOCALLY,
            CurrentServiceViewModel.endTripOutcome(writeSucceeded = true)
        )
        assertEquals(
            CurrentServiceViewModel.EndTripLocalOutcome.KEEP_ACTIVE_RETRYABLE,
            CurrentServiceViewModel.endTripOutcome(writeSucceeded = false)
        )
    }
}
