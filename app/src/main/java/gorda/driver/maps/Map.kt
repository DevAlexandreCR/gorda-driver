package gorda.driver.maps

import android.annotation.SuppressLint
import android.graphics.Color
import android.location.Location
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions

object Map {
    private val tmpDistance = FloatArray(1)

    fun calculateDistance(latLng1: LatLng, latLng2: LatLng): Float {
        val startLoc = Location("last")
        startLoc.latitude = latLng1.latitude
        startLoc.longitude = latLng1.longitude
        val endLoc = Location("last")
        endLoc.latitude = latLng2.latitude
        endLoc.longitude = latLng2.longitude

        Location.distanceBetween(
            startLoc.latitude, startLoc.longitude,
            endLoc.latitude, endLoc.longitude,
            tmpDistance,
        )

        return tmpDistance[0]
    }

    @SuppressLint("DefaultLocale")
    fun distanceToString(distance: Float): String {
        return if (distance > 1000) String.format("%.2f", (distance / 1000)) + "km"
        else String.format("%.2f", distance) + "m"
    }

    fun calculateTime(distance: Float): Float {
        return (distance / 5)
    }

    fun getTimeString(time: Float): String {
        return if (time < 60) {
            "1m"
        } else {
            (time / 60).toString() + "m"
        }
    }

    fun getDirectionURL(origin: LatLng, dest: LatLng, secret: String): String {
        return "directions/json?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${dest.latitude},${dest.longitude}" +
                "&sensor=false" +
                "&mode=driving" +
                "&key=$secret"
    }

    fun makePolylineOptions(routes: ArrayList<Routes>): PolylineOptions {
        val path = ArrayList<LatLng>()
        for (i in 0 until routes[0].legs[0].steps.size) {
            val decoded = decodePolyline(routes[0].legs[0].steps[i].polyline.points)
            path.addAll(decoded)
        }
        val lineOptions = PolylineOptions()
        lineOptions.addAll(path)
        lineOptions.width(10F)
        lineOptions.color(Color.GREEN)
        lineOptions.geodesic(true)

        return lineOptions
    }

    private fun decodePolyline(encoded: String): List<LatLng> {
        val poly = ArrayList<LatLng>()
        var index = 0
        val len = encoded.length
        var lat = 0
        var lng = 0
        while (index < len) {
            var b: Int
            var shift = 0
            var result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlat = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lat += dlat
            shift = 0
            result = 0
            do {
                b = encoded[index++].code - 63
                result = result or (b and 0x1f shl shift)
                shift += 5
            } while (b >= 0x20)
            val dlng = if (result and 1 != 0) (result shr 1).inv() else result shr 1
            lng += dlng
            val latLng = LatLng((lat.toDouble() / 1E5), (lng.toDouble() / 1E5))
            poly.add(latLng)
        }

        return poly
    }

}