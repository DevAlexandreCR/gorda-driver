package gorda.driver.ui.service

import android.content.pm.PackageManager
import android.content.res.Resources
import android.location.Location
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
import gorda.driver.R
import gorda.driver.databinding.FragmentMapBinding
import gorda.driver.interfaces.LocType
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
        const val TAG = "ui.service.mapFragment"
    }

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentMapBinding
    private lateinit var textTime: TextView
    private lateinit var textDistance: TextView
    private var location: Location? = null
    private var statLoc: LocType? = null

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
        mainViewModel.lastLocation.value.let {
            if (it is LocationUpdates.LastLocation) {
                location = it.location
            }
        }
        mainViewModel.serviceUpdates.value.let {
            if (it is ServiceUpdates.StarLoc) {
                statLoc = it.starLoc
            }
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        setMapStyle(googleMap)
        if (null != statLoc && null != location) {
            val infoWindow = WindowAdapter(requireContext())
            googleMap.setInfoWindowAdapter(infoWindow)
            val driverLatLng = LatLng(location!!.latitude, location!!.longitude)
            val startLatLng = LatLng(statLoc!!.lat, statLoc!!.lng)
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
                    .title(statLoc!!.name)
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
                getApiKey()
            )
            getDirections(url, object : OnDirectionCompleteListener {
                override fun onSuccess(routes: ArrayList<Routes>) {
                    if (isAdded) {
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
                                    statLoc!!.name,
                                    distance.getDistanceString(),
                                    time.getDurationString()
                                )
                                markerStartAddress.showInfoWindow()
                            }
                        }
                    }
                }

                override fun onFailure() {
                    if (isAdded) {
                        requireActivity().runOnUiThread {
                            markerStartAddress?.tag = makeInfoWindowData(statLoc!!.name)
                            markerStartAddress?.showInfoWindow()
                            Toast.makeText(
                                context,
                                R.string.no_routes_available,
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                }
            })
        } else {
            println(statLoc.toString() + location.toString())
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
                        Log.e("Error No routes", it.error_message)
                        listener.onFailure()
                    }
                }
            } else {
                Log.e(TAG, cal.message())
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

    private fun getApiKey(): String {
        val app = requireContext().packageManager.getApplicationInfo(
            requireContext().packageName,
            PackageManager.GET_META_DATA
        )
        val bundle = app.metaData
        return bundle.getString("com.google.android.geo.API_KEY", "")
    }
}