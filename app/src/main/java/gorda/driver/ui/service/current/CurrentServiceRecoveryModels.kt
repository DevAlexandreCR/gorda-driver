package gorda.driver.ui.service.current

import java.io.Serializable

enum class PendingServiceActionType : Serializable {
    START,
    END
}

enum class PendingServiceActionPhase : Serializable {
    BLOCKED_BY_CONNECTION,
    FAILED,
    IN_FLIGHT_RECOVERABLE
}

data class PendingServiceActionSnapshot(
    val serviceId: String,
    val actionType: PendingServiceActionType,
    val phase: PendingServiceActionPhase,
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
