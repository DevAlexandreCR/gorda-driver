package gorda.driver.ui.service

import android.graphics.Color
import androidx.fragment.app.Fragment
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import gorda.driver.BuildConfig
import gorda.driver.R
import gorda.driver.maps.Map
import gorda.driver.maps.MapApiService
import gorda.driver.maps.OnDirectionCompleteListener
import gorda.driver.maps.Routes
import gorda.driver.services.retrofit.RetrofitBase
import gorda.driver.ui.MainViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        const val TAG = "gorda.driver.ui.service.mapFragment"
    }

    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_map, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mainViewModel.currentServiceStartLocation.observe(viewLifecycleOwner) { loc ->
            if (loc.lat != null && loc.long != null) {
                mainViewModel.lastLocation.observe(viewLifecycleOwner) {
                    when(it) {
                        is LocationUpdates.LastLocation -> {
                            val driverLatLng = LatLng(it.location.latitude, it.location.longitude)
                            val startLatLng = LatLng(loc.lat!!, loc.long!!)
                            googleMap.addMarker(MarkerOptions().position(driverLatLng).title(loc.name))
                            googleMap.addMarker(MarkerOptions().position(startLatLng).title(loc.name))
                                ?.showInfoWindow()
                            val bounds = LatLngBounds
                                .Builder()
                                .include(startLatLng)
                                .include(driverLatLng)
                                .build()
                            googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds , 250))
                            mainViewModel.lastLocation.removeObservers(viewLifecycleOwner)

                            val mapService = Map()
                            val url = mapService.getDirectionURL(driverLatLng, startLatLng, BuildConfig.MAPS_API_KEY)
                            getDirections(url, object: OnDirectionCompleteListener {
                                override fun onSuccess(routes: ArrayList<Routes>) {
                                    val path =  ArrayList<LatLng>()
                                    for (i in 0 until routes[0].legs[0].steps.size) {
                                        val decoded = mapService.decodePolyline(routes[0].legs[0].steps[i].polyline.points)
                                        path.addAll(decoded)
                                    }
                                    val lineOptions = PolylineOptions()
                                    lineOptions.addAll(path)
                                    lineOptions.width(10F)
                                    lineOptions.color(Color.GREEN)
                                    lineOptions.geodesic(true)
                                    googleMap.addPolyline(lineOptions)
                                }

                                override fun onFailure() {
                                    requireActivity().runOnUiThread {
                                        Toast.makeText(context, R.string.no_routes_available, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            })
                        }
                    }
                }
            }
        }
    }

    private fun getDirections(url: String, listener: OnDirectionCompleteListener) {
        CoroutineScope(Dispatchers.IO).launch {
            val cal = RetrofitBase.getRetrofit()
                .create(MapApiService::class.java)
                .getDirections(url)
            if (cal.isSuccessful) {
                cal.body()?.let {
                    if (it.routes.size > 0) {
                        listener.onSuccess(it.routes)
                    } else {
                        listener.onFailure()
                    }
                }
            } else {
                listener.onFailure()
            }
        }
    }
}