package gorda.driver.background

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterTickerTest {

    private class FakeScheduler {
        val scheduled = mutableListOf<Runnable>()

        fun post(runnable: Runnable, delayMs: Long) {
            scheduled.add(runnable)
        }

        fun cancel(runnable: Runnable) {
            scheduled.remove(runnable)
        }

        fun runNext() {
            val runnable = scheduled.firstOrNull() ?: return
            runnable.run()
        }
    }

    @Test
    fun repeatedStartLeavesExactlyOneScheduledRunnable() {
        val scheduler = FakeScheduler()
        val ticker = MeterTicker(scheduler::post, scheduler::cancel)

        ticker.start {}
        ticker.start {}

        assertEquals(1, scheduler.scheduled.size)
    }

    @Test
    fun stopRemovesTheRunnableAndSuppressesLaterTicks() {
        val scheduler = FakeScheduler()
        val ticker = MeterTicker(scheduler::post, scheduler::cancel)
        var ticks = 0

        ticker.start { ticks += 1 }
        val leakedRunnable = scheduler.scheduled.first()
        ticker.stop()

        assertTrue(scheduler.scheduled.isEmpty())

        leakedRunnable.run()

        assertEquals(0, ticks)
    }

    @Test
    fun startAfterStopSchedulesAgain() {
        val scheduler = FakeScheduler()
        val ticker = MeterTicker(scheduler::post, scheduler::cancel)

        ticker.start {}
        ticker.stop()
        ticker.start {}

        assertEquals(1, scheduler.scheduled.size)
    }
}
