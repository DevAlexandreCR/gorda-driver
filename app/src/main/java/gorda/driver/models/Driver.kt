package gorda.driver.models

import com.google.android.gms.tasks.Task
import gorda.driver.interfaces.DriverInterface
import gorda.driver.interfaces.LocInterface
import gorda.driver.interfaces.VehicleInterface
import gorda.driver.repositories.DriverRepository
import java.io.Serializable

class Driver : DriverInterface, Serializable {
    companion object {
        const val TAG = "gorda.driver.models.Driver"
        const val DRIVER_KEY = "driver_id"
    }

    override var id: String? = null
    override lateinit var name: String
    override lateinit var email: String
    override lateinit var phone: String
    override lateinit var docType: String
    override lateinit var document: String
    override var photoUrl: String? = null
    override lateinit var vehicle: VehicleInterface
    override var enabled_at: Int? = null
    override var created_at: Int = 0

    fun connect(location: LocInterface): Task<Void> {
        return DriverRepository.connect(this, location)
    }

    fun disconnect(): Task<Void> {
        return DriverRepository.disconnect(this)
    }
}