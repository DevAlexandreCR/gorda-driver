package gorda.driver.ui.service

import androidx.fragment.app.Fragment
import android.os.Bundle
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
import gorda.driver.maps.*
import gorda.driver.maps.Map
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
        val infoWindow = WindowAdapter(requireContext())
        googleMap.setInfoWindowAdapter(infoWindow)
        mainViewModel.currentServiceStartLocation.observe(viewLifecycleOwner) { loc ->
            if (loc.lat != null && loc.long != null) {
                mainViewModel.lastLocation.observe(viewLifecycleOwner) {
                    when(it) {
                        is LocationUpdates.LastLocation -> {
                            val driverLatLng = LatLng(it.location.latitude, it.location.longitude)
                            val startLatLng = LatLng(loc.lat!!, loc.long!!)
                            val driverMarker = googleMap.addMarker(MarkerOptions().position(driverLatLng))
                            driverMarker?.tag = makeInfoWindowData("A")
                            val markerStartAddress = googleMap.addMarker(MarkerOptions().position(startLatLng).title(loc.name))
                            markerStartAddress?.tag = makeInfoWindowData("B")
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
                                    val lineOptions = mapService.makePolylineOptions(routes)
                                    googleMap.addPolyline(lineOptions)
                                    requireActivity().runOnUiThread {
                                        if (markerStartAddress != null) {
                                            markerStartAddress.tag = makeInfoWindowData(
                                                loc.name,
                                                routes[0].legs[0].distance.value.toString(),
                                                routes[0].legs[0].duration.value.toString()
                                            )
                                            markerStartAddress.showInfoWindow()
                                        }
                                    }
                                }

                                override fun onFailure() {
                                    requireActivity().runOnUiThread {
                                        markerStartAddress?.tag = makeInfoWindowData(
                                            loc.name,
                                        )
                                        markerStartAddress?.showInfoWindow()
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

    private fun makeInfoWindowData(name: String = "", distance: String = "", time: String = ""): InfoWindowData {
        return InfoWindowData(name, distance, time)
    }
}