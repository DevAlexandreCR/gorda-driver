package gorda.driver.models

import com.google.android.gms.tasks.Task
import com.google.gson.annotations.SerializedName
import gorda.driver.interfaces.Device
import gorda.driver.interfaces.DriverInterface
import gorda.driver.interfaces.LocInterface
import gorda.driver.interfaces.Vehicle
import gorda.driver.repositories.DriverRepository
import java.io.Serializable

data class Driver(
    @SerializedName("id") override var id: String = "",
    @SerializedName("name") override var name: String = "",
    @SerializedName("email") override var email: String = "",
    @SerializedName("phone") override var phone: String = "",
    @SerializedName("docType") override var docType: String = "",
    @SerializedName("document") override var document: String = "",
    @SerializedName("photoUrl") override var photoUrl: String = "",
    @SerializedName("enabled_at") override var enabled_at: Int = 0,
    @SerializedName("created_at") override var created_at: Int = 0,
    @SerializedName("device") override var device: Device? = null,
    @SerializedName("vehicle") override var vehicle: Vehicle = Vehicle(),
): DriverInterface, Serializable {
    companion object {
        const val TAG = "gorda.driver.models.Driver"
        const val DRIVER_KEY = "driver_id"
    }

    fun connect(location: LocInterface): Task<Void> {
        return DriverRepository.connect(this, location)
    }

    fun disconnect(): Task<Void> {
        return DriverRepository.disconnect(this.id)
    }
}