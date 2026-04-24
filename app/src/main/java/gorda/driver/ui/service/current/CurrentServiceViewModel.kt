package gorda.driver.ui.service.current

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import gorda.driver.interfaces.RideFees
import gorda.driver.ui.MainViewModel

class CurrentServiceViewModel : ViewModel() {

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
    )

    data class EndTripRequest(
        val serviceId: String,
        val endedAt: Long,
        val route: String,
        val tripDistance: Int,
        val tripFee: Int,
        val multiplier: Double
    )

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
    private var startTripRequest: StartTripRequest? = null
    private var endTripRequest: EndTripRequest? = null

    fun newAttempt(): Long {
        nextAttemptId += 1
        activeAttemptId = nextAttemptId
        return activeAttemptId
    }

    fun isActiveAttempt(attemptId: Long): Boolean {
        return activeAttemptId == attemptId
    }

    fun showIdle() {
        _uiState.value = ServiceActionUiState.Idle
    }

    fun showPreparingStart() {
        _uiState.value = ServiceActionUiState.PreparingStart
    }

    fun showBlockedStartByConnection() {
        _uiState.value = ServiceActionUiState.BlockedStartByConnection
    }

    fun showStartingTrip() {
        _uiState.value = ServiceActionUiState.StartingTrip
    }

    fun showStartFailed(@StringRes messageRes: Int, canRetry: Boolean) {
        _uiState.value = ServiceActionUiState.StartFailed(messageRes, canRetry)
    }

    fun showPreparingEnd() {
        _uiState.value = ServiceActionUiState.PreparingEnd
    }

    fun showBlockedEndByConnection() {
        _uiState.value = ServiceActionUiState.BlockedEndByConnection
    }

    fun showEndingTrip() {
        _uiState.value = ServiceActionUiState.EndingTrip
    }

    fun showEndFailed(@StringRes messageRes: Int, canRetry: Boolean) {
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
    }

    fun clearEndTripRequest() {
        endTripRequest = null
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

    fun reset() {
        clearStartTripRequest()
        clearEndTripRequest()
        showIdle()
    }
}
