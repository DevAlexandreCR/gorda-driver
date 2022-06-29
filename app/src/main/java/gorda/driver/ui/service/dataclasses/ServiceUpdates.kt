package gorda.driver.ui.service.dataclasses

import gorda.driver.models.Service

sealed class ServiceUpdates {
    data class SetList(var services: MutableList<Service>): ServiceUpdates()

    data class StopListen(var stop: Boolean = true): ServiceUpdates()

    companion object {
        fun setList(services: MutableList<Service>): ServiceUpdates = SetList(services)

        fun stopListen(): StopListen = StopListen()
    }
}