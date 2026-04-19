package gorda.driver.ui.home

import gorda.driver.repositories.ServiceObserverHandle
import org.junit.Assert.assertEquals
import org.junit.Test

class PendingFeedSubscriptionControllerTest {

    @Test
    fun repeatedStartKeepsSingleActiveHandleUntilStop() {
        var started = 0
        var disposed = 0
        val controller = PendingFeedSubscriptionController {
            started += 1
            ServiceObserverHandle {
                disposed += 1
            }
        }

        controller.start()
        controller.start()
        controller.stop()
        controller.stop()

        assertEquals(1, started)
        assertEquals(1, disposed)
    }
}
