package gorda.driver.maps

import android.graphics.Color
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.PolylineOptions

class Map {

    fun getDirectionURL(origin: LatLng, dest:LatLng, secret: String) : String{
        return "directions/json?origin=${origin.latitude},${origin.longitude}" +
                "&destination=${dest.latitude},${dest.longitude}" +
                "&sensor=false" +
                "&mode=driving" +
                "&key=$secret"
    }

    fun makePolylineOptions(routes: ArrayList<Routes>): PolylineOptions {
        val path =  ArrayList<LatLng>()
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
            val latLng = LatLng((lat.toDouble() / 1E5),(lng.toDouble() / 1E5))
            poly.add(latLng)
        }
        return poly
    }
}