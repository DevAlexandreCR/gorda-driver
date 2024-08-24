package gorda.go.interfaces

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class LocType(
    @SerializedName("name") var name: String = "",
    @SerializedName("lat") var lat: Double = 0.0,
    @SerializedName("lng") var lng: Double = 0.0
): Serializable {
    companion object {
        const val TAG = "gorda.go.interfaces.locType"
    }
}