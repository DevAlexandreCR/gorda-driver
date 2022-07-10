package gorda.driver.interfaces

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class ServiceMetadata(
    @SerializedName("arrived_at") var arrivedAt: Long? = null,
    @SerializedName("start_trip_at") var startTripAt: Long? = null,
    @SerializedName("end_trip_at") var endTripAt: Long? = null
): Serializable