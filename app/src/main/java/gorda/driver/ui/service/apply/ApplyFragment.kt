package gorda.driver.ui.service.apply

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.annotation.StringRes
import androidx.activity.addCallback
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.NavController
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
    private var _binding: FragmentApplyBinding? = null
    private val binding get() = _binding!!
    private var navController: NavController? = null
    private var cancelOnExitEnabled = true
    private var destinationChangedListener: NavController.OnDestinationChangedListener? = null
    private var distance: Int = 0
    private lateinit var driver: Driver
    private lateinit var service: Service
    private var time: Int = 0

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApplyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = findNavController()
        cancelOnExitEnabled = true
        mainViewModel.setLoading(true)

        requireActivity().onBackPressedDispatcher.addCallback(this) {}

        binding.btnCancel.setOnClickListener { button ->
            button.isEnabled = false
            cancelApply().addOnSuccessListener {
                updateCancelButton(enabled = true)
                cancelOnExitEnabled = false
                showToastIfAvailable(R.string.cancelApply, Toast.LENGTH_SHORT)
                navigateHomeIfCurrent()
            }.addOnFailureListener { e ->
                updateCancelButton(enabled = true)
                e.message?.let { message -> Log.e(TAG, message) }
                showToastIfAvailable(R.string.common_error, Toast.LENGTH_LONG)
            }.withTimeout {
                updateCancelButton(enabled = true)
                mainViewModel.setErrorTimeout(true)
            }
        }

        mainViewModel.driver.value?.let {
            driver = it
            arguments?.let { bundle ->
                service = bundle.getSerializable("service") as Service
                apply()
                destinationChangedListener = NavController.OnDestinationChangedListener { _, destination, _ ->
                    if (cancelOnExitEnabled && destination.id != R.id.nav_apply) {
                        cancelApply()
                    }
                }
                navController?.addOnDestinationChangedListener(destinationChangedListener!!)
            }
        }
    }

    override fun onDestroyView() {
        cancelOnExitEnabled = false
        destinationChangedListener?.let { listener ->
            navController?.removeOnDestinationChangedListener(listener)
        }
        destinationChangedListener = null
        navController = null
        _binding = null
        super.onDestroyView()
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

                        // Validate service before applying
                        service.validateForApply().addOnSuccessListener { validatedService ->
                            if (!canUpdateUi()) return@addOnSuccessListener

                            service = validatedService

                            service.addApplicant(driver, distance, time, connection).addOnSuccessListener {
                                val currentBinding = _binding ?: return@addOnSuccessListener
                                val applyText = getString(R.string.wait_for_assign, service.start_loc.name)
                                currentBinding.textView.text = StringHelper.getString(applyText)
                                currentBinding.btnCancel.isEnabled = true
                                mainViewModel.setLoading(false)
                            }.addOnFailureListener { e ->
                                e.message?.let { message -> Log.e(TAG, message) }
                                showToastIfAvailable(R.string.common_error, Toast.LENGTH_LONG)
                                navigateHomeIfCurrent()
                            }.withTimeout {
                                if (canUpdateUi()) {
                                    mainViewModel.setLoading(false)
                                    mainViewModel.setErrorTimeout(true)
                                    binding.btnCancel.isEnabled = true
                                    navigateHomeIfCurrent()
                                }
                            }
                        }.addOnFailureListener { e ->
                            if (!isAdded) return@addOnFailureListener

                            e.message?.let { message -> Log.e(TAG, "Service validation failed: $message") }

                            val errorMessage = when {
                                e.message?.contains("does not exist", ignoreCase = true) == true ->
                                    R.string.service_not_exists
                                e.message?.contains("already has a driver", ignoreCase = true) == true ->
                                    R.string.service_already_assigned
                                e.message?.contains("no longer available", ignoreCase = true) == true ->
                                    R.string.service_not_available
                                else -> R.string.common_error
                            }

                            showToastIfAvailable(errorMessage, Toast.LENGTH_LONG)
                            mainViewModel.setLoading(false)
                            navigateHomeIfCurrent()
                        }
                    }
                }
                is ServiceUpdates.Status -> {
                    when (it.status) {
                        Service.STATUS_CANCELED -> {
                            showToastIfAvailable(R.string.service_canceled, Toast.LENGTH_SHORT)
                            navigateHomeIfCurrent()
                        }
                        Service.STATUS_IN_PROGRESS -> {
                            navigateHomeIfCurrent()
                        }
                    }
                }
                else -> {}
            }
        }
    }

    private fun canUpdateUi(): Boolean {
        return isAdded && _binding != null
    }

    private fun updateCancelButton(enabled: Boolean) {
        _binding?.btnCancel?.isEnabled = enabled
    }

    private fun navigateHomeIfCurrent() {
        navController?.let { controller ->
            if (controller.currentDestination?.id == R.id.nav_apply) {
                controller.navigate(R.id.action_cancel_apply)
            }
        }
    }

    private fun showToastIfAvailable(@StringRes messageRes: Int, duration: Int) {
        context?.applicationContext?.let { appContext ->
            Toast.makeText(appContext, messageRes, duration).show()
        }
    }
}
