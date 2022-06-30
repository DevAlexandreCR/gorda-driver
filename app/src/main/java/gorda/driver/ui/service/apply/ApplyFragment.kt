package gorda.driver.ui.service.apply

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import gorda.driver.R
import gorda.driver.databinding.FragmentApplyBinding
import gorda.driver.models.Driver
import gorda.driver.models.Service
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.service.dataclasses.ServiceUpdates

class ApplyFragment : Fragment() {

    companion object {
        fun newInstance() = ApplyFragment()
    }

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentApplyBinding
    private lateinit var btnCancel: Button
    private lateinit var distance: String
    private lateinit var driver: Driver
    private lateinit var service: Service
    private lateinit var time: String

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentApplyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        btnCancel = binding.btnCancel

        mainViewModel.driver.value?.let {
            driver = it
            arguments?.let { bundle ->
                service = bundle.getSerializable("service") as Service
                apply()
            }
        }
    }

    private fun apply() {
        mainViewModel.serviceUpdates.observe(viewLifecycleOwner) {
            when(it) {
                is ServiceUpdates.DistanceTime -> {
                    time = it.time
                    distance = it.distance
                    service.addApplicant(driver, distance, time).addOnSuccessListener {
                        Toast.makeText(requireContext(), R.string.applied, Toast.LENGTH_LONG).show()
                    }.addOnFailureListener {
                        Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_LONG).show()
                    }
                }
                else -> {}
            }
        }
    }
}