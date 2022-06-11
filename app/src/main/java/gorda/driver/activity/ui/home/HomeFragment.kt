package gorda.driver.activity.ui.home

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.*
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.activity.background.LocationService
import gorda.driver.activity.ui.MainViewModel
import gorda.driver.activity.ui.service.LocationUpdates
import gorda.driver.activity.ui.service.ServiceAdapter
import gorda.driver.databinding.FragmentHomeBinding

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val serviceAdapter = ServiceAdapter()
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
        recyclerView.adapter = serviceAdapter
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = getString(it)
        }

        homeViewModel.serviceList.observe(viewLifecycleOwner) {
            this.serviceAdapter.submitList(it)
        }

        mainViewModel.lastLocation.observe(viewLifecycleOwner) {
            when (it) {
                is LocationUpdates.LastLocation -> {
                    it.location
                    this.serviceAdapter.lastLocation = it.location
                    this.serviceAdapter.notifyDataSetChanged()
                    println("location updated from view model fragment ${it.location.longitude}")
                }
            }
        }
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}