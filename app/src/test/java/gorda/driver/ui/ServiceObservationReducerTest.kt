package gorda.driver.ui

import gorda.driver.models.Service
import gorda.driver.repositories.ServiceObservationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceObservationReducerTest {

    @Test
    fun terminalCurrentServiceClearsActiveStateAndEmitsCanceledFeedback() {
        val service = Service(
            id = "service-1",
            status = Service.STATUS_CANCELED,
            driver_id = "driver-1"
        )

        val resolution = ServiceObservationReducer.reduceCurrent(
            result = ServiceObservationResult.Terminal(service),
            lastTerminalServiceId = null
        )

        assertNull(resolution.currentService)
        assertTrue(resolution.shouldEmitCanceledFeedback)
        assertEquals("service-1", resolution.terminalServiceId)
    }

    @Test
    fun missingConnectionServiceClearsQueuedState() {
        val nextService = ServiceObservationReducer.reduceNext(
            result = ServiceObservationResult.Missing,
            driverId = "driver-1",
            currentServiceId = "current-service"
        )

        assertNull(nextService)
    }

    @Test
    fun terminalConnectionServiceClearsQueuedState() {
        val service = Service(
            id = "service-2",
            status = Service.STATUS_CANCELED,
            driver_id = "driver-1"
        )

        val nextService = ServiceObservationReducer.reduceNext(
            result = ServiceObservationResult.Terminal(service),
            driverId = "driver-1",
            currentServiceId = "current-service"
        )

        assertNull(nextService)
    }

    @Test
    fun repeatedTerminalCurrentServiceDoesNotEmitDuplicateFeedback() {
        val service = Service(
            id = "service-1",
            status = Service.STATUS_CANCELED,
            driver_id = "driver-1"
        )

        val resolution = ServiceObservationReducer.reduceCurrent(
            result = ServiceObservationResult.Terminal(service),
            lastTerminalServiceId = "service-1"
        )

        assertNull(resolution.currentService)
        assertFalse(resolution.shouldEmitCanceledFeedback)
        assertEquals("service-1", resolution.terminalServiceId)
    }
}
