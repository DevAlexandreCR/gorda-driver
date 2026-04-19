package gorda.driver.activity

import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceObserverCoordinatorTest {

    @Test
    fun loadingToLoadedWhileStartedReactivatesObservers() {
        var started = 0
        var stopped = 0
        val coordinator = ServiceObserverCoordinator(
            startObservers = { started += 1 },
            stopObservers = { stopped += 1 }
        )

        coordinator.onActivityStarted()
        coordinator.onDriverNotLoaded()
        coordinator.onDriverLoaded()

        assertEquals(1, started)
        assertEquals(0, stopped)
    }

    @Test
    fun stoppingActivityDeactivatesObservers() {
        var started = 0
        var stopped = 0
        val coordinator = ServiceObserverCoordinator(
            startObservers = { started += 1 },
            stopObservers = { stopped += 1 }
        )

        coordinator.onDriverLoaded()
        coordinator.onActivityStarted()
        coordinator.onActivityStopped()

        assertEquals(1, started)
        assertEquals(1, stopped)
    }
}
