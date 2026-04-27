package gorda.driver.ui.service.current

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import gorda.driver.R
import gorda.driver.interfaces.RideFees
import gorda.driver.ui.MainViewModel
import gorda.driver.utils.RideRecoveryPolicy
import java.io.Serializable

class CurrentServiceViewModel(
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    enum class ServiceActionConnectionStatus {
        OFFLINE,
        FIREBASE_DISCONNECTED,
        RECONNECTING,
        READY
    }

    enum class StartRideFeesSource {
        LIVE,
        FALLBACK,
        UNAVAILABLE
    }

    enum class EndTripLocalOutcome {
        FINISH_LOCALLY,
        KEEP_ACTIVE_RETRYABLE
    }

    data class StartTripRequest(
        val serviceId: String,
        val startedAt: Long,
        val multiplier: Double,
        val origin: String
    ) : Serializable

    data class EndTripRequest(
        val serviceId: String,
        val endedAt: Long,
        val route: String,
        val tripDistance: Int,
        val tripFee: Int,
        val multiplier: Double
    ) : Serializable

    data class StartRideFeesResolution(
        val fees: RideFees?,
        val source: StartRideFeesSource
    )

    sealed class ServiceActionUiState {
        object Idle : ServiceActionUiState()

        object PreparingStart : ServiceActionUiState()

        object BlockedStartByConnection : ServiceActionUiState()

        object StartingTrip : ServiceActionUiState()

        data class StartFailed(
            @StringRes val messageRes: Int,
            val canRetry: Boolean
        ) : ServiceActionUiState()

        object PreparingEnd : ServiceActionUiState()

        object BlockedEndByConnection : ServiceActionUiState()

        object EndingTrip : ServiceActionUiState()

        data class EndFailed(
            @StringRes val messageRes: Int,
            val canRetry: Boolean
        ) : ServiceActionUiState()
    }

    companion object {
        private const val KEY_PENDING_ACTION_SNAPSHOT = "current_service_pending_action_snapshot"
        private const val KEY_CURRENT_SERVICE_UI_SNAPSHOT = "current_service_ui_snapshot"
        private const val KEY_BOTTOM_SHEET_SNAPSHOT = "current_service_bottom_sheet_snapshot"

        fun connectionStatusForServiceAction(
            state: MainViewModel.DriverPresenceState
        ): ServiceActionConnectionStatus {
            return when {
                !state.hasNetwork -> ServiceActionConnectionStatus.OFFLINE
                !state.firebaseConnected -> ServiceActionConnectionStatus.FIREBASE_DISCONNECTED
                !state.actualOnline -> ServiceActionConnectionStatus.RECONNECTING
                else -> ServiceActionConnectionStatus.READY
            }
        }

        fun isReadyForServiceAction(
            state: MainViewModel.DriverPresenceState
        ): Boolean {
            return connectionStatusForServiceAction(state) == ServiceActionConnectionStatus.READY
        }

        fun resolveStartRideFees(
            liveFees: RideFees?,
            inMemoryFees: RideFees,
            storedFees: RideFees?,
            currentMultiplier: Double?,
            storedMultiplier: Double?
        ): StartRideFeesResolution {
            if (liveFees != null && liveFees != RideFees()) {
                return StartRideFeesResolution(
                    fees = liveFees,
                    source = StartRideFeesSource.LIVE
                )
            }

            val inMemoryCandidate = if (inMemoryFees != RideFees()) {
                inMemoryFees.copy(feeMultiplier = currentMultiplier ?: inMemoryFees.feeMultiplier)
            } else {
                null
            }

            if (inMemoryCandidate != null) {
                return StartRideFeesResolution(
                    fees = inMemoryCandidate,
                    source = StartRideFeesSource.FALLBACK
                )
            }

            val storedCandidate = storedFees?.copy(
                feeMultiplier = storedMultiplier ?: storedFees.feeMultiplier
            )

            return StartRideFeesResolution(
                fees = storedCandidate,
                source = if (storedCandidate != null) {
                    StartRideFeesSource.FALLBACK
                } else {
                    StartRideFeesSource.UNAVAILABLE
                }
            )
        }

        fun endTripOutcome(writeSucceeded: Boolean): EndTripLocalOutcome {
            return if (writeSucceeded) {
                EndTripLocalOutcome.FINISH_LOCALLY
            } else {
                EndTripLocalOutcome.KEEP_ACTIVE_RETRYABLE
            }
        }
    }

    private val _uiState = MutableLiveData<ServiceActionUiState>(ServiceActionUiState.Idle)
    val uiState: LiveData<ServiceActionUiState> = _uiState

    private var nextAttemptId: Long = 0L
    private var activeAttemptId: Long = 0L
    private var pendingActionSnapshot: PendingServiceActionSnapshot? =
        savedStateHandle[KEY_PENDING_ACTION_SNAPSHOT]
    private var currentServiceUiSnapshot: CurrentServiceUiSnapshot? =
        savedStateHandle[KEY_CURRENT_SERVICE_UI_SNAPSHOT]
    private var bottomSheetPresentationSnapshot: BottomSheetPresentationSnapshot? =
        savedStateHandle[KEY_BOTTOM_SHEET_SNAPSHOT]
    private var startTripRequest: StartTripRequest? = pendingActionSnapshot?.startRequest
    private var endTripRequest: EndTripRequest? = pendingActionSnapshot?.endRequest

    fun newAttempt(): Long {
        nextAttemptId += 1
        activeAttemptId = nextAttemptId
        return activeAttemptId
    }

    fun isActiveAttempt(attemptId: Long): Boolean {
        return activeAttemptId == attemptId
    }

    fun showIdle() {
        clearPendingActionSnapshot()
        _uiState.value = ServiceActionUiState.Idle
    }

    fun showPreparingStart() {
        _uiState.value = ServiceActionUiState.PreparingStart
    }

    fun showBlockedStartByConnection() {
        persistRecoverableStartSnapshot(PendingServiceActionPhase.BLOCKED_BY_CONNECTION)
        _uiState.value = ServiceActionUiState.BlockedStartByConnection
    }

    fun showStartingTrip() {
        persistRecoverableStartSnapshot(PendingServiceActionPhase.IN_FLIGHT_RECOVERABLE)
        _uiState.value = ServiceActionUiState.StartingTrip
    }

    fun showStartFailed(@StringRes messageRes: Int, canRetry: Boolean) {
        if (canRetry) {
            persistRecoverableStartSnapshot(
                phase = PendingServiceActionPhase.FAILED,
                failureMessageRes = messageRes
            )
        } else {
            clearPendingActionSnapshot()
        }
        _uiState.value = ServiceActionUiState.StartFailed(messageRes, canRetry)
    }

    fun showPreparingEnd() {
        _uiState.value = ServiceActionUiState.PreparingEnd
    }

    fun showBlockedEndByConnection() {
        persistRecoverableEndSnapshot(PendingServiceActionPhase.BLOCKED_BY_CONNECTION)
        _uiState.value = ServiceActionUiState.BlockedEndByConnection
    }

    fun showEndingTrip() {
        persistRecoverableEndSnapshot(PendingServiceActionPhase.IN_FLIGHT_RECOVERABLE)
        _uiState.value = ServiceActionUiState.EndingTrip
    }

    fun showEndFailed(@StringRes messageRes: Int, canRetry: Boolean) {
        if (canRetry) {
            persistRecoverableEndSnapshot(
                phase = PendingServiceActionPhase.FAILED,
                failureMessageRes = messageRes
            )
        } else {
            clearPendingActionSnapshot()
        }
        _uiState.value = ServiceActionUiState.EndFailed(messageRes, canRetry)
    }

    fun rememberStartTripRequest(request: StartTripRequest) {
        startTripRequest = request
        endTripRequest = null
    }

    fun rememberEndTripRequest(request: EndTripRequest) {
        endTripRequest = request
    }

    fun getStartTripRequest(): StartTripRequest? {
        return startTripRequest
    }

    fun getEndTripRequest(): EndTripRequest? {
        return endTripRequest
    }

    fun clearStartTripRequest() {
        startTripRequest = null
        if (pendingActionSnapshot?.actionType == PendingServiceActionType.START) {
            clearPendingActionSnapshot()
        }
    }

    fun clearEndTripRequest() {
        endTripRequest = null
        if (pendingActionSnapshot?.actionType == PendingServiceActionType.END) {
            clearPendingActionSnapshot()
        }
    }

    fun onTripStartedObserved() {
        clearStartTripRequest()
        if (
            _uiState.value == ServiceActionUiState.PreparingStart ||
            _uiState.value == ServiceActionUiState.BlockedStartByConnection ||
            _uiState.value == ServiceActionUiState.StartingTrip ||
            _uiState.value is ServiceActionUiState.StartFailed
        ) {
            showIdle()
        }
    }

    fun onTripEndedObserved() {
        clearEndTripRequest()
        if (
            _uiState.value == ServiceActionUiState.PreparingEnd ||
            _uiState.value == ServiceActionUiState.BlockedEndByConnection ||
            _uiState.value == ServiceActionUiState.EndingTrip ||
            _uiState.value is ServiceActionUiState.EndFailed
        ) {
            showIdle()
        }
    }

    fun getPendingActionSnapshot(): PendingServiceActionSnapshot? {
        return pendingActionSnapshot
    }

    fun hasRestorableState(): Boolean {
        return pendingActionSnapshot != null ||
            currentServiceUiSnapshot != null ||
            bottomSheetPresentationSnapshot != null
    }

    fun restoreFromStoreIfNeeded(
        pendingActionSnapshot: PendingServiceActionSnapshot?,
        currentServiceUiSnapshot: CurrentServiceUiSnapshot?,
        bottomSheetSnapshot: BottomSheetPresentationSnapshot?
    ) {
        if (this.pendingActionSnapshot == null && pendingActionSnapshot != null) {
            this.pendingActionSnapshot = pendingActionSnapshot
            savedStateHandle[KEY_PENDING_ACTION_SNAPSHOT] = pendingActionSnapshot
            startTripRequest = pendingActionSnapshot.startRequest
            endTripRequest = pendingActionSnapshot.endRequest
        }

        if (this.currentServiceUiSnapshot == null && currentServiceUiSnapshot != null) {
            this.currentServiceUiSnapshot = currentServiceUiSnapshot
            savedStateHandle[KEY_CURRENT_SERVICE_UI_SNAPSHOT] = currentServiceUiSnapshot
        }

        if (this.bottomSheetPresentationSnapshot == null && bottomSheetSnapshot != null) {
            this.bottomSheetPresentationSnapshot = bottomSheetSnapshot
            savedStateHandle[KEY_BOTTOM_SHEET_SNAPSHOT] = bottomSheetSnapshot
        }
    }

    fun getCurrentServiceUiSnapshot(): CurrentServiceUiSnapshot? {
        return currentServiceUiSnapshot
    }

    fun getBottomSheetPresentationSnapshot(): BottomSheetPresentationSnapshot? {
        return bottomSheetPresentationSnapshot
    }

    fun updateFeeDetailsExpanded(serviceId: String, isExpanded: Boolean) {
        currentServiceUiSnapshot = CurrentServiceUiSnapshot(
            serviceId = serviceId,
            isFeeDetailsExpanded = isExpanded
        )
        savedStateHandle[KEY_CURRENT_SERVICE_UI_SNAPSHOT] = currentServiceUiSnapshot
    }

    fun updateBottomSheetExpanded(serviceId: String, isExpanded: Boolean) {
        bottomSheetPresentationSnapshot = BottomSheetPresentationSnapshot(
            serviceId = serviceId,
            isExpanded = isExpanded
        )
        savedStateHandle[KEY_BOTTOM_SHEET_SNAPSHOT] = bottomSheetPresentationSnapshot
    }

    fun clearServiceScopedSnapshots() {
        clearPendingActionSnapshot()
        currentServiceUiSnapshot = null
        bottomSheetPresentationSnapshot = null
        savedStateHandle.remove<CurrentServiceUiSnapshot>(KEY_CURRENT_SERVICE_UI_SNAPSHOT)
        savedStateHandle.remove<BottomSheetPresentationSnapshot>(KEY_BOTTOM_SHEET_SNAPSHOT)
    }

    fun discardStaleStateForService(serviceId: String) {
        if (pendingActionSnapshot?.serviceId != null &&
            pendingActionSnapshot?.serviceId != serviceId
        ) {
            clearPendingActionSnapshot()
            _uiState.value = ServiceActionUiState.Idle
        }

        if (currentServiceUiSnapshot?.serviceId != null &&
            currentServiceUiSnapshot?.serviceId != serviceId
        ) {
            currentServiceUiSnapshot = null
            savedStateHandle.remove<CurrentServiceUiSnapshot>(KEY_CURRENT_SERVICE_UI_SNAPSHOT)
        }

        if (bottomSheetPresentationSnapshot?.serviceId != null &&
            bottomSheetPresentationSnapshot?.serviceId != serviceId
        ) {
            bottomSheetPresentationSnapshot = null
            savedStateHandle.remove<BottomSheetPresentationSnapshot>(KEY_BOTTOM_SHEET_SNAPSHOT)
        }
    }

    fun reconcileRestoredPendingAction(
        service: gorda.driver.models.Service,
        presenceState: MainViewModel.DriverPresenceState
    ): RideRecoveryPolicy.PendingActionReconciliation? {
        val snapshot = pendingActionSnapshot ?: return null
        val reconciliation = RideRecoveryPolicy.reconcilePendingActionSnapshot(
            snapshot = snapshot,
            observedService = service,
            connectionReady = isReadyForServiceAction(presenceState)
        )

        when (reconciliation) {
            is RideRecoveryPolicy.PendingActionReconciliation.Clear -> {
                clearPendingActionSnapshot()
                _uiState.value = ServiceActionUiState.Idle
            }
            is RideRecoveryPolicy.PendingActionReconciliation.Restore -> {
                startTripRequest = reconciliation.snapshot.startRequest
                endTripRequest = reconciliation.snapshot.endRequest
                when (reconciliation.snapshot.actionType) {
                    PendingServiceActionType.START -> {
                        when (reconciliation.renderMode) {
                            RideRecoveryPolicy.RestoredActionRenderMode.BLOCKED -> {
                                pendingActionSnapshot = reconciliation.snapshot.copy(
                                    phase = PendingServiceActionPhase.BLOCKED_BY_CONNECTION
                                )
                                savedStateHandle[KEY_PENDING_ACTION_SNAPSHOT] = pendingActionSnapshot
                                _uiState.value = ServiceActionUiState.BlockedStartByConnection
                            }
                            RideRecoveryPolicy.RestoredActionRenderMode.RETRYABLE_FAILURE -> {
                                pendingActionSnapshot = reconciliation.snapshot.copy(
                                    phase = PendingServiceActionPhase.FAILED,
                                    failureMessageRes = reconciliation.snapshot.failureMessageRes
                                        ?: R.string.common_error
                                )
                                savedStateHandle[KEY_PENDING_ACTION_SNAPSHOT] = pendingActionSnapshot
                                _uiState.value = ServiceActionUiState.StartFailed(
                                    pendingActionSnapshot?.failureMessageRes ?: R.string.common_error,
                                    true
                                )
                            }
                        }
                    }
                    PendingServiceActionType.END -> {
                        when (reconciliation.renderMode) {
                            RideRecoveryPolicy.RestoredActionRenderMode.BLOCKED -> {
                                pendingActionSnapshot = reconciliation.snapshot.copy(
                                    phase = PendingServiceActionPhase.BLOCKED_BY_CONNECTION
                                )
                                savedStateHandle[KEY_PENDING_ACTION_SNAPSHOT] = pendingActionSnapshot
                                _uiState.value = ServiceActionUiState.BlockedEndByConnection
                            }
                            RideRecoveryPolicy.RestoredActionRenderMode.RETRYABLE_FAILURE -> {
                                pendingActionSnapshot = reconciliation.snapshot.copy(
                                    phase = PendingServiceActionPhase.FAILED,
                                    failureMessageRes = reconciliation.snapshot.failureMessageRes
                                        ?: R.string.common_error
                                )
                                savedStateHandle[KEY_PENDING_ACTION_SNAPSHOT] = pendingActionSnapshot
                                _uiState.value = ServiceActionUiState.EndFailed(
                                    pendingActionSnapshot?.failureMessageRes ?: R.string.common_error,
                                    true
                                )
                            }
                        }
                    }
                }
            }
        }

        return reconciliation
    }

    fun reset() {
        clearStartTripRequest()
        clearEndTripRequest()
        clearServiceScopedSnapshots()
        _uiState.value = ServiceActionUiState.Idle
    }

    private fun clearPendingActionSnapshot() {
        pendingActionSnapshot = null
        savedStateHandle.remove<PendingServiceActionSnapshot>(KEY_PENDING_ACTION_SNAPSHOT)
    }

    private fun persistRecoverableStartSnapshot(
        phase: PendingServiceActionPhase,
        @StringRes failureMessageRes: Int? = null
    ) {
        val request = startTripRequest ?: return
        pendingActionSnapshot = PendingServiceActionSnapshot(
            serviceId = request.serviceId,
            actionType = PendingServiceActionType.START,
            phase = phase,
            failureMessageRes = failureMessageRes,
            startRequest = request
        )
        savedStateHandle[KEY_PENDING_ACTION_SNAPSHOT] = pendingActionSnapshot
    }

    private fun persistRecoverableEndSnapshot(
        phase: PendingServiceActionPhase,
        @StringRes failureMessageRes: Int? = null
    ) {
        val request = endTripRequest ?: return
        pendingActionSnapshot = PendingServiceActionSnapshot(
            serviceId = request.serviceId,
            actionType = PendingServiceActionType.END,
            phase = phase,
            failureMessageRes = failureMessageRes,
            endRequest = request
        )
        savedStateHandle[KEY_PENDING_ACTION_SNAPSHOT] = pendingActionSnapshot
    }
}
