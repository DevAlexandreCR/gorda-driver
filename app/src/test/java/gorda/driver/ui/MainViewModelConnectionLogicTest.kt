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
    fun networkRestoredReconnectWaitsForFirebaseSocket() {
        val gated = MainViewModel.shouldGateAutomaticReconnectOnFirebaseSocket(
            reason = "network_restored",
            firebaseConnected = false,
            phase = MainViewModel.DriverPresencePhase.RECONNECTING
        )

        assertTrue(gated)
    }

    @Test
    fun healthySocketSkipsGateForPresenceRewriteFailures() {
        val gated = MainViewModel.shouldGateAutomaticReconnectOnFirebaseSocket(
            reason = "presence_write_failed",
            firebaseConnected = true,
            phase = MainViewModel.DriverPresencePhase.RECONNECTING
        )

        assertFalse(gated)
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
    fun automaticReconnectPolicyUsesAggressiveEarlyBackoff() {
        val policy = MainViewModel.automaticReconnectBackoffPolicy()

        assertEquals(0L, policy.delayForAttempt(0))
        assertEquals(500L, policy.delayForAttempt(1))
        assertEquals(1_000L, policy.delayForAttempt(2))
    }
}
