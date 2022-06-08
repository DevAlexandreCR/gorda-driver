package gorda.driver.activity.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.activity.ui.services.ServiceAdapter
import gorda.driver.databinding.FragmentHomeBinding
import gorda.driver.models.Service
import gorda.driver.repositories.ServiceRepository

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val serviceAdapter = ServiceAdapter()

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
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}