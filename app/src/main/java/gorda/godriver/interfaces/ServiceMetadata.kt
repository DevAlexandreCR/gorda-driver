package gorda.godriver.interfaces

import com.google.android.gms.maps.model.LatLng
import com.google.firebase.database.IgnoreExtraProperties
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.Serializable

@IgnoreExtraProperties
data class ServiceMetadata(
    @SerializedName("arrived_at") var arrived_at: Long? = null,
    @SerializedName("start_trip_at") var start_trip_at: Long? = null,
    @SerializedName("end_trip_at") var end_trip_at: Long? = null,
    @SerializedName("trip_distance") var trip_distance: Int? = 0,
    @SerializedName("trip_fee") var trip_fee: Int? = 0,
    @SerializedName("trip_multiplier") var trip_multiplier: Double? = 1.0,
    @SerializedName("route") var route: String? = null
): Serializable {

    companion object {
        fun serializeRoute(points: ArrayList<LatLng>): String {
            val serializedMap = HashMap<Int, HashMap<String, Double>>()
            for ((index, latLng) in points.withIndex()) {
                serializedMap[index] = serializeLatLng(latLng)
            }

            return Gson().toJson(serializedMap)
        }

        private fun serializeLatLng(latLng: LatLng): HashMap<String, Double> {
            val map = HashMap<String, Double>()
            map["lat"] = latLng.latitude
            map["lng"] = latLng.longitude
            return map
        }
    }
}