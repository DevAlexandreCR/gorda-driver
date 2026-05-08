package gorda.driver.ui

import gorda.driver.models.Service
import gorda.driver.repositories.ServiceObservationResult

internal object ServiceObservationReducer {

    data class CurrentServiceResolution(
        val currentService: Service?,
        val shouldEmitCanceledFeedback: Boolean,
        val terminalServiceId: String?
    )

    fun reduceCurrent(
        result: ServiceObservationResult,
        lastTerminalServiceId: String?
    ): CurrentServiceResolution {
        return when (result) {
            is ServiceObservationResult.Active -> {
                CurrentServiceResolution(
                    currentService = result.service,
                    shouldEmitCanceledFeedback = false,
                    terminalServiceId = null
                )
            }
            is ServiceObservationResult.Terminal -> {
                CurrentServiceResolution(
                    currentService = null,
                    shouldEmitCanceledFeedback = lastTerminalServiceId != result.service.id,
                    terminalServiceId = result.service.id
                )
            }
            ServiceObservationResult.Missing -> {
                CurrentServiceResolution(
                    currentService = null,
                    shouldEmitCanceledFeedback = false,
                    terminalServiceId = lastTerminalServiceId
                )
            }
        }
    }

    fun reduceNext(
        result: ServiceObservationResult,
        driverId: String?,
        currentServiceId: String?
    ): Service? {
        return when (result) {
            is ServiceObservationResult.Active -> {
                if (
                    driverId != null &&
                    driverId == result.service.driver_id &&
                    currentServiceId != result.service.id
                ) {
                    result.service
                } else {
                    null
                }
            }
            is ServiceObservationResult.Terminal,
            ServiceObservationResult.Missing -> null
        }
    }
}
