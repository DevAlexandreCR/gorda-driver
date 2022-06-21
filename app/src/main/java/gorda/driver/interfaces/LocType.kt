package gorda.driver.interfaces

import java.io.Serializable

class LocType: Serializable {
    lateinit var name: String
    var lat: Double? = null
    var long: Double? = null

    companion object {
        const val TAG = "gorda.driver.interfaces.locType"
    }
}