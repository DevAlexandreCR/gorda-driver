package gorda.driver.ui.home

import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.R
import gorda.driver.databinding.FragmentHomeBinding
import gorda.driver.interfaces.LocType
import gorda.driver.models.Service
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.ServiceAdapter
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.dataclasses.ServiceUpdates

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()
    private var location: Location? = null
    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel: HomeViewModel by viewModels()

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        this.recyclerView = binding.listServices
        val showMapFromService: (location: LocType) -> Unit = { location ->
            mainViewModel.setServiceUpdateStartLocation(location)
            findNavController().navigate(R.id.nav_map)
        }
        val apply: (service: Service, location: LocType) -> Unit = { service, location ->
            mainViewModel.setServiceUpdateApply(service)
            mainViewModel.setServiceUpdateStartLocation(location)
            val bundle = bundleOf("service" to service)
            findNavController().navigate(R.id.nav_apply, bundle)
        }
        val serviceAdapter = ServiceAdapter(requireContext(), showMapFromService, apply)
        this.recyclerView.adapter = serviceAdapter
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = getString(it)
        }

        homeViewModel.serviceList.observe(viewLifecycleOwner) { updates ->
            when (updates) {
                is ServiceUpdates.SetList -> {
                    location?.let { loc ->
                        updates.services.sortWith(compareBy { service ->
                            val location = Location("last")
                            location.latitude = service.start_loc.lat
                            location.longitude = service.start_loc.lng

                            loc.distanceTo(location).toInt()
                        })
                    }
                    serviceAdapter.submitList(updates.services)
                }
                is ServiceUpdates.StopListen -> {
                    serviceAdapter.submitList(mutableListOf())
                }
                else -> {}
            }
        }

        mainViewModel.lastLocation.observe(viewLifecycleOwner) {
            when (it) {
                is LocationUpdates.LastLocation -> {
                    location = it.location
                    serviceAdapter.lastLocation = it.location
                    serviceAdapter.notifyDataSetChanged()
                }
            }
        }

        mainViewModel.isNetWorkConnected.observe(viewLifecycleOwner) {
            if (it) {
                this.recyclerView.visibility = View.VISIBLE
            } else {
                this.recyclerView.visibility = View.GONE
            }
        }

        mainViewModel.driverStatus.observe(viewLifecycleOwner) {
            when (it) {
                is DriverUpdates.IsConnected -> {
                    if (it.connected) {
                        this.recyclerView.visibility = View.VISIBLE
                        homeViewModel.startListenServices()
                    } else {
                        homeViewModel.stopListenServices()
                        this.recyclerView.visibility = View.GONE
                    }
                }
                else -> {}
            }
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}