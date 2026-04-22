package gorda.driver.ui

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

internal class ReconnectCoordinator(
    private val scope: CoroutineScope,
    private val onAttempt: suspend (attemptIndex: Int, reason: String) -> Unit,
    private val policy: ReconnectBackoffPolicy = ReconnectBackoffPolicy(),
    private val dispatcher: CoroutineDispatcher = Dispatchers.Main
) {
    private var job: Job? = null
    private var nextAttemptIndex = 0

    fun schedule(
        reason: String,
        resetBackoff: Boolean = false,
        forceReschedule: Boolean = false
    ) {
        if (resetBackoff) {
            nextAttemptIndex = 0
        }

        if (job?.isActive == true) {
            if (!forceReschedule) {
                return
            }
            job?.cancel()
        }

        val attemptIndex = nextAttemptIndex
        val delayMs = policy.delayForAttempt(attemptIndex)
        job = scope.launch(dispatcher) {
            if (delayMs > 0) {
                delay(delayMs)
            }
            job = null
            onAttempt(attemptIndex, reason)
        }
        nextAttemptIndex = min(attemptIndex + 1, policy.lastAttemptIndex)
    }

    fun cancelAndReset() {
        job?.cancel()
        job = null
        nextAttemptIndex = 0
    }

    fun hasPendingAttempt(): Boolean {
        return job?.isActive == true
    }
}

internal class ReconnectBackoffPolicy(
    private val delaysMs: LongArray = longArrayOf(0L, 1_000L, 2_000L, 5_000L, 10_000L, 15_000L)
) {
    val lastAttemptIndex: Int = delaysMs.lastIndex

    fun delayForAttempt(attemptIndex: Int): Long {
        return delaysMs[attemptIndex.coerceIn(0, lastAttemptIndex)]
    }
}
