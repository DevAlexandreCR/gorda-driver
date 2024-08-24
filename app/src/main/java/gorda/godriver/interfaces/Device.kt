package gorda.godriver.interfaces

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Device(
    @SerializedName("id") override var id: String = "",
    @SerializedName("name") override var name: String = "",
): Serializable, DeviceInterface {
}