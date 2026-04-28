package gorda.driver.ui.service.current

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import androidx.lifecycle.SavedStateHandle
import gorda.driver.interfaces.RideFees
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import gorda.driver.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class CurrentServiceViewModelLogicTest {

    @get:Rule
    val instantTaskExecutorRule = InstantTaskExecutorRule()

    @Test
    fun missingTransportBlocksServiceActions() {
        val status = CurrentServiceViewModel.connectionStatusForServiceAction(
            MainViewModel.DriverPresenceState(
                hasTransportNetwork = false,
                firebaseConnected = true,
                actualOnline = true
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionConnectionStatus.NO_TRANSPORT, status)
    }

    @Test
    fun firebaseDisconnectedAllowsQueueableRecoveryForServiceActions() {
        val status = CurrentServiceViewModel.connectionStatusForServiceAction(
            MainViewModel.DriverPresenceState(
                hasTransportNetwork = true,
                firebaseConnected = false,
                actualOnline = false
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionConnectionStatus.RECOVERING_BUT_QUEUEABLE, status)
    }

    @Test
    fun confirmedOnlinePresenceAllowsServiceActionsWithoutFirebaseSocket() {
        val status = CurrentServiceViewModel.connectionStatusForServiceAction(
            MainViewModel.DriverPresenceState(
                hasTransportNetwork = true,
                firebaseConnected = false,
                actualOnline = true
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionConnectionStatus.READY, status)
    }

    @Test
    fun reconnectingPresenceAllowsQueueableRecoveryForServiceActions() {
        val status = CurrentServiceViewModel.connectionStatusForServiceAction(
            MainViewModel.DriverPresenceState(
                hasTransportNetwork = true,
                firebaseConnected = true,
                actualOnline = false,
                phase = MainViewModel.DriverPresencePhase.RECONNECTING
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionConnectionStatus.RECOVERING_BUT_QUEUEABLE, status)
    }

    @Test
    fun activePendingSyncMarksServiceActionsAsSyncing() {
        val status = CurrentServiceViewModel.connectionStatusForServiceAction(
            MainViewModel.DriverPresenceState(
                hasTransportNetwork = true,
                firebaseConnected = true,
                actualOnline = true
            ),
            hasPendingSync = true
        )

        assertEquals(CurrentServiceViewModel.ServiceActionConnectionStatus.SYNCING, status)
    }

    @Test
    fun connectedPresenceAllowsServiceActions() {
        val state = MainViewModel.DriverPresenceState(
            hasTransportNetwork = true,
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
    fun assignedServiceBeforeTimeoutKeepsServicesFabHidden() {
        assertFalse(
            CurrentServiceViewModel.shouldShowServicesFab(
                currentService = inProgressService(createdAt = 900L),
                hasNextService = false,
                timeoutToConnectionSeconds = 120,
                rideElapsedSeconds = 0L,
                nowEpochSeconds = 1_000L,
                serviceStageOverride = CurrentServiceViewModel.ServiceStageOverride()
            )
        )
    }

    @Test
    fun assignedServiceAfterTimeoutShowsServicesFab() {
        assertTrue(
            CurrentServiceViewModel.shouldShowServicesFab(
                currentService = inProgressService(createdAt = 900L),
                hasNextService = false,
                timeoutToConnectionSeconds = 120,
                rideElapsedSeconds = 0L,
                nowEpochSeconds = 1_021L,
                serviceStageOverride = CurrentServiceViewModel.ServiceStageOverride()
            )
        )
    }

    @Test
    fun arrivedServiceBeforeTimeoutStillKeepsServicesFabHidden() {
        assertFalse(
            CurrentServiceViewModel.shouldShowServicesFab(
                currentService = inProgressService(
                    createdAt = 900L,
                    metadata = ServiceMetadata(arrived_at = 950L)
                ),
                hasNextService = false,
                timeoutToConnectionSeconds = 120,
                rideElapsedSeconds = 0L,
                nowEpochSeconds = 1_000L,
                serviceStageOverride = CurrentServiceViewModel.ServiceStageOverride()
            )
        )
    }

    @Test
    fun startedTripBeforeTimeoutKeepsServicesFabHidden() {
        assertFalse(
            CurrentServiceViewModel.shouldShowServicesFab(
                currentService = inProgressService(
                    createdAt = 900L,
                    metadata = ServiceMetadata(arrived_at = 910L, start_trip_at = 920L)
                ),
                hasNextService = false,
                timeoutToConnectionSeconds = 120,
                rideElapsedSeconds = 119L,
                nowEpochSeconds = 5_000L,
                serviceStageOverride = CurrentServiceViewModel.ServiceStageOverride()
            )
        )
    }

    @Test
    fun startedTripAfterTimeoutShowsServicesFab() {
        assertTrue(
            CurrentServiceViewModel.shouldShowServicesFab(
                currentService = inProgressService(
                    createdAt = 900L,
                    metadata = ServiceMetadata(arrived_at = 910L, start_trip_at = 920L)
                ),
                hasNextService = false,
                timeoutToConnectionSeconds = 120,
                rideElapsedSeconds = 121L,
                nowEpochSeconds = 5_000L,
                serviceStageOverride = CurrentServiceViewModel.ServiceStageOverride()
            )
        )
    }

    @Test
    fun nextServiceKeepsServicesFabHidden() {
        assertFalse(
            CurrentServiceViewModel.shouldShowServicesFab(
                currentService = inProgressService(createdAt = 900L),
                hasNextService = true,
                timeoutToConnectionSeconds = 120,
                rideElapsedSeconds = 300L,
                nowEpochSeconds = 2_000L,
                serviceStageOverride = CurrentServiceViewModel.ServiceStageOverride()
            )
        )
    }

    @Test
    fun missingCurrentServiceKeepsServicesFabHidden() {
        assertFalse(
            CurrentServiceViewModel.shouldShowServicesFab(
                currentService = null,
                hasNextService = false,
                timeoutToConnectionSeconds = 120,
                rideElapsedSeconds = 300L,
                nowEpochSeconds = 2_000L,
                serviceStageOverride = CurrentServiceViewModel.ServiceStageOverride()
            )
        )
    }

    @Test
    fun optimisticStartedOverrideUsesRideElapsedInsteadOfCreatedAt() {
        assertFalse(
            CurrentServiceViewModel.shouldShowServicesFab(
                currentService = inProgressService(
                    createdAt = 100L,
                    metadata = ServiceMetadata(arrived_at = 150L)
                ),
                hasNextService = false,
                timeoutToConnectionSeconds = 120,
                rideElapsedSeconds = 60L,
                nowEpochSeconds = 1_000L,
                serviceStageOverride = CurrentServiceViewModel.ServiceStageOverride(
                    treatAsStarted = true
                )
            )
        )
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
    fun observedStartedTripAckSkipsSyncingForSameServiceRequest() {
        val request = CurrentServiceViewModel.StartTripRequest(
            serviceId = "service-1",
            startedAt = 100L,
            multiplier = 1.2,
            origin = "Origin"
        )

        assertTrue(
            CurrentServiceViewModel.hasObservedStartAck(
                currentService = Service(
                    id = "service-1",
                    status = Service.STATUS_IN_PROGRESS,
                    metadata = ServiceMetadata(arrived_at = 50L, start_trip_at = 70L)
                ),
                request = request
            )
        )
    }

    @Test
    fun missingObservedStartAckKeepsStartEligibleForSyncing() {
        val request = CurrentServiceViewModel.StartTripRequest(
            serviceId = "service-1",
            startedAt = 100L,
            multiplier = 1.2,
            origin = "Origin"
        )

        assertFalse(
            CurrentServiceViewModel.hasObservedStartAck(
                currentService = Service(
                    id = "service-1",
                    status = Service.STATUS_IN_PROGRESS,
                    metadata = ServiceMetadata(arrived_at = 50L)
                ),
                request = request
            )
        )
    }

    @Test
    fun observedStartedTripOnDifferentServiceDoesNotAckRequest() {
        val request = CurrentServiceViewModel.StartTripRequest(
            serviceId = "service-1",
            startedAt = 100L,
            multiplier = 1.2,
            origin = "Origin"
        )

        assertFalse(
            CurrentServiceViewModel.hasObservedStartAck(
                currentService = Service(
                    id = "service-2",
                    status = Service.STATUS_IN_PROGRESS,
                    metadata = ServiceMetadata(arrived_at = 50L, start_trip_at = 70L)
                ),
                request = request
            )
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
                actionId = "action-1",
                serviceId = "service-1",
                actionType = PendingServiceActionType.START,
                phase = PendingServiceActionPhase.IN_FLIGHT_RECOVERABLE,
                queuedAt = 100L,
                attemptCount = 1,
                optimisticApplied = false,
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
                hasTransportNetwork = true,
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
                actionId = "action-1",
                serviceId = "service-1",
                actionType = PendingServiceActionType.END,
                phase = PendingServiceActionPhase.FAILED,
                queuedAt = 100L,
                attemptCount = 1,
                optimisticApplied = false,
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
                hasTransportNetwork = false,
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
    fun optimisticPendingStartRestoresAsSyncingUntilObservedAck() {
        val viewModel = CurrentServiceViewModel(SavedStateHandle())
        val request = CurrentServiceViewModel.StartTripRequest(
            serviceId = "service-1",
            startedAt = 100L,
            multiplier = 1.2,
            origin = "Origin"
        )
        viewModel.restoreFromStoreIfNeeded(
            pendingActionSnapshot = PendingServiceActionSnapshot(
                actionId = "action-1",
                serviceId = "service-1",
                actionType = PendingServiceActionType.START,
                phase = PendingServiceActionPhase.SYNCING,
                queuedAt = 100L,
                attemptCount = 1,
                optimisticApplied = true,
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
                hasTransportNetwork = true,
                firebaseConnected = false,
                actualOnline = false
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionUiState.StartSyncing, viewModel.uiState.value)
        assertTrue(viewModel.hasPendingSyncAction())
    }

    @Test
    fun observedStartedTripClearsRestoredPendingStartSnapshot() {
        val viewModel = CurrentServiceViewModel(SavedStateHandle())
        viewModel.restoreFromStoreIfNeeded(
            pendingActionSnapshot = PendingServiceActionSnapshot(
                actionId = "action-1",
                serviceId = "service-1",
                actionType = PendingServiceActionType.START,
                phase = PendingServiceActionPhase.BLOCKED_BY_CONNECTION,
                queuedAt = 100L,
                attemptCount = 1,
                optimisticApplied = false,
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
                hasTransportNetwork = true,
                firebaseConnected = true,
                actualOnline = true
            )
        )

        assertEquals(CurrentServiceViewModel.ServiceActionUiState.Idle, viewModel.uiState.value)
        assertNull(viewModel.getPendingActionSnapshot())
        assertNull(viewModel.getStartTripRequest())
    }

    private fun inProgressService(
        createdAt: Long,
        metadata: ServiceMetadata = ServiceMetadata()
    ): Service {
        return Service(
            id = "service-1",
            status = Service.STATUS_IN_PROGRESS,
            created_at = createdAt,
            metadata = metadata
        )
    }
}
