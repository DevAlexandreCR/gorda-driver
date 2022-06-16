package gorda.driver.interfaces

import java.io.Serializable

class VehicleInterface: Serializable {
    lateinit var brand: String
    lateinit var model: String
    var photoUrl: String? = null
    lateinit var plate: String
}