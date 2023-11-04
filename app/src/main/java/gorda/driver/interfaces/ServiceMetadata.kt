package gorda.driver.interfaces

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class ServiceMetadata(
    @SerializedName("arrived_at") var arrived_at: Long? = null,
    @SerializedName("start_trip_at") var start_trip_at: Long? = null,
    @SerializedName("end_trip_at") var end_trip_at: Long? = null,
    @SerializedName("trip_distance") var trip_distance: Int? = null,
    @SerializedName("trip_fee") var trip_fee: Int? = null
): Serializable