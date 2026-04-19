package gorda.driver.repositories

class ServiceObserverHandle(private val disposeAction: () -> Unit) {
    private var disposed = false

    fun dispose() {
        if (disposed) {
            return
        }

        disposed = true
        disposeAction()
    }

    companion object {
        fun empty(): ServiceObserverHandle = ServiceObserverHandle {}
    }
}

internal class ServicePointerObserver(
    private val observeService: (serviceId: String) -> ServiceObserverHandle,
    private val onMissing: () -> Unit
) {
    private var currentServiceId: String? = null
    private var currentServiceHandle: ServiceObserverHandle? = null

    fun onPointerChanged(serviceId: String?) {
        if (serviceId.isNullOrBlank()) {
            clearCurrentService(notifyMissing = true)
            return
        }

        if (currentServiceId == serviceId && currentServiceHandle != null) {
            return
        }

        currentServiceHandle?.dispose()
        currentServiceId = serviceId
        currentServiceHandle = observeService(serviceId)
    }

    fun dispose() {
        clearCurrentService(notifyMissing = false)
    }

    private fun clearCurrentService(notifyMissing: Boolean) {
        currentServiceHandle?.dispose()
        currentServiceHandle = null
        currentServiceId = null

        if (notifyMissing) {
            onMissing()
        }
    }
}
