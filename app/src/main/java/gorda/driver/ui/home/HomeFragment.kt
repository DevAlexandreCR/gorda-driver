package gorda.driver.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.R
import gorda.driver.databinding.FragmentHomeBinding
import gorda.driver.interfaces.LocType
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.ServiceAdapter
import gorda.driver.ui.service.dataclasses.ServiceUpdates

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel: HomeViewModel by viewModels()

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        val recyclerView: RecyclerView = binding.listServices
        val showMapFromService: (location: LocType) -> Unit = { location ->
            mainViewModel.setServiceUpdateStartLocation(location)
            findNavController().navigate(R.id.nav_map)
        }
        val apply: (serviceId: String, location: LocType) -> Unit = { serviceId, location ->
            mainViewModel.setServiceUpdateApply(serviceId)
            mainViewModel.setServiceUpdateStartLocation(location)
            findNavController().navigate(R.id.nav_apply)
        }
        val serviceAdapter = ServiceAdapter(requireContext(), showMapFromService, apply)
        recyclerView.adapter = serviceAdapter
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = getString(it)
        }

        homeViewModel.serviceList.observe(viewLifecycleOwner) {
            when (it) {
                is ServiceUpdates.SetList -> {
                    serviceAdapter.submitList(it.services)
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
                    it.location
                    serviceAdapter.lastLocation = it.location
                    serviceAdapter.notifyDataSetChanged()
                }
            }
        }

        mainViewModel.driverStatus.observe(viewLifecycleOwner) {
            when (it) {
                is DriverUpdates.IsConnected -> {
                    if (it.connected) {
                        homeViewModel.startListenServices()
                    } else {
                        homeViewModel.stopListenServices()
                    }
                }
                else -> {}
            }
        }
        return root
    }

    override fun onStart() {
        super.onStart()
        mainViewModel.currentService.observe(viewLifecycleOwner) { currentService ->
            if (currentService != null) {
                findNavController().navigate(R.id.nav_current_service)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}