package gorda.driver.services.selfservice

import androidx.annotation.StringRes
import com.google.gson.Gson
import gorda.driver.R
import gorda.driver.models.Service
import gorda.driver.services.masterData.ConnectLocation
import gorda.driver.services.masterData.CreateServiceRequest
import gorda.driver.services.masterData.MasterDataApiService
import gorda.driver.services.masterData.SelfServiceErrorBody
import gorda.driver.services.masterData.SelfServiceRejectionReason
import gorda.driver.services.retrofit.DriverAppRequestException
import gorda.driver.services.retrofit.DriverAppRequestRunner
import gorda.driver.services.retrofit.MasterDataRetrofit
import gorda.driver.ui.service.current.SelfServiceProvisionalTrip
import gorda.driver.ui.service.current.SelfServiceTerminalData
import gorda.driver.ui.service.current.SelfServiceTerminalStatus
import retrofit2.Response

/**
 * Owns the queued `POST /driver-app/me/services` sync for a local, offline-tolerant self-service
 * trip (add-driver-self-service design D5, steps 1-3). `HomeFragment.onSelfServiceTripConfirmed`
 * starts `FeesService` immediately against a client-generated local id; this manager retries the
 * creation request whenever [MainViewModel] tells it connectivity/location may have improved, and
 * reports back so the caller can rebind metering/UI to the real id or surface a rejection.
 *
 * Mirrors [gorda.driver.services.firebase.PresenceManager]'s shape: fire-and-forget retries driven
 * by discrete external signals (no polling/backoff loop), a swappable [RequestExecutor] for tests.
 *
 * If the trip ends or is canceled locally before the creation synced, [SelfServiceProvisionalTrip.terminal]
 * is set (via `MainViewModel.endUnsyncedSelfServiceTrip`/`cancelUnsyncedSelfServiceTrip`) *before* the
 * next [trySync] call — so exactly one request ever goes out for that trip, carrying both the creation
 * and the terminal data in a single deferred payload (design D5, step 2: "a separate second write" is
 * the server's internal orchestration, never a second request from this app).
 */
class SelfServiceTripManager(
    private val apiService: MasterDataApiService = MasterDataRetrofit.getRetrofit().create(MasterDataApiService::class.java),
    private val requestExecutor: RequestExecutor = RequestExecutor.Default
) {

    sealed class Result {
        data class Created(val trip: SelfServiceProvisionalTrip, val service: Service) : Result()
        data class Rejected(val trip: SelfServiceProvisionalTrip, @StringRes val messageRes: Int) : Result()

        /** Transient failure (network, timeout, missing GPS fix, `driver_not_connected`): stays queued. */
        object Retryable : Result()
        object NoPendingTrip : Result()
        object AlreadySyncing : Result()
    }

    /** Lets tests stub out the authenticated-request wrapper (bypasses real Firebase Auth). */
    interface RequestExecutor {
        suspend fun <T> execute(
            endpoint: String,
            request: suspend (authorizationHeader: String) -> Response<T>
        ): Response<T>

        object Default : RequestExecutor {
            override suspend fun <T> execute(
                endpoint: String,
                request: suspend (String) -> Response<T>
            ): Response<T> = DriverAppRequestRunner.execute(endpoint, request)
        }
    }

    @Volatile
    private var pendingTrip: SelfServiceProvisionalTrip? = null

    @Volatile
    private var isSyncing: Boolean = false

    /** Restores a trip persisted before a process death (RideRecoveryStore-backed). */
    fun restore(trip: SelfServiceProvisionalTrip?) {
        pendingTrip = trip
    }

    fun enqueue(trip: SelfServiceProvisionalTrip) {
        pendingTrip = trip
    }

    fun getPendingTrip(): SelfServiceProvisionalTrip? = pendingTrip

    fun hasPendingTrip(): Boolean = pendingTrip != null

    /** Fills in a GPS fix for a trip that started without one yet (design D3: never block on it). */
    fun updateLocationIfMissing(lat: Double, lng: Double) {
        val trip = pendingTrip ?: return
        if (trip.gpsLat != null && trip.gpsLng != null) return
        pendingTrip = trip.copy(gpsLat = lat, gpsLng = lng)
    }

    /**
     * Marks the pending trip as ended locally (design D5, step 2/task 4.4): the next [trySync]
     * sends a single deferred payload carrying both the creation and this terminal data, instead
     * of the online-mode create request. A zero fee is never written, matching the normal
     * termination rule — pass `tripFee = null` for that case (same convention `CurrentServiceFragment`
     * already uses for a normal trip end). No-op if there is no pending trip (already synced/cleared).
     */
    fun markTerminated(endedAt: Long, route: String, tripDistance: Int, tripFee: Int?) {
        val trip = pendingTrip ?: return
        pendingTrip = trip.copy(
            terminal = SelfServiceTerminalData(
                status = SelfServiceTerminalStatus.TERMINATED,
                endedAt = endedAt,
                route = route,
                tripDistance = tripDistance,
                tripFee = tripFee
            )
        )
    }

    /**
     * Marks the pending trip as canceled locally (design D5, step 3/task 4.4 plumbing for the
     * windowed cancel UI landing in task 4.5): the next [trySync] sends a deferred payload with
     * `status="canceled"` — no terminal metrics apply to a cancellation.
     */
    fun markCanceled() {
        val trip = pendingTrip ?: return
        pendingTrip = trip.copy(terminal = SelfServiceTerminalData(status = SelfServiceTerminalStatus.CANCELED))
    }

    suspend fun trySync(): Result {
        val trip = pendingTrip ?: return Result.NoPendingTrip
        if (trip.gpsLat == null || trip.gpsLng == null) return Result.Retryable
        if (isSyncing) return Result.AlreadySyncing

        isSyncing = true
        try {
            val payload = buildRequest(trip)
            val response = requestExecutor.execute("/driver-app/me/services") { authorization ->
                apiService.createSelfService(authorization, payload)
            }

            val service = response.body()?.data?.service ?: return Result.Retryable
            pendingTrip = null
            return Result.Created(trip, service)
        } catch (e: DriverAppRequestException) {
            val reason = parseRejectionReason(e.errorBody)
            if (!isTerminalRejection(reason)) {
                // Includes driver_not_connected: presence typically self-heals via PresenceManager's
                // own reconnect flow moments after the network returns — don't discard the ride for it.
                return Result.Retryable
            }
            pendingTrip = null
            return Result.Rejected(trip, messageResFor(reason))
        } catch (e: Exception) {
            return Result.Retryable
        } finally {
            isSyncing = false
        }
    }

    private fun isTerminalRejection(reason: String?): Boolean {
        return reason == SelfServiceRejectionReason.DRIVER_DISABLED ||
            reason == SelfServiceRejectionReason.NEGATIVE_BALANCE_PERCENTAGE ||
            reason == SelfServiceRejectionReason.DRIVER_ALREADY_IN_SERVICE ||
            // A malformed deferred payload (e.g. the sync arrived after the device clock drifted
            // ahead of the server) can never fix itself by retrying — surface and drop it rather
            // than wedging the queue forever (design D5, "Timestamp source of truth").
            reason == SelfServiceRejectionReason.MALFORMED_DEFERRED_PAYLOAD
    }

    @StringRes
    private fun messageResFor(reason: String?): Int = when (reason) {
        SelfServiceRejectionReason.DRIVER_DISABLED -> R.string.self_service_rejected_disabled
        SelfServiceRejectionReason.NEGATIVE_BALANCE_PERCENTAGE -> R.string.self_service_rejected_negative_balance
        SelfServiceRejectionReason.DRIVER_ALREADY_IN_SERVICE -> R.string.self_service_rejected_already_in_service
        SelfServiceRejectionReason.MALFORMED_DEFERRED_PAYLOAD -> R.string.self_service_rejected_malformed_deferred_payload
        else -> R.string.common_error
    }

    companion object {
        /**
         * Builds the `POST /driver-app/me/services` body for [trip]: the plain online-mode
         * request while [SelfServiceProvisionalTrip.terminal] is null, or a single deferred
         * payload carrying both the creation and the terminal data once it is set. `created_at`
         * and `start_trip_at` are both [SelfServiceProvisionalTrip.startedAt] — the trip is
         * created and started in the same one-step action — and `end_trip_at` is clamped to be
         * strictly after it, so a locally-generated payload can never violate the server's own
         * `created_at <= start_trip_at < end_trip_at` ordering check (design D5, "Timestamp source
         * of truth"). The server still clamps every timestamp to its own arrival time.
         */
        internal fun buildRequest(trip: SelfServiceProvisionalTrip): CreateServiceRequest {
            val location = ConnectLocation(lat = requireNotNull(trip.gpsLat), lng = requireNotNull(trip.gpsLng))
            val terminal = trip.terminal
                ?: return CreateServiceRequest(location = location, trip_multiplier = trip.multiplier)

            val isTerminated = terminal.status == SelfServiceTerminalStatus.TERMINATED
            return CreateServiceRequest(
                location = location,
                trip_multiplier = trip.multiplier,
                deferred = true,
                created_at = trip.startedAt,
                start_trip_at = trip.startedAt,
                end_trip_at = if (isTerminated) clampEndTripAt(trip.startedAt, terminal.endedAt) else null,
                status = if (isTerminated) STATUS_TERMINATED else STATUS_CANCELED,
                trip_fee = if (isTerminated) terminal.tripFee else null,
                trip_distance = if (isTerminated) terminal.tripDistance else null,
                route = if (isTerminated) terminal.route else null
            )
        }

        private fun clampEndTripAt(startTripAt: Long, endedAt: Long?): Long {
            val candidate = endedAt ?: startTripAt
            return if (candidate > startTripAt) candidate else startTripAt + 1
        }

        private const val STATUS_TERMINATED = "terminated"
        private const val STATUS_CANCELED = "canceled"
    }

    private fun parseRejectionReason(errorBody: String?): String? {
        if (errorBody == null) return null
        return try {
            Gson().fromJson(errorBody, SelfServiceErrorBody::class.java)?.error
        } catch (_: Exception) {
            null
        }
    }
}
