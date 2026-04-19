package gorda.driver.activity

internal class ServiceObserverCoordinator(
    private val startObservers: () -> Unit,
    private val stopObservers: () -> Unit
) {
    private var isActivityStarted = false
    private var isDriverLoaded = false
    private var observersActive = false

    fun onActivityStarted() {
        isActivityStarted = true
        sync()
    }

    fun onActivityStopped() {
        isActivityStarted = false
        sync()
    }

    fun onDriverLoaded() {
        isDriverLoaded = true
        sync()
    }

    fun onDriverNotLoaded() {
        isDriverLoaded = false
        sync()
    }

    private fun sync() {
        val shouldBeActive = isActivityStarted && isDriverLoaded
        if (shouldBeActive == observersActive) {
            return
        }

        observersActive = shouldBeActive
        if (shouldBeActive) {
            startObservers()
        } else {
            stopObservers()
        }
    }
}
