package gorda.driver.ui.service

import android.content.res.Resources
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.*
import gorda.driver.BuildConfig
import gorda.driver.R
import gorda.driver.databinding.FragmentMapBinding
import gorda.driver.maps.*
import gorda.driver.maps.Map
import gorda.driver.services.retrofit.RetrofitBase
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.dataclasses.ServiceUpdates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


class MapFragment : Fragment(), OnMapReadyCallback {

    companion object {
        const val TAG = "gorda.driver.ui.service.mapFragment"
    }

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentMapBinding
    private lateinit var textTime: TextView
    private lateinit var textDistance: TextView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val mapFragment = childFragmentManager.findFragmentById(R.id.map) as SupportMapFragment?
        mapFragment?.getMapAsync(this)
        textTime = binding.textTime
        textDistance = binding.textDistance
    }

    override fun onMapReady(googleMap: GoogleMap) {
        setMapStyle(googleMap)
        val infoWindow = WindowAdapter(requireContext())
        googleMap.setInfoWindowAdapter(infoWindow)
        mainViewModel.serviceUpdates.value.let { serviceUpdates ->
            when (serviceUpdates) {
                is ServiceUpdates.StarLoc -> {
                    val loc = serviceUpdates.starLoc
                    mainViewModel.lastLocation.value.let { locationUpdates ->
                        when (locationUpdates) {
                            is LocationUpdates.LastLocation -> {
                                val driverLatLng = LatLng(locationUpdates.location.latitude, locationUpdates.location.longitude)
                                val startLatLng = LatLng(loc.lat, loc.lng)
                                val driverMarker = googleMap.addMarker(
                                    MarkerOptions()
                                        .position(driverLatLng)
                                        .icon(
                                            BitmapDescriptorFactory.defaultMarker(
                                                BitmapDescriptorFactory.HUE_GREEN
                                            )
                                        )
                                )
                                driverMarker?.tag = makeInfoWindowData("A")
                                val markerStartAddress = googleMap.addMarker(
                                    MarkerOptions()
                                        .position(startLatLng)
                                        .icon(
                                            BitmapDescriptorFactory.defaultMarker(
                                                BitmapDescriptorFactory.HUE_GREEN
                                            )
                                        )
                                        .title(loc.name)
                                )
                                markerStartAddress?.tag = makeInfoWindowData("B")
                                val bounds = LatLngBounds
                                    .Builder()
                                    .include(startLatLng)
                                    .include(driverLatLng)
                                    .build()
                                googleMap.animateCamera(
                                    CameraUpdateFactory.newLatLngBounds(
                                        bounds,
                                        300
                                    )
                                )

                                val mapService = Map()
                                val url = mapService.getDirectionURL(
                                    driverLatLng,
                                    startLatLng,
                                    BuildConfig.MAPS_API_KEY
                                )
                                getDirections(url, object : OnDirectionCompleteListener {
                                    override fun onSuccess(routes: ArrayList<Routes>) {
                                        val lineOptions = mapService.makePolylineOptions(routes)
                                        requireActivity().runOnUiThread {
                                            googleMap.addPolyline(lineOptions)
                                            if (markerStartAddress != null) {
                                                val distance = routes[0].legs[0].distance
                                                val time = routes[0].legs[0].duration
                                                textTime.text = time.getDurationString()
                                                textDistance.text = distance.getDistanceString()
                                                mainViewModel.setServiceUpdateDistTime(distance, time)
                                                markerStartAddress.tag = makeInfoWindowData(
                                                    loc.name,
                                                    distance.getDistanceString(),
                                                    time.getDurationString()
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
                                            Toast.makeText(
                                                context,
                                                R.string.no_routes_available,
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    }
                                })
                            }
                            else -> {}
                        }
                    }
                }
                else -> {}
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

    private fun makeInfoWindowData(
        name: String = "",
        distance: String = "",
        time: String = ""
    ): InfoWindowData {
        return InfoWindowData(name, distance, time)
    }

    private fun setMapStyle(googleMap: GoogleMap) {
        try {
            val success: Boolean = googleMap.setMapStyle(
                MapStyleOptions.loadRawResourceStyle(
                    requireContext(), R.raw.style_json
                )
            )
            if (!success) {
                Log.e(TAG, "Style parsing failed.")
            }
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Can't find style. Error: ", e)
        }
    }
}