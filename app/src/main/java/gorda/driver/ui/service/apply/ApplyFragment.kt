package gorda.driver.ui.service.apply

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
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
    private lateinit var progressBar: ProgressBar
    private lateinit var textView: TextView
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
        progressBar = binding.progressBar
        textView = binding.textView

        btnCancel.setOnClickListener {
            service.cancelApplicant(driver).addOnSuccessListener {
                findNavController().navigate(R.id.nav_home)
                Toast.makeText(requireContext(), R.string.cancelApply, Toast.LENGTH_LONG).show()
            }. addOnFailureListener {
                Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_LONG).show()
            }
        }

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
                        textView.text = requireActivity().resources.getString(R.string.wait_for_assign)
                        btnCancel.isEnabled = true
                    }.addOnFailureListener {
                        Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_LONG).show()
                    }
                }
                is ServiceUpdates.Status -> {
                    when (it.status) {
                        Service.STATUS_CANCELED -> {
                            Toast.makeText(requireContext(), R.string.service_canceled, Toast.LENGTH_LONG).show()
                        }
                        Service.STATUS_IN_PROGRESS -> {
                            findNavController().navigate(R.id.nav_home)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}