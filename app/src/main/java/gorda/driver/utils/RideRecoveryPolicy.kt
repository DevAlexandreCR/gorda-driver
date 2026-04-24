package gorda.driver.utils

import gorda.driver.interfaces.RideFees
import gorda.driver.models.Service

object RideRecoveryPolicy {

    fun shouldOfferRecovery(
        hasStartedTrip: Boolean,
        isServiceRunning: Boolean,
        isStartingFreshTransition: Boolean,
        storedServiceId: String?,
        currentServiceId: String
    ): Boolean {
        return hasStartedTrip &&
            !isServiceRunning &&
            !isStartingFreshTransition &&
            storedServiceId == currentServiceId
    }

    fun shouldClearStaleRecovery(
        storedServiceId: String?,
        currentServiceId: String
    ): Boolean {
        return !storedServiceId.isNullOrBlank() && storedServiceId != currentServiceId
    }

    fun shouldClearRecoveryForObservedService(status: String?): Boolean {
        return status != null && status != Service.STATUS_IN_PROGRESS
    }

    fun isValidRideFeesSnapshot(rideFees: RideFees?): Boolean {
        return rideFees != null && rideFees != RideFees()
    }

    fun selectRideFeesSnapshotForPersistence(
        candidate: RideFees?,
        stored: RideFees?
    ): RideFees? {
        return when {
            isValidRideFeesSnapshot(candidate) -> candidate
            isValidRideFeesSnapshot(stored) -> stored
            else -> null
        }
    }
}
