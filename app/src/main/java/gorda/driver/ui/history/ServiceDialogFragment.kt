package gorda.driver.ui.history

import android.app.AlertDialog
import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.DialogFragment
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.MapView
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import gorda.driver.R
import gorda.driver.models.Service
import gorda.driver.utils.DateHelper
import gorda.driver.utils.StringHelper
import org.json.JSONObject

class ServiceDialogFragment(private val service: Service): DialogFragment(), OnMapReadyCallback {

    private lateinit var mapView: MapView
    private var googleMap: GoogleMap? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.service_history_dialog, null)

        val createdAt: TextView = view.findViewById(R.id.dialog_created_at)
        val name: TextView = view.findViewById(R.id.dialog_name)
        val place: TextView = view.findViewById(R.id.dialog_place)
        val price: TextView = view.findViewById(R.id.dialog_price)
        val status: TextView = view.findViewById(R.id.dialog_status)
        val comment: TextView = view.findViewById(R.id.dialog_comment)
        val multiplier: TextView = view.findViewById(R.id.dialog_multipler)

        val fee = service.metadata.trip_fee ?: 0
        mapView = view.findViewById(R.id.dialog_map)
        mapView.onCreate(savedInstanceState)
        mapView.getMapAsync(this)

        createdAt.text = DateHelper.formatDateFromSeconds(service.created_at)
        name.text = service.name
        place.text = service.start_loc.name
        price.text = view.context.getString(R.string.AmountCurrency, fee.toString())
        status.text = StringHelper.formatStatus(service.status, view.context)
        comment.text = service.comment
        multiplier.text = service.metadata.trip_multiplier.toString()

        builder.setView(view)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }

        return builder.create()
    }

    override fun onMapReady(googlemap: GoogleMap) {
        googleMap = googlemap
        var routePoints = ArrayList<LatLng>()
        if (service.metadata.route != null) {
            routePoints = parseRoute(service.metadata.route!!)
        } else {
            val latLng = LatLng(service.start_loc.lat, service.start_loc.lng)
            routePoints.add(latLng)
        }

        drawRoute(routePoints)
    }

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) {
            mapView.onStart()
        }
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) {
            mapView.onResume()
        }
    }

    override fun onPause() {
        if (::mapView.isInitialized) {
            mapView.onPause()
        }
        super.onPause()
    }

    override fun onStop() {
        if (::mapView.isInitialized) {
            mapView.onStop()
        }
        super.onStop()
    }

    override fun onDestroyView() {
        if (::mapView.isInitialized) {
            mapView.onDestroy()
        }
        googleMap = null
        super.onDestroyView()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapView.isInitialized) {
            mapView.onLowMemory()
        }
    }

    private fun parseRoute(jsonString: String): ArrayList<LatLng> {
        val routePoints = ArrayList<LatLng>()
        val json = JSONObject(jsonString)
        json.keys().forEach { key ->
            val point = json.getJSONObject(key)
            val lat = point.getDouble("lat")
            val lng = point.getDouble("lng")
            routePoints.add(LatLng(lat, lng))
        }
        return routePoints
    }

    private fun drawRoute(routePoints: ArrayList<LatLng>) {
        val polylineOptions = PolylineOptions()
        polylineOptions.addAll(routePoints)
        googleMap?.addPolyline(polylineOptions)

        if (routePoints.isNotEmpty()) {
            if (routePoints.size < 2) {
                googleMap?.moveCamera(CameraUpdateFactory.newLatLngZoom(routePoints.first(), 14f))

                googleMap?.addMarker(
                    MarkerOptions()
                        .position(routePoints.first())
                        .icon(
                            BitmapDescriptorFactory.fromBitmap(getResizedBitmap(R.mipmap.ic_loc_a))
                        )
                )
            } else {
                addMarkers(routePoints)
                val boundsBuilder = LatLngBounds.builder()
                boundsBuilder.include(routePoints.first())
                boundsBuilder.include(routePoints.last())
                val bounds = boundsBuilder.build()
                fitRouteBounds(bounds, routePoints.first())
            }
        }
    }

    private fun fitRouteBounds(bounds: LatLngBounds, fallbackPoint: LatLng) {
        mapView.post {
            val map = googleMap ?: return@post
            val width = mapView.width
            val height = mapView.height

            if (width > 0 && height > 0) {
                val padding = resources.getDimensionPixelSize(R.dimen.map_margin_small)
                val cameraUpdate = CameraUpdateFactory.newLatLngBounds(bounds, width, height, padding)
                map.animateCamera(cameraUpdate)
            } else {
                map.moveCamera(CameraUpdateFactory.newLatLngZoom(fallbackPoint, 14f))
            }
        }
    }

    private fun getResizedBitmap(drawableId: Int): Bitmap {
        val bitmapDrawable = ResourcesCompat.getDrawable(resources, drawableId, null) as BitmapDrawable
        val bitmap = bitmapDrawable.bitmap
        return Bitmap.createScaledBitmap(bitmap, 50, 50, false)
    }

    private fun addMarkers(routePoints: List<LatLng>) {
        if (routePoints.isNotEmpty()) {
            googleMap?.addMarker(
                MarkerOptions()
                    .position(routePoints.first())
                    .icon(
                        BitmapDescriptorFactory.fromBitmap(getResizedBitmap(R.mipmap.ic_loc_a))
                    )
            )

            googleMap?.addMarker(
                MarkerOptions()
                    .position(routePoints.last())
                    .icon(
                        BitmapDescriptorFactory.fromBitmap(getResizedBitmap(R.mipmap.ic_loc_b))
                    )
            )
        }
    }
}
