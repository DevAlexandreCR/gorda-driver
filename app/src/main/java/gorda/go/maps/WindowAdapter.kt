package gorda.go.maps

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.Marker
import gorda.go.R

class WindowAdapter(val context: Context): GoogleMap.InfoWindowAdapter {

    override fun getInfoContents(marker: Marker): View? {
        val infoView = LayoutInflater.from(context).inflate(R.layout.info_window, null)
        val infoWindowData: InfoWindowData = marker.tag as InfoWindowData

        infoView.findViewById<TextView>(R.id.location_name).text = infoWindowData.name
        infoView.findViewById<TextView>(R.id.location_distance).text = infoWindowData.distance
        infoView.findViewById<TextView>(R.id.location_time).text = infoWindowData.time

        return infoView
    }

    override fun getInfoWindow(marker: Marker): View? {
        return null
    }
}