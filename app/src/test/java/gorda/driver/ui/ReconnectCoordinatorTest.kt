package gorda.driver.ui

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ReconnectCoordinatorTest {

    @Test
    fun immediateRetryRunsWithoutDelay() = runTest {
        val attempts = mutableListOf<Pair<Int, String>>()
        val coordinator = buildCoordinator { attemptIndex, reason ->
            attempts += attemptIndex to reason
        }

        coordinator.schedule(reason = "presence_missing", resetBackoff = true)
        advanceUntilIdle()

        assertEquals(listOf(0 to "presence_missing"), attempts)
    }

    @Test
    fun secondScheduleDoesNotCreateDuplicatePendingAttempt() = runTest {
        val attempts = mutableListOf<Pair<Int, String>>()
        val coordinator = buildCoordinator { attemptIndex, reason ->
            attempts += attemptIndex to reason
        }

        coordinator.schedule(reason = "presence_missing", resetBackoff = true)
        advanceUntilIdle()

        coordinator.schedule(reason = "firebase_disconnected")
        coordinator.schedule(reason = "network_restored")
        advanceTimeBy(999)
        assertTrue(attempts.size == 1)

        advanceTimeBy(1)
        advanceUntilIdle()
        assertEquals(
            listOf(
                0 to "presence_missing",
                1 to "firebase_disconnected"
            ),
            attempts
        )
    }

    @Test
    fun backoffProgressionMatchesExpectedSchedule() = runTest {
        val attempts = mutableListOf<Int>()
        val coordinator = buildCoordinator { attemptIndex, _ ->
            attempts += attemptIndex
        }

        coordinator.schedule(reason = "presence_missing", resetBackoff = true)
        advanceUntilIdle()
        coordinator.schedule(reason = "presence_write_failed")
        advanceTimeBy(1_000)
        advanceUntilIdle()
        coordinator.schedule(reason = "presence_ack_timeout")
        advanceTimeBy(2_000)
        advanceUntilIdle()
        coordinator.schedule(reason = "firebase_disconnected")
        advanceTimeBy(5_000)
        advanceUntilIdle()

        assertEquals(listOf(0, 1, 2, 3), attempts)
    }

    @Test
    fun cancelAndResetClearsPendingRetryAndRestartsBackoff() = runTest {
        val attempts = mutableListOf<Int>()
        val coordinator = buildCoordinator { attemptIndex, _ ->
            attempts += attemptIndex
        }

        coordinator.schedule(reason = "presence_missing", resetBackoff = true)
        advanceUntilIdle()
        coordinator.schedule(reason = "presence_write_failed")
        coordinator.cancelAndReset()
        advanceTimeBy(5_000)
        advanceUntilIdle()

        coordinator.schedule(reason = "firebase_connected", resetBackoff = true)
        advanceUntilIdle()

        assertEquals(listOf(0, 0), attempts)
    }

    private fun TestScope.buildCoordinator(
        onAttempt: suspend (attemptIndex: Int, reason: String) -> Unit
    ): ReconnectCoordinator {
        val dispatcher = StandardTestDispatcher(testScheduler)
        return ReconnectCoordinator(
            scope = this,
            onAttempt = onAttempt,
            dispatcher = dispatcher
        )
    }
}
