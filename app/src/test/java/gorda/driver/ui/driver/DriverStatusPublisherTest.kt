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
            hasTransportNetwork = true,
            firebaseConnected = true
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

    @Test
    fun reconnectingWithoutFirebaseSocketKeepsFeedConnected() {
        val publisher = DriverStatusPublisher()

        val updates = publisher.updatesFor(
            MainViewModel.DriverPresenceState(
                desiredOnline = true,
                actualOnline = false,
                phase = MainViewModel.DriverPresencePhase.RECONNECTING,
                hasTransportNetwork = false,
                firebaseConnected = false
            )
        )

        assertEquals(
            listOf(
                DriverUpdates.connecting(true),
                DriverUpdates.setConnected(true)
            ),
            updates
        )
    }

    @Test
    fun waitingForFirebaseSocketKeepsFeedConnectedWithoutConnectingSpinner() {
        val publisher = DriverStatusPublisher()

        val updates = publisher.updatesFor(
            MainViewModel.DriverPresenceState(
                desiredOnline = true,
                actualOnline = false,
                phase = MainViewModel.DriverPresencePhase.WAITING_FOR_FIREBASE_SOCKET,
                hasTransportNetwork = true,
                firebaseConnected = false
            )
        )

        assertEquals(
            listOf(
                DriverUpdates.connecting(false),
                DriverUpdates.setConnected(true)
            ),
            updates
        )
    }

    @Test
    fun waitingForPresenceAckWithFirebaseSocketPublishesRecoverableFeed() {
        val publisher = DriverStatusPublisher()

        val updates = publisher.updatesFor(
            MainViewModel.DriverPresenceState(
                desiredOnline = true,
                actualOnline = false,
                phase = MainViewModel.DriverPresencePhase.WAITING_FOR_PRESENCE_ACK,
                hasTransportNetwork = true,
                firebaseConnected = true
            )
        )

        assertEquals(
            listOf(
                DriverUpdates.connecting(true),
                DriverUpdates.setConnected(true)
            ),
            updates
        )
    }

    @Test
    fun precheckingDoesNotPublishConnectedEvenWhenFirebaseSocketIsUp() {
        val publisher = DriverStatusPublisher()

        val updates = publisher.updatesFor(
            MainViewModel.DriverPresenceState(
                desiredOnline = true,
                actualOnline = false,
                phase = MainViewModel.DriverPresencePhase.PRECHECKING,
                hasTransportNetwork = true,
                firebaseConnected = true
            )
        )

        assertEquals(
            listOf(
                DriverUpdates.connecting(true),
                DriverUpdates.setConnected(false)
            ),
            updates
        )
    }
}
