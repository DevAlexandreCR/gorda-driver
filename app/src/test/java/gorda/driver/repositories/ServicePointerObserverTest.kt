package gorda.driver.repositories

import org.junit.Assert.assertEquals
import org.junit.Test

class ServicePointerObserverTest {

    @Test
    fun pointerSwapDisposesPreviousObservedServiceHandle() {
        val disposedServiceIds = mutableListOf<String>()
        val observer = ServicePointerObserver(
            observeService = { serviceId, _ ->
                ServiceObserverHandle {
                    disposedServiceIds += serviceId
                }
            },
            onMissing = {}
        )

        observer.onPointerChanged("service-1")
        observer.onPointerChanged("service-2")
        observer.dispose()

        assertEquals(
            listOf("service-1", "service-2"),
            disposedServiceIds
        )
    }

    @Test
    fun samePointerCanBeReobservedAfterObservedServiceListenerCloses() {
        val observedServiceIds = mutableListOf<String>()
        var onObservedServiceClosed: (() -> Unit)? = null
        val observer = ServicePointerObserver(
            observeService = { serviceId, onClosed ->
                observedServiceIds += serviceId
                onObservedServiceClosed = onClosed
                ServiceObserverHandle {}
            },
            onMissing = {}
        )

        observer.onPointerChanged("service-1")
        onObservedServiceClosed?.invoke()
        observer.onPointerChanged("service-1")

        assertEquals(listOf("service-1", "service-1"), observedServiceIds)
    }
}
