package gorda.driver.ui.service.dataclasses

import gorda.driver.interfaces.LocType
import gorda.driver.models.Service

sealed class ServiceUpdates {
    data class SetList(var services: MutableList<Service>): ServiceUpdates()

    data class StopListen(var stop: Boolean = true): ServiceUpdates()

    data class Apply(var serviceId: String): ServiceUpdates()

    data class StarLoc(var starLoc: LocType): ServiceUpdates()

    data class DistanceTime(var distance: String, val time: String): ServiceUpdates()

    companion object {
        fun setList(services: MutableList<Service>): SetList = SetList(services)

        fun stopListen(): StopListen = StopListen()

        fun setServiceApply(serviceId: String): Apply = Apply(serviceId)

        fun setStarLoc(starLoc: LocType): StarLoc = StarLoc(starLoc)

        fun distanceTime(distance: String, time: String): DistanceTime = DistanceTime(distance, time)
    }
}