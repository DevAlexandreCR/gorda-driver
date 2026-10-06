package gorda.driver.utils

object MeterSessionPolicy {

    enum class NullIntentStartAction {
        RESTORE,
        STOP
    }

    fun resolveNullIntentStart(
        trackedServiceId: String?,
        hasRecoverableSession: Boolean
    ): NullIntentStartAction {
        if (trackedServiceId.isNullOrBlank()) {
            return NullIntentStartAction.STOP
        }

        return if (hasRecoverableSession) {
            NullIntentStartAction.RESTORE
        } else {
            NullIntentStartAction.STOP
        }
    }

    fun shouldAcceptFeeUpdate(boundToken: Long?, emittedToken: Long): Boolean {
        return boundToken != null && boundToken == emittedToken
    }

    fun elapsedOffsetMs(startedAtEpochSec: Long, nowEpochMs: Long): Long {
        return maxOf(0L, nowEpochMs - startedAtEpochSec * 1000)
    }
}
