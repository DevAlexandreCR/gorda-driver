package gorda.driver.interfaces

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class Vehicle(
    @SerializedName("brand") var brand: String = "",
    @SerializedName("model") var model: String = "",
    @SerializedName("photoUrl") var photoUrl: String = "",
    @SerializedName("plate") var plate: String = "",
): Serializable {
}