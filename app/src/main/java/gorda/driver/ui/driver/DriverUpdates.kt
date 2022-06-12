package gorda.driver.ui.driver

import gorda.driver.models.Driver

sealed class DriverUpdates {
    data class SetDriver(var driver: Driver): DriverUpdates()

    data class IsConnected(var connected: Boolean): DriverUpdates()

    data class Connecting(var connecting: Boolean): DriverUpdates()

    companion object {
        fun setConnected(connected: Boolean): DriverUpdates = IsConnected(connected)

        fun connecting(connecting: Boolean): DriverUpdates = Connecting(connecting)

        fun setDriver(driver: Driver): DriverUpdates = SetDriver(driver)
    }
}
