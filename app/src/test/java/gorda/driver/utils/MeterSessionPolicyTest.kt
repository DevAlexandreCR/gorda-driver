package gorda.driver.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class MeterSessionPolicyTest {

    @Test
    fun nullTrackedServiceIdStopsRestart() {
        assertEquals(
            MeterSessionPolicy.NullIntentStartAction.STOP,
            MeterSessionPolicy.resolveNullIntentStart(
                trackedServiceId = null,
                hasRecoverableSession = true
            )
        )
    }

    @Test
    fun blankTrackedServiceIdStopsRestart() {
        assertEquals(
            MeterSessionPolicy.NullIntentStartAction.STOP,
            MeterSessionPolicy.resolveNullIntentStart(
                trackedServiceId = "  ",
                hasRecoverableSession = true
            )
        )
    }

    @Test
    fun trackedServiceIdWithoutRecoverableSessionStops() {
        assertEquals(
            MeterSessionPolicy.NullIntentStartAction.STOP,
            MeterSessionPolicy.resolveNullIntentStart(
                trackedServiceId = "service-1",
                hasRecoverableSession = false
            )
        )
    }

    @Test
    fun trackedServiceIdWithRecoverableSessionRestores() {
        assertEquals(
            MeterSessionPolicy.NullIntentStartAction.RESTORE,
            MeterSessionPolicy.resolveNullIntentStart(
                trackedServiceId = "service-1",
                hasRecoverableSession = true
            )
        )
    }

    @Test
    fun nullBoundTokenRejectsFeeUpdate() {
        assertEquals(
            false,
            MeterSessionPolicy.shouldAcceptFeeUpdate(boundToken = null, emittedToken = 42L)
        )
    }

    @Test
    fun matchingTokenAcceptsFeeUpdate() {
        assertEquals(
            true,
            MeterSessionPolicy.shouldAcceptFeeUpdate(boundToken = 42L, emittedToken = 42L)
        )
    }

    @Test
    fun mismatchingTokenRejectsFeeUpdate() {
        assertEquals(
            false,
            MeterSessionPolicy.shouldAcceptFeeUpdate(boundToken = 42L, emittedToken = 7L)
        )
    }

    @Test
    fun elapsedOffsetForPastStartIsPositive() {
        assertEquals(
            5_000L,
            MeterSessionPolicy.elapsedOffsetMs(startedAtEpochSec = 100L, nowEpochMs = 105_000L)
        )
    }

    @Test
    fun elapsedOffsetForFutureStartIsClampedToZero() {
        assertEquals(
            0L,
            MeterSessionPolicy.elapsedOffsetMs(startedAtEpochSec = 200L, nowEpochMs = 100_000L)
        )
    }

    @Test
    fun elapsedOffsetForEqualStartIsZero() {
        assertEquals(
            0L,
            MeterSessionPolicy.elapsedOffsetMs(startedAtEpochSec = 100L, nowEpochMs = 100_000L)
        )
    }
}
