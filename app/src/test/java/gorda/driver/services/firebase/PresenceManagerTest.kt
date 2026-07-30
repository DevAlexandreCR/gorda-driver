package gorda.driver.services.firebase

import gorda.driver.interfaces.LocInterface
import gorda.driver.services.masterData.ApiEnvelope
import gorda.driver.services.masterData.ConnectRequest
import gorda.driver.services.masterData.ConnectResponse
import gorda.driver.services.masterData.DevicePayload
import gorda.driver.services.masterData.DriverTokenPayload
import gorda.driver.services.masterData.LocationRequest
import gorda.driver.services.masterData.LocationResponse
import gorda.driver.services.masterData.MasterDataApiService
import gorda.driver.services.masterData.SetSelectedVehicleRequest
import gorda.driver.services.retrofit.DriverAppRequestException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import java.io.IOException

/**
 * PresenceManager is only reachable through Firebase (`DatabaseReference`) and Firebase Auth
 * (`DriverAppRequestRunner` -> `Auth`), neither of which can be constructed off-device without
 * Robolectric/Mockito (not present in this module). These tests use the constructor seams added
 * for testability (nullable `infoConnectedRef`, injectable `ioDispatcher`/`RequestExecutor`, and
 * the `@VisibleForTesting internal` helpers) to drive the heartbeat ticker deterministically with
 * `kotlinx-coroutines-test`, without touching real Firebase.
 *
 * IMPORTANT: the ticker is `while (isActive) { delay(30_000); send }` — it never idles on its
 * own. `advanceUntilIdle()` therefore hangs forever once the ticker is alive (it keeps advancing
 * virtual time to run the ticker's ever-rescheduled next tick), and `runTest` itself calls an
 * equivalent auto-advance-to-idle when the test body returns, so a live ticker hangs even a test
 * that never calls `advanceUntilIdle()` explicitly. Every advance below is therefore bounded:
 * `runCurrent()` drains work already due "now"; `advanceTimeBy(interval + 1)` crosses exactly one
 * heartbeat boundary, each followed by `runCurrent()`. Every test also disposes the manager in a
 * `finally` block *inside* the `runTest` body (not a JUnit `@After`, which runs too late — after
 * `runTest` has already tried to idle and would hang/throw first) so no live ticker job is left
 * on the shared TestCoroutineScheduler when the test body returns.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class PresenceManagerTest {

    private val heartbeatIntervalMs = 30_000L

    private fun buildManager(
        dispatcher: TestDispatcher,
        requestExecutor: FakeRequestExecutor
    ): PresenceManager {
        return PresenceManager(
            infoConnectedRef = null,
            dispatcher = dispatcher,
            ioDispatcher = dispatcher,
            transportCycler = object : PresenceManager.TransportCycler {
                override fun cycle() = Unit
            },
            apiService = FakeMasterDataApiService(),
            requestExecutor = requestExecutor
        )
    }

    /** Drives the manager to State.Connected: start() + simulated .info/connected = true. */
    private fun connect(manager: PresenceManager, location: TestLocation = TestLocation(4.0, -74.0)) {
        manager.start(driverId = "driver-1", vehicleId = "vehicle-1", location = location)
        manager.simulateFirebaseConnectedForTest(true)
    }

    @Test(timeout = 10_000)
    fun `ticker starts on Connected and sends a heartbeat after the interval elapses`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val executor = FakeRequestExecutor()
        val manager = buildManager(dispatcher, executor)
        try {
            connect(manager)
            runCurrent()
            assertTrue(manager.state.value is PresenceManager.State.Connected)
            assertEquals(listOf("/driver-app/me/connect"), executor.calls)

            advanceTimeBy(heartbeatIntervalMs + 1)
            runCurrent()

            assertEquals(listOf("/driver-app/me/connect", "/driver-app/me/location"), executor.calls)
        } finally {
            manager.dispose()
        }
    }

    @Test(timeout = 10_000)
    fun `ticker is cancelled when the driver stops — no further sends`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val executor = FakeRequestExecutor()
        val manager = buildManager(dispatcher, executor)
        try {
            connect(manager)
            runCurrent()
            advanceTimeBy(heartbeatIntervalMs + 1)
            runCurrent()
            val callsAtStop = executor.calls.size

            manager.stop()
            advanceTimeBy(heartbeatIntervalMs * 3)
            runCurrent()

            assertEquals(callsAtStop, executor.calls.size)
        } finally {
            manager.dispose()
        }
    }

    @Test(timeout = 10_000)
    fun `410 not_connected reruns writePresence (connect flow)`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val executor = FakeRequestExecutor()
        val manager = buildManager(dispatcher, executor)
        try {
            connect(manager)
            runCurrent()
            executor.enqueue(FakeRequestExecutor.Outcome.HttpError(code = 410))

            advanceTimeBy(heartbeatIntervalMs + 1)
            runCurrent()

            assertEquals(
                listOf("/driver-app/me/connect", "/driver-app/me/location", "/driver-app/me/connect"),
                executor.calls
            )
            // The re-run connect succeeds (default queued outcome is Success), so the driver
            // transparently stays Connected and the ticker keeps running.
            assertTrue(manager.state.value is PresenceManager.State.Connected)
        } finally {
            manager.dispose()
        }
    }

    @Test(timeout = 10_000)
    fun `409 session_superseded transitions to ConnectRejected and stops the ticker`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val executor = FakeRequestExecutor()
        val manager = buildManager(dispatcher, executor)
        try {
            connect(manager)
            runCurrent()
            executor.enqueue(
                FakeRequestExecutor.Outcome.HttpError(
                    code = 409,
                    errorBody = """{"error":"session_superseded"}"""
                )
            )

            advanceTimeBy(heartbeatIntervalMs + 1)
            runCurrent()

            assertEquals(
                PresenceManager.State.ConnectRejected(reason = "session_superseded"),
                manager.state.value
            )
            val callsAtRejection = executor.calls.size

            advanceTimeBy(heartbeatIntervalMs * 3)
            runCurrent()

            assertEquals(callsAtRejection, executor.calls.size)
        } finally {
            manager.dispose()
        }
    }

    @Test(timeout = 10_000)
    fun `network error is swallowed and the ticker retries on the next interval`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val executor = FakeRequestExecutor()
        val manager = buildManager(dispatcher, executor)
        try {
            connect(manager)
            runCurrent()
            executor.enqueue(FakeRequestExecutor.Outcome.NetworkError)

            advanceTimeBy(heartbeatIntervalMs + 1)
            runCurrent()

            assertTrue(manager.state.value is PresenceManager.State.Connected)
            assertEquals(listOf("/driver-app/me/connect", "/driver-app/me/location"), executor.calls)

            advanceTimeBy(heartbeatIntervalMs + 1)
            runCurrent()

            assertEquals(
                listOf("/driver-app/me/connect", "/driver-app/me/location", "/driver-app/me/location"),
                executor.calls
            )
            assertTrue(manager.state.value is PresenceManager.State.Connected)
        } finally {
            manager.dispose()
        }
    }

    @Test(timeout = 10_000)
    fun `null latestLocation skips the tick without a network call`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val executor = FakeRequestExecutor()
        val manager = buildManager(dispatcher, executor)
        try {
            connect(manager)
            runCurrent()
            manager.clearLatestLocationForTest()

            advanceTimeBy(heartbeatIntervalMs + 1)
            runCurrent()

            // Only the initial connect() call is present — no heartbeat was sent.
            assertEquals(listOf("/driver-app/me/connect"), executor.calls)
        } finally {
            manager.dispose()
        }
    }
}

private data class TestLocation(override var lat: Double, override var lng: Double) : LocInterface

/**
 * Fakes PresenceManager.RequestExecutor: records every endpoint invoked and returns queued
 * outcomes in order (defaulting to Success), so tests can drive 200/410/409/network-error paths
 * without ever going through real Firebase Auth.
 */
private class FakeRequestExecutor : PresenceManager.RequestExecutor {
    val calls = mutableListOf<String>()
    private val queuedOutcomes = ArrayDeque<Outcome>()

    sealed class Outcome {
        object Success : Outcome()
        data class HttpError(val code: Int, val errorBody: String? = null) : Outcome()
        object NetworkError : Outcome()
    }

    fun enqueue(outcome: Outcome) {
        queuedOutcomes.addLast(outcome)
    }

    override suspend fun <T> execute(
        endpoint: String,
        request: suspend (authorizationHeader: String) -> Response<T>
    ): Response<T> {
        calls.add(endpoint)
        val outcome = if (queuedOutcomes.isEmpty()) Outcome.Success else queuedOutcomes.removeFirst()
        return when (outcome) {
            is Outcome.Success -> request("Bearer test-token")
            is Outcome.HttpError -> throw DriverAppRequestException(
                endpoint = endpoint,
                baseUrl = "https://test.example",
                code = outcome.code,
                errorBody = outcome.errorBody,
                hasCurrentUser = true
            )
            is Outcome.NetworkError -> throw IOException("simulated network error")
        }
    }
}

/** Only connect()/updateLocation() are ever reached in these tests; the rest are unused stubs. */
private class FakeMasterDataApiService : MasterDataApiService {
    override suspend fun getRideFeesSnapshot() = error("not used in PresenceManagerTest")
    override suspend fun getVersionPolicy() = error("not used in PresenceManagerTest")
    override suspend fun getDriver(driverId: String) = error("not used in PresenceManagerTest")
    override suspend fun updateDevice(driverId: String, payload: DevicePayload) =
        error("not used in PresenceManagerTest")

    override suspend fun getDriverHistory(authorization: String) = error("not used in PresenceManagerTest")
    override suspend fun upsertDriverToken(authorization: String, payload: DriverTokenPayload) =
        error("not used in PresenceManagerTest")

    override suspend fun deleteDriverToken(authorization: String) = error("not used in PresenceManagerTest")

    override suspend fun connect(
        authorization: String,
        payload: ConnectRequest
    ): Response<ApiEnvelope<ConnectResponse>> =
        Response.success(ApiEnvelope(success = true, data = ConnectResponse(connected = true)))

    override suspend fun disconnect(authorization: String) = error("not used in PresenceManagerTest")

    override suspend fun updateLocation(
        authorization: String,
        payload: LocationRequest
    ): Response<ApiEnvelope<LocationResponse>> =
        Response.success(ApiEnvelope(success = true, data = LocationResponse(updated = true)))

    override suspend fun getVehicles(authorization: String) = error("not used in PresenceManagerTest")
    override suspend fun setSelectedVehicle(authorization: String, payload: SetSelectedVehicleRequest) =
        error("not used in PresenceManagerTest")
}
