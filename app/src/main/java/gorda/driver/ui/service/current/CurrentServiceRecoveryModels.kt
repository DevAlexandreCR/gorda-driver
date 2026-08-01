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

enum class SelfServiceTerminalStatus : Serializable {
    TERMINATED,
    CANCELED
}

/**
 * Terminal data for a self-service trip that ended or was canceled before its creation synced
 * (add-driver-self-service design D5, steps 2-3). Set on [SelfServiceProvisionalTrip.terminal] by
 * `MainViewModel.endUnsyncedSelfServiceTrip`/`cancelUnsyncedSelfServiceTrip` the moment the driver
 * acts locally; [SelfServiceTripManager.trySync] then sends a single deferred payload carrying
 * both the creation and this terminal data instead of the online-mode create request. [endedAt],
 * [route], [tripDistance], and [tripFee] are only meaningful for [SelfServiceTerminalStatus.TERMINATED]
 * — a cancellation carries no terminal metrics, mirroring the API contract.
 */
data class SelfServiceTerminalData(
    val status: SelfServiceTerminalStatus,
    val endedAt: Long? = null,
    val route: String? = null,
    val tripDistance: Int? = null,
    val tripFee: Int? = null
) : Serializable

/**
 * A self-service trip started locally before the creation request synced with the API
 * (add-driver-self-service design D5, step 1). [localId] is the client-generated id `FeesService`
 * meters against until the real service id arrives; [gpsLat]/[gpsLng] are nullable because a GPS
 * fix may not be available yet at confirm time (design D3) — the sync is deferred, not the trip
 * start, until a fix shows up (see `MainViewModel.updateLocation`). [terminal] is non-null once the
 * trip ended or was canceled locally before syncing (design D5, steps 2-3, task 4.4) — its presence
 * is what makes [SelfServiceTripManager.trySync] send a deferred payload instead of an online create.
 */
data class SelfServiceProvisionalTrip(
    val localId: String,
    val startedAt: Long,
    val multiplier: Double,
    val gpsLat: Double? = null,
    val gpsLng: Double? = null,
    val terminal: SelfServiceTerminalData? = null
) : Serializable
