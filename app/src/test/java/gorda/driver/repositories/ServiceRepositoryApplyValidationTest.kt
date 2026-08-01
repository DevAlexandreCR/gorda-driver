package gorda.driver.repositories

import gorda.driver.models.Service
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceRepositoryApplyValidationTest {

    @Test
    fun notDirectedWhenDirectedToIsNull() {
        val service = Service(id = "service-1", directed_to = null)

        assertFalse(ServiceRepository.isDirectedToAnotherDriver(service, "driver-1"))
    }

    @Test
    fun notDirectedWhenDirectedToIsEmpty() {
        val service = Service(id = "service-1", directed_to = "")

        assertFalse(ServiceRepository.isDirectedToAnotherDriver(service, "driver-1"))
    }

    @Test
    fun notDirectedWhenDirectedToMatchesApplyingDriver() {
        val service = Service(id = "service-1", directed_to = "driver-1")

        assertFalse(ServiceRepository.isDirectedToAnotherDriver(service, "driver-1"))
    }

    @Test
    fun directedToAnotherDriverWhenDirectedToTargetsSomeoneElse() {
        val service = Service(id = "service-1", directed_to = "driver-2")

        assertTrue(ServiceRepository.isDirectedToAnotherDriver(service, "driver-1"))
    }
}
