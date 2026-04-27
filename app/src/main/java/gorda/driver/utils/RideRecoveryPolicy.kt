package gorda.driver.utils

import gorda.driver.interfaces.RideFees
import gorda.driver.models.Service
import gorda.driver.ui.service.current.PendingServiceActionSnapshot
import gorda.driver.ui.service.current.PendingServiceActionType

object RideRecoveryPolicy {

    enum class RestoredActionRenderMode {
        BLOCKED,
        RETRYABLE_FAILURE
    }

    sealed class PendingActionReconciliation {
        data class Clear(val snapshot: PendingServiceActionSnapshot) : PendingActionReconciliation()

        data class Restore(
            val snapshot: PendingServiceActionSnapshot,
            val renderMode: RestoredActionRenderMode
        ) : PendingActionReconciliation()
    }

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

    fun reconcilePendingActionSnapshot(
        snapshot: PendingServiceActionSnapshot,
        observedService: Service,
        connectionReady: Boolean
    ): PendingActionReconciliation {
        if (shouldClearStaleRecovery(snapshot.serviceId, observedService.id)) {
            return PendingActionReconciliation.Clear(snapshot)
        }

        if (!observedService.isInProgress()) {
            return PendingActionReconciliation.Clear(snapshot)
        }

        val stageStillCompatible = when (snapshot.actionType) {
            PendingServiceActionType.START -> {
                if (observedService.metadata.start_trip_at != null) {
                    return PendingActionReconciliation.Clear(snapshot)
                }
                observedService.metadata.arrived_at != null
            }
            PendingServiceActionType.END -> {
                if (observedService.metadata.end_trip_at != null) {
                    return PendingActionReconciliation.Clear(snapshot)
                }
                observedService.metadata.start_trip_at != null
            }
        }

        if (!stageStillCompatible) {
            return PendingActionReconciliation.Clear(snapshot)
        }

        return PendingActionReconciliation.Restore(
            snapshot = snapshot,
            renderMode = if (connectionReady) {
                RestoredActionRenderMode.RETRYABLE_FAILURE
            } else {
                RestoredActionRenderMode.BLOCKED
            }
        )
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
