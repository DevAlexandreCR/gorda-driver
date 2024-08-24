package gorda.godriver.maps

import com.google.gson.annotations.SerializedName


data class MapData(
    @SerializedName("status") var status: String = "",
    @SerializedName("routes") var routes: ArrayList<Routes> = ArrayList(),
    @SerializedName("error_message") var error_message: String = ""
)

data class Routes (
    @SerializedName("legs") var legs: ArrayList<Legs> = ArrayList()
)

data class Legs (
    @SerializedName("distance") var distance: Distance = Distance(),
    @SerializedName("duration") var duration: Duration = Duration(),
    @SerializedName("end_address") var end_address: String = "",
    @SerializedName("start_address") var start_address: String = "",
    @SerializedName("end_location") var end_location: Location = Location(),
    @SerializedName("start_location") var start_location: Location = Location(),
    @SerializedName("steps") var steps: ArrayList<Steps> = ArrayList()
)

data class Steps (
    @SerializedName("distance") var distance: Distance = Distance(),
    @SerializedName("duration") var duration: Duration = Duration(),
    @SerializedName("end_address") var end_address: String = "",
    @SerializedName("start_address") var start_address: String = "",
    @SerializedName("end_location") var end_location: Location = Location(),
    @SerializedName("start_location") var start_location: Location = Location(),
    @SerializedName("polyline") var polyline: PolyLine = PolyLine(),
    @SerializedName("travel_mode") var travel_mode: String = "",
    @SerializedName("maneuver") var maneuver: String = ""
)

data class Duration (
    @SerializedName("text") var text: String = "",
    @SerializedName("value") var value: Int = 0
) {
    fun getDurationString(): String {
        return if (value < 60) {
            value.toString() + "s"
        } else {
            (value / 60).toString() + "m"
        }
    }
}

data class Distance (
    @SerializedName("text") var text: String = "",
    @SerializedName("value") var value: Int = 0
) {
    fun getDistanceString(): String {
        return if (value < 1000) {
            value.toString() + "m"
        } else {
            (value / 1000).toString() + "km"
        }
    }
}

data class PolyLine (
    @SerializedName("points") var points: String = ""
)

data class Location (
    @SerializedName("lat") var lat: String = "",
    @SerializedName("lng") var lng: String = ""
)