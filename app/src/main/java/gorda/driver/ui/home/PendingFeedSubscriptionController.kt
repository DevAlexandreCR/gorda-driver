package gorda.driver.ui.home

import gorda.driver.repositories.ServiceObserverHandle

internal class PendingFeedSubscriptionController(
    private val startObservation: () -> ServiceObserverHandle
) {
    private var activeHandle: ServiceObserverHandle? = null

    fun start() {
        if (activeHandle != null) {
            return
        }

        activeHandle = startObservation()
    }

    fun stop() {
        activeHandle?.dispose()
        activeHandle = null
    }

    fun restart() {
        if (activeHandle == null) {
            return
        }

        activeHandle?.dispose()
        activeHandle = startObservation()
    }
}
