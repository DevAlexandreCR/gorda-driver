package gorda.driver.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.findNavController
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.R
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.service.LocationUpdates
import gorda.driver.ui.service.ServiceAdapter
import gorda.driver.ui.service.ServiceUpdates
import gorda.driver.databinding.FragmentHomeBinding
import gorda.driver.interfaces.LocType
import gorda.driver.ui.driver.DriverUpdates

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val homeViewModel =
            ViewModelProvider(this).get(HomeViewModel::class.java)

        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        val recyclerView: RecyclerView = binding.listServices
        val showMapFromService: (location: LocType) -> Unit = { location ->
            mainViewModel.setCurrentServiceStartLocation(location)
            findNavController().navigate(R.id.nav_map)
        }
        val serviceAdapter = ServiceAdapter(requireContext(), showMapFromService)
        recyclerView.adapter = serviceAdapter
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = getString(it)
        }

        homeViewModel.serviceList.observe(viewLifecycleOwner) {
            when(it) {
                is ServiceUpdates.SetList -> {
                    serviceAdapter.submitList(it.services)
                }
                is ServiceUpdates.StopListen -> {
                    serviceAdapter.submitList(mutableListOf())
                }
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
                else -> {  }
            }
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}