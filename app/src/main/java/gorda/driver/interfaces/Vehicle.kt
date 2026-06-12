package gorda.driver.interfaces

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class VehicleColor(
    @SerializedName("hex") val hex: String? = null,
    @SerializedName("name") val name: String? = null,
) : Serializable

data class Vehicle(
    @SerializedName("id") var id: String = "",
    @SerializedName("brand") var brand: String = "",
    @SerializedName("model") var model: String = "",
    @SerializedName("photoUrl") var photoUrl: String = "",
    @SerializedName("plate") var plate: String = "",
    @SerializedName("color") var color: VehicleColor? = null,
    @SerializedName("is_selectable") var is_selectable: Boolean = true,
    @SerializedName("is_selected") var is_selected: Boolean = false,
): Serializable