package gorda.driver.ui.driver

import gorda.driver.ui.MainViewModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DriverStatusPublisherTest {

    @Test
    fun repeatedLogicalStatusDoesNotEmitDuplicateUpdates() {
        val publisher = DriverStatusPublisher()
        val initialState = MainViewModel.DriverPresenceState(
            desiredOnline = true,
            actualOnline = true,
            phase = MainViewModel.DriverPresencePhase.CONNECTED,
            hasNetwork = true
        )
        val heartbeatRefreshedState = initialState.copy(lastError = "heartbeat_refresh")

        val firstUpdates = publisher.updatesFor(initialState)
        val secondUpdates = publisher.updatesFor(heartbeatRefreshedState)

        assertEquals(
            listOf(
                DriverUpdates.connecting(false),
                DriverUpdates.setConnected(true)
            ),
            firstUpdates
        )
        assertTrue(secondUpdates.isEmpty())
    }
}
