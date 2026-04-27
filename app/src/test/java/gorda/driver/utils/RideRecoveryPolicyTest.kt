package gorda.driver.utils

import gorda.driver.interfaces.RideFees
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import gorda.driver.ui.service.current.CurrentServiceViewModel
import gorda.driver.ui.service.current.PendingServiceActionPhase
import gorda.driver.ui.service.current.PendingServiceActionSnapshot
import gorda.driver.ui.service.current.PendingServiceActionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RideRecoveryPolicyTest {

    @Test
    fun matchingServiceIdAllowsRecoveryPrompt() {
        assertTrue(
            RideRecoveryPolicy.shouldOfferRecovery(
                hasStartedTrip = true,
                isServiceRunning = false,
                isStartingFreshTransition = false,
                storedServiceId = "service-1",
                currentServiceId = "service-1"
            )
        )
    }

    @Test
    fun mismatchedServiceIdDoesNotAllowRecoveryPromptAndMarksStateAsStale() {
        assertFalse(
            RideRecoveryPolicy.shouldOfferRecovery(
                hasStartedTrip = true,
                isServiceRunning = false,
                isStartingFreshTransition = false,
                storedServiceId = "service-1",
                currentServiceId = "service-2"
            )
        )
        assertTrue(
            RideRecoveryPolicy.shouldClearStaleRecovery(
                storedServiceId = "service-1",
                currentServiceId = "service-2"
            )
        )
    }

    @Test
    fun intentionalFreshStartSuppressesRecoveryPrompt() {
        assertFalse(
            RideRecoveryPolicy.shouldOfferRecovery(
                hasStartedTrip = true,
                isServiceRunning = false,
                isStartingFreshTransition = true,
                storedServiceId = "service-1",
                currentServiceId = "service-1"
            )
        )
    }

    @Test
    fun emptyFareSnapshotDoesNotReplaceStoredSnapshot() {
        val storedFees = RideFees(priceKm = 3200.0, priceMin = 250.0, feesBase = 5000.0)

        assertEquals(
            storedFees,
            RideRecoveryPolicy.selectRideFeesSnapshotForPersistence(
                candidate = RideFees(),
                stored = storedFees
            )
        )
    }

    @Test
    fun missingCurrentServiceDoesNotClearRecoveryDuringStartup() {
        assertFalse(RideRecoveryPolicy.shouldClearRecoveryForObservedService(status = null))
        assertFalse(RideRecoveryPolicy.shouldClearRecoveryForObservedService(Service.STATUS_IN_PROGRESS))
        assertTrue(RideRecoveryPolicy.shouldClearRecoveryForObservedService(Service.STATUS_TERMINATED))
    }

    @Test
    fun pendingStartSnapshotClearsWhenBackendAlreadyShowsStartedTrip() {
        val reconciliation = RideRecoveryPolicy.reconcilePendingActionSnapshot(
            snapshot = PendingServiceActionSnapshot(
                actionId = "action-1",
                serviceId = "service-1",
                actionType = PendingServiceActionType.START,
                phase = PendingServiceActionPhase.IN_FLIGHT_RECOVERABLE,
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
            observedService = Service(
                id = "service-1",
                status = Service.STATUS_IN_PROGRESS,
                metadata = ServiceMetadata(arrived_at = 90L, start_trip_at = 100L)
            ),
            connectionReady = true
        )

        assertTrue(reconciliation is RideRecoveryPolicy.PendingActionReconciliation.Clear)
    }

    @Test
    fun pendingEndSnapshotClearsWhenObservedServiceIsNoLongerActive() {
        val reconciliation = RideRecoveryPolicy.reconcilePendingActionSnapshot(
            snapshot = PendingServiceActionSnapshot(
                actionId = "action-2",
                serviceId = "service-1",
                actionType = PendingServiceActionType.END,
                phase = PendingServiceActionPhase.FAILED,
                queuedAt = 100L,
                attemptCount = 1,
                optimisticApplied = false,
                endRequest = CurrentServiceViewModel.EndTripRequest(
                    serviceId = "service-1",
                    endedAt = 100L,
                    route = "{}",
                    tripDistance = 2000,
                    tripFee = 5000,
                    multiplier = 1.0
                )
            ),
            observedService = Service(
                id = "service-1",
                status = Service.STATUS_TERMINATED,
                metadata = ServiceMetadata(arrived_at = 90L, start_trip_at = 95L, end_trip_at = 100L)
            ),
            connectionReady = true
        )

        assertTrue(reconciliation is RideRecoveryPolicy.PendingActionReconciliation.Clear)
    }

    @Test
    fun pendingActionRestoresAsBlockedWhenConnectionIsNotReady() {
        val reconciliation = RideRecoveryPolicy.reconcilePendingActionSnapshot(
            snapshot = PendingServiceActionSnapshot(
                actionId = "action-3",
                serviceId = "service-1",
                actionType = PendingServiceActionType.END,
                phase = PendingServiceActionPhase.IN_FLIGHT_RECOVERABLE,
                queuedAt = 100L,
                attemptCount = 1,
                optimisticApplied = false,
                endRequest = CurrentServiceViewModel.EndTripRequest(
                    serviceId = "service-1",
                    endedAt = 100L,
                    route = "{}",
                    tripDistance = 2000,
                    tripFee = 5000,
                    multiplier = 1.0
                )
            ),
            observedService = Service(
                id = "service-1",
                status = Service.STATUS_IN_PROGRESS,
                metadata = ServiceMetadata(arrived_at = 90L, start_trip_at = 95L)
            ),
            connectionReady = false
        )

        assertTrue(reconciliation is RideRecoveryPolicy.PendingActionReconciliation.Restore)
        reconciliation as RideRecoveryPolicy.PendingActionReconciliation.Restore
        assertEquals(RideRecoveryPolicy.RestoredActionRenderMode.BLOCKED, reconciliation.renderMode)
    }
}
