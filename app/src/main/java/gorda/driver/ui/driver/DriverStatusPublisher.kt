package gorda.driver.ui.driver

import gorda.driver.ui.MainViewModel

internal data class DriverStatusSnapshot(
    val connecting: Boolean,
    val connected: Boolean
)

internal class DriverStatusPublisher {
    private var lastPublishedStatus: DriverStatusSnapshot? = null

    fun updatesFor(state: MainViewModel.DriverPresenceState): List<DriverUpdates> {
        val nextStatus = DriverStatusSnapshot(
            connecting = state.connecting,
            connected = state.actualOnline || (state.desiredOnline && state.connecting)
        )

        if (lastPublishedStatus == nextStatus) {
            return emptyList()
        }

        lastPublishedStatus = nextStatus
        return listOf(
            DriverUpdates.connecting(nextStatus.connecting),
            DriverUpdates.setConnected(nextStatus.connected)
        )
    }
}
