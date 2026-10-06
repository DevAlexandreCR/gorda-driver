package gorda.driver.ui

import gorda.driver.models.Service
import gorda.driver.repositories.ServiceObservationResult

internal object ServiceObservationReducer {

    internal enum class TerminalFeedback {
        NONE,
        CANCELED,
        FINISHED
    }

    data class CurrentServiceResolution(
        val currentService: Service?,
        val terminalFeedback: TerminalFeedback,
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
                    terminalFeedback = TerminalFeedback.NONE,
                    terminalServiceId = null
                )
            }
            is ServiceObservationResult.Terminal -> {
                val feedback = if (lastTerminalServiceId == result.service.id) {
                    TerminalFeedback.NONE
                } else when (result.service.status) {
                    Service.STATUS_CANCELED -> TerminalFeedback.CANCELED
                    Service.STATUS_TERMINATED -> TerminalFeedback.FINISHED
                    else -> TerminalFeedback.NONE
                }
                CurrentServiceResolution(
                    currentService = null,
                    terminalFeedback = feedback,
                    terminalServiceId = result.service.id
                )
            }
            ServiceObservationResult.Missing -> {
                CurrentServiceResolution(
                    currentService = null,
                    terminalFeedback = TerminalFeedback.NONE,
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
