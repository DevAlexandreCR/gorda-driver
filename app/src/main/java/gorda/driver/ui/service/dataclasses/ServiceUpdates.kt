package gorda.driver.ui.service.dataclasses

import gorda.driver.interfaces.LocType
import gorda.driver.maps.Distance
import gorda.driver.maps.Duration
import gorda.driver.models.Driver
import gorda.driver.models.Service

sealed class ServiceUpdates {
    data class SetList(var services: MutableList<Service>): ServiceUpdates()

    data class StopListen(var stop: Boolean = true): ServiceUpdates()

    data class Apply(var service: Service, var driver: Driver): ServiceUpdates()

    data class StarLoc(var starLoc: LocType): ServiceUpdates()

    data class DistanceTime(var distance: Distance, val time: Duration): ServiceUpdates()

    data class Status(var status: String): ServiceUpdates()

    companion object {
        fun setList(services: MutableList<Service>): SetList = SetList(services)

        fun stopListen(): StopListen = StopListen()

        fun setServiceApply(service: Service, driver: Driver): Apply = Apply(service, driver)

        fun setStarLoc(starLoc: LocType): StarLoc = StarLoc(starLoc)

        fun distanceTime(distance: Distance, time: Duration): DistanceTime = DistanceTime(distance, time)

        fun status(status: String): Status = Status(status)
    }
}