package gorda.driver.repositories

import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceRepositoryTransitionValidationTest {

    @Test
    fun startValidationFailsWhenAssignedDriverDoesNotMatch() {
        val service = Service(
            id = "service-1",
            status = Service.STATUS_IN_PROGRESS,
            driver_id = "driver-2"
        )

        val result = ServiceRepository.validateStartTransition(service, "driver-1")

        assertEquals(ServiceRepository.StartTransitionValidation.WRONG_DRIVER, result)
    }

    @Test
    fun startValidationFailsWhenTripAlreadyStarted() {
        val service = Service(
            id = "service-1",
            status = Service.STATUS_IN_PROGRESS,
            driver_id = "driver-1",
            metadata = ServiceMetadata(start_trip_at = 1000L)
        )

        val result = ServiceRepository.validateStartTransition(service, "driver-1")

        assertEquals(ServiceRepository.StartTransitionValidation.ALREADY_STARTED, result)
    }

    @Test
    fun endValidationFailsWhenTripAlreadyTerminated() {
        val service = Service(
            id = "service-1",
            status = Service.STATUS_TERMINATED,
            driver_id = "driver-1",
            metadata = ServiceMetadata(start_trip_at = 1000L, end_trip_at = 1200L)
        )

        val result = ServiceRepository.validateEndTransition(service, "driver-1")

        assertEquals(ServiceRepository.EndTransitionValidation.ALREADY_TERMINATED, result)
    }

    @Test
    fun endValidationFailsWhenTripHasNotStarted() {
        val service = Service(
            id = "service-1",
            status = Service.STATUS_IN_PROGRESS,
            driver_id = "driver-1",
            metadata = ServiceMetadata(start_trip_at = null)
        )

        val result = ServiceRepository.validateEndTransition(service, "driver-1")

        assertEquals(ServiceRepository.EndTransitionValidation.NOT_STARTED, result)
    }

    @Test
    fun validationsAcceptValidCurrentService() {
        val service = Service(
            id = "service-1",
            status = Service.STATUS_IN_PROGRESS,
            driver_id = "driver-1",
            metadata = ServiceMetadata(arrived_at = 900L, start_trip_at = null)
        )
        val startedService = service.copy(
            metadata = service.metadata.copy(start_trip_at = 1000L)
        )

        assertEquals(
            ServiceRepository.StartTransitionValidation.VALID,
            ServiceRepository.validateStartTransition(service, "driver-1")
        )
        assertEquals(
            ServiceRepository.EndTransitionValidation.VALID,
            ServiceRepository.validateEndTransition(startedService, "driver-1")
        )
    }
}
