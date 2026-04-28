package gorda.driver.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainViewModelConnectionLogicTest {

    @Test
    fun manualConnectUsesCachedLocationWhenAvailable() {
        val plan = MainViewModel.planManualConnect(hasCachedLocation = true)

        assertEquals(MainViewModel.ManualConnectSource.CACHED_LOCATION, plan.source)
        assertTrue(plan.usesImmediateLocation)
    }

    @Test
    fun manualConnectFallsBackToServiceLocationWhenNoCachedLocationExists() {
        val plan = MainViewModel.planManualConnect(hasCachedLocation = false)

        assertEquals(MainViewModel.ManualConnectSource.SERVICE_LOCATION, plan.source)
        assertFalse(plan.usesImmediateLocation)
    }

    @Test
    fun matchingSessionConfirmsPresenceAckEvenWithoutFirebaseSocketGate() {
        val confirmed = MainViewModel.canConfirmPresenceAck(
            expectedSessionId = "session-1",
            observedSessionId = "session-1"
        )

        assertTrue(confirmed)
    }

    @Test
    fun mismatchedSessionDoesNotConfirmPresenceAck() {
        val confirmed = MainViewModel.canConfirmPresenceAck(
            expectedSessionId = "session-1",
            observedSessionId = "session-2"
        )

        assertFalse(confirmed)
    }

    @Test
    fun missingExpectedSessionDoesNotConfirmPresenceAck() {
        val confirmed = MainViewModel.canConfirmPresenceAck(
            expectedSessionId = null,
            observedSessionId = "session-1"
        )

        assertFalse(confirmed)
    }

    @Test
    fun networkRestoredReconnectWaitsForFirebaseSocketWhenBudgetRemains() {
        val action = MainViewModel.resolveFirebaseSocketRecoveryAction(
            reason = "network_restored",
            firebaseConnected = false,
            phase = MainViewModel.DriverPresencePhase.RECONNECTING,
            remainingWaitBudget = 1
        )

        assertEquals(MainViewModel.FirebaseSocketRecoveryAction.WAIT_FOR_SOCKET, action)
    }

    @Test
    fun repeatedFirebaseSocketTimeoutFallsThroughToPresenceWriteProbe() {
        val action = MainViewModel.resolveFirebaseSocketRecoveryAction(
            reason = "firebase_socket_timeout",
            firebaseConnected = false,
            phase = MainViewModel.DriverPresencePhase.RECONNECTING,
            remainingWaitBudget = 0
        )

        assertEquals(MainViewModel.FirebaseSocketRecoveryAction.PROBE_PRESENCE_WRITE, action)
    }

    @Test
    fun healthySocketSkipsGateForPresenceRewriteFailures() {
        val action = MainViewModel.resolveFirebaseSocketRecoveryAction(
            reason = "presence_write_failed",
            firebaseConnected = true,
            phase = MainViewModel.DriverPresencePhase.RECONNECTING,
            remainingWaitBudget = 1
        )

        assertEquals(MainViewModel.FirebaseSocketRecoveryAction.SKIP, action)
    }

    @Test
    fun firebaseSocketReadyOnlyResumesFromSocketWaitingPhase() {
        val waitingSocket = MainViewModel.shouldResumeAutomaticReconnectFromSocket(
            desiredOnline = true,
            phase = MainViewModel.DriverPresencePhase.WAITING_FOR_FIREBASE_SOCKET
        )
        val reconnecting = MainViewModel.shouldResumeAutomaticReconnectFromSocket(
            desiredOnline = true,
            phase = MainViewModel.DriverPresencePhase.RECONNECTING
        )

        assertTrue(waitingSocket)
        assertFalse(reconnecting)
    }

    @Test
    fun recoveredLocationReschedulesReconnectWhenPresenceIsRecoverable() {
        val shouldReconnect = MainViewModel.shouldScheduleReconnectFromRecoveredLocation(
            MainViewModel.DriverPresenceState(
                desiredOnline = true,
                actualOnline = false,
                phase = MainViewModel.DriverPresencePhase.RECONNECTING
            )
        )

        assertTrue(shouldReconnect)
    }

    @Test
    fun recoveredLocationDoesNotRescheduleWhenPresenceHasFatalStop() {
        val shouldReconnect = MainViewModel.shouldScheduleReconnectFromRecoveredLocation(
            MainViewModel.DriverPresenceState(
                desiredOnline = true,
                actualOnline = false,
                firebaseConnected = true,
                fatalStopReason = "auth_lost"
            )
        )

        assertFalse(shouldReconnect)
    }

    @Test
    fun automaticReconnectPolicyUsesAggressiveEarlyBackoff() {
        val policy = MainViewModel.automaticReconnectBackoffPolicy()

        assertEquals(0L, policy.delayForAttempt(0))
        assertEquals(500L, policy.delayForAttempt(1))
        assertEquals(1_000L, policy.delayForAttempt(2))
    }

    @Test
    fun firebaseTransportResetUsesCooldown() {
        assertTrue(
            MainViewModel.shouldResetFirebaseTransport(
                lastResetAtMs = 1_000L,
                nowMs = 16_000L
            )
        )
        assertFalse(
            MainViewModel.shouldResetFirebaseTransport(
                lastResetAtMs = 1_000L,
                nowMs = 10_000L
            )
        )
    }

    @Test
    fun freshCachedLocationCanBeRestoredWithoutPresenceSideEffects() {
        val decision = MainViewModel.cachedLocationRestoreDecision(
            capturedAtEpochMs = 10_000L,
            nowEpochMs = 10_000L + MainViewModel.CACHED_LOCATION_MAX_AGE_MS
        )

        assertTrue(decision.shouldRestore)
        assertTrue(decision.emitsUiLocation)
        assertFalse(decision.schedulesReconnect)
        assertFalse(decision.sendsPresenceHeartbeat)
    }

    @Test
    fun staleCachedLocationIsRejected() {
        val decision = MainViewModel.cachedLocationRestoreDecision(
            capturedAtEpochMs = 10_000L,
            nowEpochMs = 10_000L + MainViewModel.CACHED_LOCATION_MAX_AGE_MS + 1L
        )

        assertFalse(decision.shouldRestore)
        assertFalse(decision.emitsUiLocation)
        assertFalse(decision.schedulesReconnect)
        assertFalse(decision.sendsPresenceHeartbeat)
    }
}
