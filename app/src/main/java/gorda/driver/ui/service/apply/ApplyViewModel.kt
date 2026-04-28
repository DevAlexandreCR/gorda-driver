package gorda.driver.ui.service.apply

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import gorda.driver.R
import gorda.driver.ui.MainViewModel
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

class ApplyViewModel : ViewModel() {

    enum class ApplyConnectionStatus {
        OFFLINE,
        FIREBASE_DISCONNECTED,
        RECONNECTING,
        READY
    }

    data class RouteEstimate(
        val distanceMeters: Int,
        val timeSeconds: Int
    )

    sealed class ApplyUiState {
        object Preparing : ApplyUiState()

        data class RecoveringLocation(
            val canManualRetry: Boolean
        ) : ApplyUiState()

        object BlockedByConnection : ApplyUiState()

        object Applying : ApplyUiState()

        data class AppliedWaitingAssignment(val serviceName: String) : ApplyUiState()

        data class Failed(
            @StringRes val messageRes: Int,
            val canRetry: Boolean
        ) : ApplyUiState()

        object Canceling : ApplyUiState()
    }

    companion object {
        fun connectionStatusForApply(
            state: MainViewModel.DriverPresenceState
        ): ApplyConnectionStatus {
            return when {
                !state.hasNetwork -> ApplyConnectionStatus.OFFLINE
                state.actualOnline -> ApplyConnectionStatus.READY
                !state.firebaseConnected -> ApplyConnectionStatus.FIREBASE_DISCONNECTED
                else -> ApplyConnectionStatus.RECONNECTING
            }
        }

        fun isReadyToApply(
            state: MainViewModel.DriverPresenceState
        ): Boolean {
            return connectionStatusForApply(state) == ApplyConnectionStatus.READY
        }

        fun estimateRoute(
            originLat: Double,
            originLng: Double,
            destinationLat: Double,
            destinationLng: Double
        ): RouteEstimate {
            val distanceMeters = haversineDistanceMeters(
                originLat = originLat,
                originLng = originLng,
                destinationLat = destinationLat,
                destinationLng = destinationLng
            ).roundToInt()

            return RouteEstimate(
                distanceMeters = distanceMeters,
                timeSeconds = distanceMeters / 5
            )
        }

        private fun haversineDistanceMeters(
            originLat: Double,
            originLng: Double,
            destinationLat: Double,
            destinationLng: Double
        ): Double {
            val earthRadiusMeters = 6_371_000.0
            val dLat = Math.toRadians(destinationLat - originLat)
            val dLng = Math.toRadians(destinationLng - originLng)
            val lat1 = Math.toRadians(originLat)
            val lat2 = Math.toRadians(destinationLat)

            val a = sin(dLat / 2).pow(2.0) +
                cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2.0)

            val c = 2 * asin(sqrt(a))
            return earthRadiusMeters * c
        }
    }

    private val _uiState = MutableLiveData<ApplyUiState>(ApplyUiState.Preparing)
    val uiState: LiveData<ApplyUiState> = _uiState

    private var started = false
    private var applicantWriteConfirmed = false
    private var applicantWriteInFlight = false

    fun markStarted(): Boolean {
        if (started) {
            return false
        }

        started = true
        return true
    }

    fun showPreparing() {
        _uiState.value = ApplyUiState.Preparing
    }

    fun showRecoveringLocation(canManualRetry: Boolean) {
        applicantWriteInFlight = false
        _uiState.value = ApplyUiState.RecoveringLocation(canManualRetry)
    }

    fun showBlockedByConnection() {
        applicantWriteInFlight = false
        _uiState.value = ApplyUiState.BlockedByConnection
    }

    fun showApplying() {
        applicantWriteInFlight = true
        _uiState.value = ApplyUiState.Applying
    }

    fun showAppliedWaitingAssignment(serviceName: String) {
        applicantWriteConfirmed = true
        applicantWriteInFlight = false
        _uiState.value = ApplyUiState.AppliedWaitingAssignment(serviceName)
    }

    fun showFailed(@StringRes messageRes: Int, canRetry: Boolean) {
        applicantWriteInFlight = false
        _uiState.value = ApplyUiState.Failed(messageRes, canRetry)
    }

    fun showCanceling() {
        applicantWriteInFlight = false
        _uiState.value = ApplyUiState.Canceling
    }

    fun hasApplicantWriteConfirmed(): Boolean {
        return applicantWriteConfirmed
    }

    fun isApplicantWriteInFlight(): Boolean {
        return applicantWriteInFlight
    }

    fun isRecoveringLocation(): Boolean {
        return _uiState.value is ApplyUiState.RecoveringLocation
    }

    fun primaryActionRes(): Int {
        return if (hasApplicantWriteConfirmed()) {
            R.string.cancel_application
        } else {
            R.string.back_to_services
        }
    }
}
