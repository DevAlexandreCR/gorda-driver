package gorda.driver.ui.service.current

import androidx.lifecycle.SavedStateHandle
import gorda.driver.interfaces.RideFees
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import gorda.driver.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
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
                actualOnline = false,
                phase = MainViewModel.DriverPresencePhase.RECONNECTING
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionConnectionStatus.RECONNECTING, status)
    }

    @Test
    fun waitingForPresenceAckStillBlocksServiceActions() {
        val status = CurrentServiceViewModel.connectionStatusForServiceAction(
            MainViewModel.DriverPresenceState(
                hasNetwork = true,
                firebaseConnected = true,
                actualOnline = false,
                phase = MainViewModel.DriverPresencePhase.WAITING_FOR_PRESENCE_ACK
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionConnectionStatus.RECONNECTING, status)
    }

    @Test
    fun connectedPresenceAllowsServiceActions() {
        val state = MainViewModel.DriverPresenceState(
            hasNetwork = true,
            firebaseConnected = true,
            actualOnline = true,
            phase = MainViewModel.DriverPresencePhase.CONNECTED
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

    @Test
    fun coldStartRestoreOfPendingStartReconcilesToRetryableFailureWhenConnectionReady() {
        val viewModel = CurrentServiceViewModel(SavedStateHandle())
        val request = CurrentServiceViewModel.StartTripRequest(
            serviceId = "service-1",
            startedAt = 100L,
            multiplier = 1.2,
            origin = "Origin"
        )
        viewModel.restoreFromStoreIfNeeded(
            pendingActionSnapshot = PendingServiceActionSnapshot(
                serviceId = "service-1",
                actionType = PendingServiceActionType.START,
                phase = PendingServiceActionPhase.IN_FLIGHT_RECOVERABLE,
                startRequest = request
            ),
            currentServiceUiSnapshot = null,
            bottomSheetSnapshot = null
        )

        viewModel.reconcileRestoredPendingAction(
            service = Service(
                id = "service-1",
                status = Service.STATUS_IN_PROGRESS,
                metadata = ServiceMetadata(arrived_at = 50L)
            ),
            presenceState = MainViewModel.DriverPresenceState(
                hasNetwork = true,
                firebaseConnected = true,
                actualOnline = true,
                phase = MainViewModel.DriverPresencePhase.CONNECTED
            )
        )

        val uiState = viewModel.uiState.value
        assertTrue(uiState is CurrentServiceViewModel.ServiceActionUiState.StartFailed)
        assertEquals(request, viewModel.getStartTripRequest())
    }

    @Test
    fun coldStartRestoreOfPendingEndStaysBlockedWhileConnectionIsUnavailable() {
        val viewModel = CurrentServiceViewModel(SavedStateHandle())
        val request = CurrentServiceViewModel.EndTripRequest(
            serviceId = "service-1",
            endedAt = 100L,
            route = "{}",
            tripDistance = 1200,
            tripFee = 5500,
            multiplier = 1.0
        )
        viewModel.restoreFromStoreIfNeeded(
            pendingActionSnapshot = PendingServiceActionSnapshot(
                serviceId = "service-1",
                actionType = PendingServiceActionType.END,
                phase = PendingServiceActionPhase.FAILED,
                failureMessageRes = gorda.driver.R.string.error_timeout,
                endRequest = request
            ),
            currentServiceUiSnapshot = null,
            bottomSheetSnapshot = null
        )

        viewModel.reconcileRestoredPendingAction(
            service = Service(
                id = "service-1",
                status = Service.STATUS_IN_PROGRESS,
                metadata = ServiceMetadata(arrived_at = 50L, start_trip_at = 60L)
            ),
            presenceState = MainViewModel.DriverPresenceState(
                hasNetwork = false,
                firebaseConnected = false,
                actualOnline = false
            )
        )

        assertEquals(
            CurrentServiceViewModel.ServiceActionUiState.BlockedEndByConnection,
            viewModel.uiState.value
        )
        assertEquals(request, viewModel.getEndTripRequest())
    }

    @Test
    fun observedStartedTripClearsRestoredPendingStartSnapshot() {
        val viewModel = CurrentServiceViewModel(SavedStateHandle())
        viewModel.restoreFromStoreIfNeeded(
            pendingActionSnapshot = PendingServiceActionSnapshot(
                serviceId = "service-1",
                actionType = PendingServiceActionType.START,
                phase = PendingServiceActionPhase.BLOCKED_BY_CONNECTION,
                startRequest = CurrentServiceViewModel.StartTripRequest(
                    serviceId = "service-1",
                    startedAt = 100L,
                    multiplier = 1.0,
                    origin = "Origin"
                )
            ),
            currentServiceUiSnapshot = null,
            bottomSheetSnapshot = null
        )

        viewModel.reconcileRestoredPendingAction(
            service = Service(
                id = "service-1",
                status = Service.STATUS_IN_PROGRESS,
                metadata = ServiceMetadata(arrived_at = 50L, start_trip_at = 70L)
            ),
            presenceState = MainViewModel.DriverPresenceState(
                hasNetwork = true,
                firebaseConnected = true,
                actualOnline = true
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionUiState.Idle, viewModel.uiState.value)
        assertNull(viewModel.getPendingActionSnapshot())
        assertNull(viewModel.getStartTripRequest())
    }
}
