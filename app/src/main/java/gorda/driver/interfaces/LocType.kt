package gorda.driver.interfaces

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class LocType(
    @SerializedName("name") var name: String = "",
    @SerializedName("lat") var lat: Double = 0.0,
    @SerializedName("long") var long: Double = 0.0
): Serializable {
    companion object {
        const val TAG = "gorda.driver.interfaces.locType"
    }
}