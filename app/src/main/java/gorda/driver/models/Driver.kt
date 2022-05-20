package gorda.driver.models

import gorda.driver.interfaces.DriverInterface
import gorda.driver.repositories.DriverRepository

class Driver() : DriverInterface {
    override var id: String? = null
    override lateinit var name: String
    override lateinit var email: String
    override lateinit var phone: String
    override lateinit var docType: String
    override lateinit var document: String
    override var photoUrl: String? = null
//    override lateinit var vehicle: VehicleInterface
    override var enabled_at: Int? = null
    override var created_at: Int = 0

    fun connect(): Unit {
        DriverRepository.connect(this)
    }
}