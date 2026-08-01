package gorda.driver.ui.service

import gorda.driver.models.Service
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServicesEventListenerVisibilityTest {

    @Test
    fun visibleWhenDirectedToIsNull() {
        val service = Service(id = "service-1", directed_to = null)

        assertTrue(ServicesEventListener.isVisibleToDriver(service, "driver-1"))
    }

    @Test
    fun visibleWhenDirectedToIsEmpty() {
        val service = Service(id = "service-1", directed_to = "")

        assertTrue(ServicesEventListener.isVisibleToDriver(service, "driver-1"))
    }

    @Test
    fun visibleWhenDirectedToMatchesDriverId() {
        val service = Service(id = "service-1", directed_to = "driver-1")

        assertTrue(ServicesEventListener.isVisibleToDriver(service, "driver-1"))
    }

    @Test
    fun notVisibleWhenDirectedToTargetsAnotherDriver() {
        val service = Service(id = "service-1", directed_to = "driver-2")

        assertFalse(ServicesEventListener.isVisibleToDriver(service, "driver-1"))
    }

    @Test
    fun notVisibleWhenDirectedToIsSetAndDriverIdIsNull() {
        val service = Service(id = "service-1", directed_to = "driver-2")

        assertFalse(ServicesEventListener.isVisibleToDriver(service, null))
    }
}
