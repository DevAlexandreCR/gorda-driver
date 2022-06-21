package gorda.driver.ui.service

import androidx.fragment.app.Fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels

import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import gorda.driver.R
import gorda.driver.interfaces.LocType
import gorda.driver.ui.MainViewModel

class MapFragment : Fragment(), OnMapReadyCallback {

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
                println("latlng " + loc.name )
                val startLatLng = LatLng(loc.lat!!, loc.long!!)
                googleMap.addMarker(MarkerOptions().position(startLatLng).title(loc.name))
                googleMap.moveCamera(CameraUpdateFactory.newLatLng(startLatLng))
            }
        }
    }
}