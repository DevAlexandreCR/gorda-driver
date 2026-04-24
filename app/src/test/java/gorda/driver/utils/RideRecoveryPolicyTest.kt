package gorda.driver.utils

import gorda.driver.interfaces.RideFees
import gorda.driver.models.Service
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
}
