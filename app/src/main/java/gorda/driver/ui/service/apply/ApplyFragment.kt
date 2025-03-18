package gorda.driver.ui.service.apply

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.tasks.Task
import gorda.driver.R
import gorda.driver.databinding.FragmentApplyBinding
import gorda.driver.helpers.withTimeout
import gorda.driver.models.Driver
import gorda.driver.models.Service
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.service.dataclasses.ServiceUpdates
import gorda.driver.utils.StringHelper

class ApplyFragment : Fragment() {

    companion object {
        const val TAG = "ApplyFragment"
    }

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var binding: FragmentApplyBinding
    private lateinit var btnCancel: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var textView: TextView
    private var distance: Int = 0
    private lateinit var driver: Driver
    private lateinit var service: Service
    private var time: Int = 0

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
        mainViewModel.setLoading(true)

        requireActivity().onBackPressedDispatcher.addCallback(this) {}

        btnCancel.setOnClickListener { button ->
            button.isEnabled = false
            cancelApply().addOnSuccessListener {
                button.isEnabled = true
                if (isAdded && findNavController().currentDestination?.id == R.id.nav_apply) {
                    findNavController().navigate(R.id.action_cancel_apply)
                }
                Toast.makeText(requireContext(), R.string.cancelApply, Toast.LENGTH_SHORT).show()
            }. addOnFailureListener { e ->
                button.isEnabled = true
                e.message?.let { message -> Log.e(TAG, message) }
                Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_LONG).show()
            } .withTimeout {
                button.isEnabled = true
                Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_LONG).show()
            }
        }

        mainViewModel.driver.value?.let {
            driver = it
            arguments?.let { bundle ->
                service = bundle.getSerializable("service") as Service
                apply()
                if (isAdded) {
                    findNavController().addOnDestinationChangedListener { _, _, _ ->
                        cancelApply()
                    }
                }
            }
        }
    }

    private fun cancelApply(): Task<Void> {
        return service.cancelApplicant(driver)
    }

    private fun apply() {
        mainViewModel.serviceUpdates.observe(viewLifecycleOwner) {
            when(it) {
                is ServiceUpdates.DistanceTime -> {
                    if (isAdded) {
                        time = it.time
                        distance = it.distance
                        mainViewModel.setLoading(true)
                        var connection: String? = null
                        mainViewModel.currentService.value?.let {_ ->
                            connection = service.id
                        }
                        service.addApplicant(driver, distance, time, connection).addOnSuccessListener {
                            if (isAdded) {
                                val applyText = requireActivity().resources.getString(R.string.wait_for_assign, service.start_loc.name)
                                textView.text = StringHelper.getString(applyText)
                                btnCancel.isEnabled = true
                                mainViewModel.setLoading(false)
                            }
                        }.addOnFailureListener { e ->
                            if (isAdded) {
                                e.message?.let { message -> Log.e(TAG, message) }
                                Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_LONG).show()
                                if (findNavController().currentDestination?.id == R.id.nav_apply)
                                    findNavController().navigate(R.id.action_cancel_apply)
                            }
                        }.withTimeout {
                            if (isAdded) {
                                Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_LONG).show()
                                mainViewModel.setLoading(false)
                                btnCancel.isEnabled = true
                                if (findNavController().currentDestination?.id == R.id.nav_apply)
                                    findNavController().navigate(R.id.action_cancel_apply)
                            }
                        }
                    }
                }
                is ServiceUpdates.Status -> {
                    when (it.status) {
                        Service.STATUS_CANCELED -> {
                            Toast.makeText(requireContext(), R.string.service_canceled, Toast.LENGTH_SHORT).show()
                            if (findNavController().currentDestination?.id == R.id.nav_apply)
                            findNavController().navigate(R.id.action_cancel_apply)
                        }
                        Service.STATUS_IN_PROGRESS -> {
                            if (findNavController().currentDestination?.id == R.id.nav_apply)
                            findNavController().navigate(R.id.action_cancel_apply)
                        }
                    }
                }
                else -> {}
            }
        }
    }
}