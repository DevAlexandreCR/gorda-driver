package gorda.driver.utils

import gorda.driver.R
import gorda.driver.interfaces.RideFees
import gorda.driver.models.Service
import gorda.driver.ui.service.current.PendingActionAckState
import gorda.driver.ui.service.current.PendingServiceActionFingerprint
import gorda.driver.ui.service.current.PendingServiceActionPhase
import gorda.driver.ui.service.current.PendingServiceActionSnapshot
import gorda.driver.ui.service.current.PendingServiceActionType

object RideRecoveryPolicy {

    enum class RestoredActionRenderMode {
        BLOCKED,
        RETRYABLE_FAILURE,
        SYNCING,
        CONFLICT
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

    fun fingerprintFor(service: Service): PendingServiceActionFingerprint {
        return PendingServiceActionFingerprint(
            expectedStatus = service.status,
            expectedDriverId = service.driver_id,
            hadArrivedAt = service.metadata.arrived_at != null,
            hadStartedAt = service.metadata.start_trip_at != null,
            hadEndedAt = service.metadata.end_trip_at != null
        )
    }

    fun pendingActionAckState(
        snapshot: PendingServiceActionSnapshot,
        observedService: Service
    ): PendingActionAckState {
        if (shouldClearStaleRecovery(snapshot.serviceId, observedService.id)) {
            return PendingActionAckState(
                isConfirmed = false,
                conflictMessageRes = R.string.service_not_available
            )
        }

        return when (snapshot.actionType) {
            PendingServiceActionType.START -> {
                when {
                    observedService.metadata.start_trip_at != null -> PendingActionAckState(isConfirmed = true)
                    observedService.status == Service.STATUS_TERMINATED -> PendingActionAckState(
                        isConfirmed = false,
                        conflictMessageRes = R.string.service_not_available
                    )
                    snapshot.fingerprint?.expectedDriverId != null &&
                        observedService.driver_id != snapshot.fingerprint.expectedDriverId -> {
                        PendingActionAckState(
                            isConfirmed = false,
                            conflictMessageRes = R.string.service_not_available
                        )
                    }
                    else -> PendingActionAckState(isConfirmed = false)
                }
            }
            PendingServiceActionType.END -> {
                when {
                    observedService.metadata.end_trip_at != null ||
                        observedService.status == Service.STATUS_TERMINATED -> {
                        PendingActionAckState(isConfirmed = true)
                    }
                    observedService.metadata.start_trip_at == null -> PendingActionAckState(
                        isConfirmed = false,
                        conflictMessageRes = R.string.service_trip_not_started
                    )
                    snapshot.fingerprint?.expectedDriverId != null &&
                        observedService.driver_id != snapshot.fingerprint.expectedDriverId -> {
                        PendingActionAckState(
                            isConfirmed = false,
                            conflictMessageRes = R.string.service_not_available
                        )
                    }
                    else -> PendingActionAckState(isConfirmed = false)
                }
            }
        }
    }

    fun reconcilePendingActionSnapshot(
        snapshot: PendingServiceActionSnapshot,
        observedService: Service,
        connectionReady: Boolean
    ): PendingActionReconciliation {
        if (shouldClearStaleRecovery(snapshot.serviceId, observedService.id)) {
            return PendingActionReconciliation.Clear(snapshot)
        }

        val ackState = pendingActionAckState(snapshot, observedService)
        if (ackState.isConfirmed) {
            return PendingActionReconciliation.Clear(snapshot)
        }

        if (ackState.conflictMessageRes != null) {
            return PendingActionReconciliation.Restore(
                snapshot = snapshot.copy(
                    phase = PendingServiceActionPhase.CONFLICT,
                    failureMessageRes = ackState.conflictMessageRes
                ),
                renderMode = RestoredActionRenderMode.CONFLICT
            )
        }

        val stageStillCompatible = when (snapshot.actionType) {
            PendingServiceActionType.START -> observedService.metadata.arrived_at != null
            PendingServiceActionType.END -> observedService.metadata.start_trip_at != null
        }

        if (!stageStillCompatible) {
            return PendingActionReconciliation.Restore(
                snapshot = snapshot.copy(
                    phase = PendingServiceActionPhase.CONFLICT,
                    failureMessageRes = when (snapshot.actionType) {
                        PendingServiceActionType.START -> R.string.service_not_available
                        PendingServiceActionType.END -> R.string.service_trip_not_started
                    }
                ),
                renderMode = RestoredActionRenderMode.CONFLICT
            )
        }

        if (snapshot.optimisticApplied) {
            return PendingActionReconciliation.Restore(
                snapshot = snapshot.copy(phase = PendingServiceActionPhase.SYNCING),
                renderMode = RestoredActionRenderMode.SYNCING
            )
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
