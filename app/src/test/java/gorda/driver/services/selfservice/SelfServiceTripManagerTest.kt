package gorda.driver.services.selfservice

import gorda.driver.services.masterData.SelfServiceRejectionReason
import gorda.driver.services.retrofit.DriverAppRequestException
import gorda.driver.ui.service.current.SelfServiceProvisionalTrip
import gorda.driver.ui.service.current.SelfServiceTerminalData
import gorda.driver.ui.service.current.SelfServiceTerminalStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * [SelfServiceTripManager.buildRequest] is what guarantees exactly one request ever goes out for
 * a trip that started and ended offline (add-driver-self-service design D5, task 4.4): whichever
 * shape [SelfServiceTripManager.trySync] sends is entirely determined by this pure function, so
 * the single-request/no-second-terminate contract is tested here directly rather than through the
 * network-calling `trySync` (already covered for the plain online-mode path by other means).
 */
class SelfServiceTripManagerTest {

    private fun runningTrip(terminal: SelfServiceTerminalData? = null) = SelfServiceProvisionalTrip(
        localId = "local-1",
        startedAt = 1_000L,
        multiplier = 1.5,
        gpsLat = -33.45,
        gpsLng = -70.66,
        terminal = terminal
    )

    @Test
    fun `still running trip builds the plain online-mode request`() {
        val request = SelfServiceTripManager.buildRequest(runningTrip())

        assertEquals(1.5, request.trip_multiplier, 0.0)
        assertNull(request.deferred)
        assertNull(request.created_at)
        assertNull(request.start_trip_at)
        assertNull(request.end_trip_at)
        assertNull(request.status)
        assertNull(request.trip_fee)
        assertNull(request.trip_distance)
        assertNull(request.route)
    }

    @Test
    fun `terminated trip builds a single deferred payload carrying creation and terminal data`() {
        val trip = runningTrip(
            terminal = SelfServiceTerminalData(
                status = SelfServiceTerminalStatus.TERMINATED,
                endedAt = 1_500L,
                route = "route-json",
                tripDistance = 4_200,
                tripFee = 3_000
            )
        )

        val request = SelfServiceTripManager.buildRequest(trip)

        assertEquals(true, request.deferred)
        assertEquals(1_000L, request.created_at)
        assertEquals(1_000L, request.start_trip_at)
        assertEquals(1_500L, request.end_trip_at)
        assertEquals("terminated", request.status)
        assertEquals(3_000, request.trip_fee)
        assertEquals(4_200, request.trip_distance)
        assertEquals("route-json", request.route)
    }

    @Test
    fun `zero fee on a deferred termination is never written`() {
        val trip = runningTrip(
            terminal = SelfServiceTerminalData(
                status = SelfServiceTerminalStatus.TERMINATED,
                endedAt = 1_500L,
                route = "route-json",
                tripDistance = 100,
                tripFee = null
            )
        )

        val request = SelfServiceTripManager.buildRequest(trip)

        assertNull(request.trip_fee)
    }

    @Test
    fun `canceled trip carries no terminal metrics`() {
        val trip = runningTrip(terminal = SelfServiceTerminalData(status = SelfServiceTerminalStatus.CANCELED))

        val request = SelfServiceTripManager.buildRequest(trip)

        assertEquals(true, request.deferred)
        assertEquals("canceled", request.status)
        assertNull(request.end_trip_at)
        assertNull(request.trip_fee)
        assertNull(request.trip_distance)
        assertNull(request.route)
    }

    @Test
    fun `end_trip_at is clamped strictly after start_trip_at when reported out of order`() {
        val trip = runningTrip(
            terminal = SelfServiceTerminalData(
                status = SelfServiceTerminalStatus.TERMINATED,
                endedAt = 900L, // before startedAt (1_000L) — clock skew / bad local reading
                route = "route-json",
                tripDistance = 10,
                tripFee = 500
            )
        )

        val request = SelfServiceTripManager.buildRequest(trip)

        assertEquals(1_001L, request.end_trip_at)
    }

    /** Fails every request with [exception] without ever invoking the real network call. */
    private class ThrowingRequestExecutor(
        private val exception: Throwable
    ) : SelfServiceTripManager.RequestExecutor {
        override suspend fun <T> execute(
            endpoint: String,
            request: suspend (authorizationHeader: String) -> Response<T>
        ): Response<T> {
            throw exception
        }
    }

    private fun rejectionException(reason: String?): DriverAppRequestException {
        val errorBody = reason?.let { """{"error":"$it"}""" }
        return DriverAppRequestException(
            endpoint = "driver-app/me/services",
            baseUrl = "https://example.test",
            code = 403,
            errorBody = errorBody,
            hasCurrentUser = true
        )
    }

    /**
     * [SelfServiceTripManager.trySync]'s terminal-vs-retryable classification (task 4.6): a
     * fatal, self-explaining rejection drops the queued trip and surfaces a message, everything
     * else (including a network hiccup with no parseable reason) stays queued for the next
     * opportunistic retry signal from [gorda.driver.ui.MainViewModel].
     */
    @Test
    fun `terminal rejection reasons drop the queued trip and surface a message`() = runTest {
        listOf(
            SelfServiceRejectionReason.DRIVER_DISABLED,
            SelfServiceRejectionReason.NEGATIVE_BALANCE_PERCENTAGE,
            SelfServiceRejectionReason.DRIVER_ALREADY_IN_SERVICE,
            SelfServiceRejectionReason.MALFORMED_DEFERRED_PAYLOAD
        ).forEach { reason ->
            val manager = SelfServiceTripManager(
                requestExecutor = ThrowingRequestExecutor(rejectionException(reason))
            )
            manager.enqueue(runningTrip())

            val result = manager.trySync()

            assertTrue("expected Rejected for reason=$reason", result is SelfServiceTripManager.Result.Rejected)
            assertNull("trip should be dropped for reason=$reason", manager.getPendingTrip())
        }
    }

    @Test
    fun `driver_not_connected keeps the trip queued for retry`() = runTest {
        val manager = SelfServiceTripManager(
            requestExecutor = ThrowingRequestExecutor(
                rejectionException(SelfServiceRejectionReason.DRIVER_NOT_CONNECTED)
            )
        )
        manager.enqueue(runningTrip())

        val result = manager.trySync()

        assertEquals(SelfServiceTripManager.Result.Retryable, result)
        assertNotNull(manager.getPendingTrip())
    }

    @Test
    fun `unrecognized or missing rejection reason is treated as retryable`() = runTest {
        val manager = SelfServiceTripManager(
            requestExecutor = ThrowingRequestExecutor(rejectionException(null))
        )
        manager.enqueue(runningTrip())

        val result = manager.trySync()

        assertEquals(SelfServiceTripManager.Result.Retryable, result)
        assertNotNull(manager.getPendingTrip())
    }

    @Test
    fun `a plain network failure is treated as retryable`() = runTest {
        val manager = SelfServiceTripManager(
            requestExecutor = ThrowingRequestExecutor(java.io.IOException("timeout"))
        )
        manager.enqueue(runningTrip())

        val result = manager.trySync()

        assertEquals(SelfServiceTripManager.Result.Retryable, result)
        assertNotNull(manager.getPendingTrip())
    }
}
