package gorda.driver.ui

import gorda.driver.models.Service
import gorda.driver.repositories.ServiceObservationResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ServiceObservationReducerTest {

    @Test
    fun terminalCanceledCurrentServiceClearsActiveStateAndEmitsCanceledFeedback() {
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
        assertEquals(ServiceObservationReducer.TerminalFeedback.CANCELED, resolution.terminalFeedback)
        assertEquals("service-1", resolution.terminalServiceId)
    }

    @Test
    fun terminalTerminatedCurrentServiceClearsActiveStateAndEmitsFinishedFeedback() {
        val service = Service(
            id = "service-1",
            status = Service.STATUS_TERMINATED,
            driver_id = "driver-1"
        )

        val resolution = ServiceObservationReducer.reduceCurrent(
            result = ServiceObservationResult.Terminal(service),
            lastTerminalServiceId = null
        )

        assertNull(resolution.currentService)
        assertEquals(ServiceObservationReducer.TerminalFeedback.FINISHED, resolution.terminalFeedback)
        assertEquals("service-1", resolution.terminalServiceId)
    }

    @Test
    fun activeCurrentServiceEmitsNoFeedback() {
        val service = Service(
            id = "service-1",
            status = Service.STATUS_IN_PROGRESS,
            driver_id = "driver-1"
        )

        val resolution = ServiceObservationReducer.reduceCurrent(
            result = ServiceObservationResult.Active(service),
            lastTerminalServiceId = null
        )

        assertEquals(service, resolution.currentService)
        assertEquals(ServiceObservationReducer.TerminalFeedback.NONE, resolution.terminalFeedback)
        assertNull(resolution.terminalServiceId)
    }

    @Test
    fun missingCurrentServiceEmitsNoFeedback() {
        val resolution = ServiceObservationReducer.reduceCurrent(
            result = ServiceObservationResult.Missing,
            lastTerminalServiceId = "service-1"
        )

        assertNull(resolution.currentService)
        assertEquals(ServiceObservationReducer.TerminalFeedback.NONE, resolution.terminalFeedback)
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
        assertEquals(ServiceObservationReducer.TerminalFeedback.NONE, resolution.terminalFeedback)
        assertEquals("service-1", resolution.terminalServiceId)
    }

    @Test
    fun repeatedTerminalFinishedCurrentServiceDoesNotEmitDuplicateFeedback() {
        val service = Service(
            id = "service-1",
            status = Service.STATUS_TERMINATED,
            driver_id = "driver-1"
        )

        val resolution = ServiceObservationReducer.reduceCurrent(
            result = ServiceObservationResult.Terminal(service),
            lastTerminalServiceId = "service-1"
        )

        assertNull(resolution.currentService)
        assertEquals(ServiceObservationReducer.TerminalFeedback.NONE, resolution.terminalFeedback)
        assertEquals("service-1", resolution.terminalServiceId)
    }
}
