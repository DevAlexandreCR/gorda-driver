package gorda.driver.ui.service.current

import java.io.Serializable

enum class PendingServiceActionType : Serializable {
    START,
    END
}

enum class PendingServiceActionPhase : Serializable {
    BLOCKED_BY_CONNECTION,
    FAILED,
    IN_FLIGHT_RECOVERABLE,
    SYNCING,
    CONFLICT
}

data class PendingServiceActionFingerprint(
    val expectedStatus: String,
    val expectedDriverId: String?,
    val hadArrivedAt: Boolean,
    val hadStartedAt: Boolean,
    val hadEndedAt: Boolean
) : Serializable

data class PendingActionAckState(
    val isConfirmed: Boolean,
    val conflictMessageRes: Int? = null
) : Serializable

data class PendingServiceActionSnapshot(
    val actionId: String,
    val serviceId: String,
    val actionType: PendingServiceActionType,
    val phase: PendingServiceActionPhase,
    val queuedAt: Long,
    val attemptCount: Int,
    val optimisticApplied: Boolean,
    val fingerprint: PendingServiceActionFingerprint? = null,
    val failureMessageRes: Int? = null,
    val startRequest: CurrentServiceViewModel.StartTripRequest? = null,
    val endRequest: CurrentServiceViewModel.EndTripRequest? = null
) : Serializable

data class CurrentServiceUiSnapshot(
    val serviceId: String,
    val isFeeDetailsExpanded: Boolean
) : Serializable

data class BottomSheetPresentationSnapshot(
    val serviceId: String,
    val isExpanded: Boolean
) : Serializable
