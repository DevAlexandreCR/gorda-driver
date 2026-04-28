package gorda.driver.ui.service

import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.content.res.ResourcesCompat
import androidx.core.graphics.scale
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.MarkerOptions
import gorda.driver.R
import gorda.driver.databinding.FragmentMapBinding
import gorda.driver.interfaces.LocType
import gorda.driver.maps.InfoWindowData
import gorda.driver.maps.Map
import gorda.driver.maps.MapApiService
import gorda.driver.maps.OnDirectionCompleteListener
import gorda.driver.maps.WindowAdapter
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
        private const val MAP_READY_TIMEOUT_MS = 3_000L
    }

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentMapBinding
    private lateinit var textTime: TextView
    private lateinit var textDistance: TextView
    private var location: Location? = null
    private var statLoc: LocType? = null
    private var googleMap: GoogleMap? = null
    private var mapReady = false
    private val mapUnavailableRunnable = Runnable {
        if (!mapReady && isAdded) {
            binding.textMapUnavailable.text = getString(R.string.map_unavailable)
            binding.textMapUnavailable.isVisible = true
        }
    }

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
        mapReady = false
        binding.textMapUnavailable.isGone = true
        binding.root.removeCallbacks(mapUnavailableRunnable)
        binding.root.postDelayed(mapUnavailableRunnable, MAP_READY_TIMEOUT_MS)
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
        observeLocationUpdates()
        observeServiceUpdates()
        renderLocationPendingStateIfNeeded()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        this.googleMap = googleMap
        mapReady = true
        binding.root.removeCallbacks(mapUnavailableRunnable)
        setMapStyle(googleMap)
        renderMapState()
    }

    override fun onDestroyView() {
        googleMap = null
        binding.root.removeCallbacks(mapUnavailableRunnable)
        super.onDestroyView()
    }

    private fun observeLocationUpdates() {
        mainViewModel.lastLocation.observe(viewLifecycleOwner) { locationUpdate ->
            if (locationUpdate is LocationUpdates.LastLocation) {
                location = locationUpdate.location
                renderMapState()
            }
        }
    }

    private fun observeServiceUpdates() {
        mainViewModel.serviceUpdates.observe(viewLifecycleOwner) { update ->
            if (update is ServiceUpdates.StarLoc) {
                statLoc = update.starLoc
                renderMapState()
            }
        }
    }

    private fun renderMapState() {
        val map = googleMap ?: return
        val currentLocation = location
        val startLocation = statLoc

        map.clear()

        if (currentLocation == null || startLocation == null) {
            renderLocationPendingStateIfNeeded()
            return
        }

        binding.textMapUnavailable.isGone = true
        val infoWindow = WindowAdapter(requireContext())
        map.setInfoWindowAdapter(infoWindow)
        val driverLatLng = LatLng(currentLocation.latitude, currentLocation.longitude)
        val startLatLng = LatLng(startLocation.lat, startLocation.lng)
        val driverMarker = map.addMarker(
            MarkerOptions()
                .position(driverLatLng)
                .icon(
                    BitmapDescriptorFactory.fromBitmap(getResizedBitmap(R.mipmap.ic_car))
                )
        )
        driverMarker?.tag = makeInfoWindowData("A")
        val markerStartAddress = map.addMarker(
            MarkerOptions()
                .position(startLatLng)
                .icon(
                    BitmapDescriptorFactory.fromBitmap(getResizedBitmap(R.mipmap.ic_loc_a_light))
                )
                .title(startLocation.name)
        )
        markerStartAddress?.tag = makeInfoWindowData("B")
        val bounds = LatLngBounds
            .Builder()
            .include(startLatLng)
            .include(driverLatLng)
            .build()
        val paddingInPixels = resources.getDimensionPixelSize(R.dimen.map_margin_big)
        map.animateCamera(
            CameraUpdateFactory.newLatLngBounds(
                bounds,
                paddingInPixels
            )
        )
        val distance = Map.calculateDistance(
            LatLng(startLocation.lat, startLocation.lng),
            LatLng(currentLocation.latitude, currentLocation.longitude)
        )
        val time = Map.calculateTime(distance)
        textTime.text = Map.getTimeString(time)
        textDistance.text = Map.distanceToString(distance)
        if (markerStartAddress != null) {
            markerStartAddress.tag = makeInfoWindowData(
                startLocation.name,
                Map.distanceToString(distance),
                Map.getTimeString(time)
            )
            markerStartAddress.showInfoWindow()
        }
    }

    private fun renderLocationPendingStateIfNeeded() {
        if (!isAdded) {
            return
        }

        textTime.text = getString(R.string.location_pending_placeholder)
        textDistance.text = getString(R.string.location_pending_placeholder)
        if (location == null) {
            binding.textMapUnavailable.text = getString(R.string.map_waiting_for_location)
            binding.textMapUnavailable.isVisible = true
        } else {
            binding.textMapUnavailable.isGone = true
        }
    }

    private fun getResizedBitmap(drawableId: Int): Bitmap {
        val bitmapDrawable = ResourcesCompat.getDrawable(resources, drawableId, null) as BitmapDrawable
        val bitmap = bitmapDrawable.bitmap
        return bitmap.scale(100, 100, false)
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
