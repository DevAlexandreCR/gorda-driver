package gorda.driver.ui.service.apply

import android.location.Location
import android.os.Bundle
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.fragment.findNavController
import com.google.android.gms.tasks.Task
import gorda.driver.R
import gorda.driver.databinding.FragmentApplyBinding
import gorda.driver.helpers.withTimeout
import gorda.driver.models.Driver
import gorda.driver.models.Service
import gorda.driver.repositories.DriverRepository
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.dataclasses.ServiceUpdates
import gorda.driver.utils.StringHelper
import kotlinx.coroutines.launch

class ApplyFragment : Fragment() {

    companion object {
        private const val TAG = "ApplyFragment"
        private const val DRIVER_REFRESH_TIMEOUT_MS = 8_000L
        private const val SERVICE_VALIDATION_TIMEOUT_MS = 8_000L
        private const val APPLICANT_WRITE_TIMEOUT_MS = 8_000L
        private const val CANCEL_TIMEOUT_MS = 8_000L
    }

    private val mainViewModel: MainViewModel by activityViewModels()
    private val applyViewModel: ApplyViewModel by viewModels()

    private var _binding: FragmentApplyBinding? = null
    private val binding get() = _binding!!

    private var navController: NavController? = null
    private var destinationChangedListener: NavController.OnDestinationChangedListener? = null
    private var lastKnownLocation: Location? = null
    private lateinit var driver: Driver
    private lateinit var service: Service
    private var currentPresenceState = MainViewModel.DriverPresenceState()
    private var applyAttemptId: Long = 0L
    private var applicantWriteAttemptId: Long? = null
    private val writeAttemptsToCancelOnSuccess = mutableSetOf<Long>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentApplyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        navController = findNavController()

        val currentDriver = mainViewModel.driver.value
        val currentService = arguments?.getSerializable("service") as? Service
        if (currentDriver == null || currentService == null) {
            navigateHomeIfCurrent()
            return
        }

        driver = currentDriver
        service = currentService
        currentPresenceState = mainViewModel.presenceState.value
        val locationUpdate = mainViewModel.lastLocation.value
        if (locationUpdate is LocationUpdates.LastLocation) {
            lastKnownLocation = locationUpdate.location
        }

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
            handleBackPress()
        }

        binding.btnCancel.setOnClickListener {
            handlePrimaryAction()
        }
        binding.btnRetry.setOnClickListener {
            attemptApply(forceRetry = true)
        }

        observeUiState()
        observePresence()
        observeLocation()
        observeServiceStatus()
        registerNavigationExitGuard()

        if (applyViewModel.markStarted()) {
            attemptApply(forceRetry = false)
        } else {
            render(applyViewModel.uiState.value ?: ApplyViewModel.ApplyUiState.Preparing)
        }
    }

    override fun onDestroyView() {
        destinationChangedListener?.let { listener ->
            navController?.removeOnDestinationChangedListener(listener)
        }
        destinationChangedListener = null
        navController = null
        _binding = null
        super.onDestroyView()
    }

    private fun observeUiState() {
        applyViewModel.uiState.observe(viewLifecycleOwner) { state ->
            render(state)
        }
    }

    private fun observePresence() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.presenceState.collect { presence ->
                    currentPresenceState = presence
                    render(applyViewModel.uiState.value ?: ApplyViewModel.ApplyUiState.Preparing)
                }
            }
        }
    }

    private fun observeLocation() {
        mainViewModel.lastLocation.observe(viewLifecycleOwner) { locationUpdate ->
            if (locationUpdate is LocationUpdates.LastLocation) {
                lastKnownLocation = locationUpdate.location
            }
        }
    }

    private fun observeServiceStatus() {
        mainViewModel.serviceUpdates.observe(viewLifecycleOwner) { update ->
            if (update !is ServiceUpdates.Status || !isAdded) {
                return@observe
            }

            when (update.status) {
                Service.STATUS_CANCELED -> {
                    showToastIfAvailable(R.string.service_canceled, Toast.LENGTH_SHORT)
                    navigateHomeIfCurrent()
                }
                Service.STATUS_IN_PROGRESS -> {
                    navigateHomeIfCurrent()
                }
            }
        }
    }

    private fun registerNavigationExitGuard() {
        destinationChangedListener = NavController.OnDestinationChangedListener { _, destination, _ ->
            if (destination.id != R.id.nav_apply) {
                when {
                    applyViewModel.hasApplicantWriteConfirmed() -> {
                        cancelApplyInBackground()
                    }
                    applyViewModel.isApplicantWriteInFlight() -> {
                        applicantWriteAttemptId?.let { writeAttemptsToCancelOnSuccess.add(it) }
                    }
                }
            }
        }

        navController?.addOnDestinationChangedListener(destinationChangedListener!!)
    }

    private fun handlePrimaryAction() {
        if (applyViewModel.hasApplicantWriteConfirmed()) {
            startCancelFlow(navigateOnSuccess = true)
        } else {
            exitBeforeApplicantWrite()
        }
    }

    private fun handleBackPress() {
        if (applyViewModel.hasApplicantWriteConfirmed()) {
            startCancelFlow(navigateOnSuccess = true)
        } else {
            exitBeforeApplicantWrite()
        }
    }

    private fun exitBeforeApplicantWrite() {
        applicantWriteAttemptId?.let { attemptId ->
            writeAttemptsToCancelOnSuccess.add(attemptId)
        }
        navigateHomeIfCurrent()
    }

    private fun attemptApply(forceRetry: Boolean) {
        val location = lastKnownLocation
        if (location == null) {
            applyViewModel.showFailed(R.string.common_error, canRetry = false)
            return
        }

        val connectionStatus = ApplyViewModel.connectionStatusForApply(currentPresenceState)
        if (connectionStatus != ApplyViewModel.ApplyConnectionStatus.READY) {
            applyViewModel.showBlockedByConnection()
            return
        }

        if (!forceRetry) {
            applyViewModel.showPreparing()
        }

        val routeEstimate = ApplyViewModel.estimateRoute(
            originLat = location.latitude,
            originLng = location.longitude,
            destinationLat = service.start_loc.lat,
            destinationLng = service.start_loc.lng
        )

        val attemptId = nextApplyAttemptId()
        applyViewModel.showApplying()
        mainViewModel.setLoading(true)

        DriverRepository.getDriverTask(driver.id)
            .addOnSuccessListener { refreshedDriver ->
                if (!isActiveAttempt(attemptId)) {
                    return@addOnSuccessListener
                }

                driver = refreshedDriver
                val restrictionMessage = mainViewModel.getApplyRestrictionMessageRes(refreshedDriver)
                if (restrictionMessage != null) {
                    finishAttempt()
                    showToastIfAvailable(restrictionMessage, Toast.LENGTH_LONG)
                    navigateHomeIfCurrent()
                    return@addOnSuccessListener
                }

                validateServiceAndApply(
                    attemptId = attemptId,
                    routeEstimate = routeEstimate
                )
            }
            .addOnFailureListener { exception ->
                if (!isActiveAttempt(attemptId)) {
                    return@addOnFailureListener
                }

                Log.e(TAG, "Driver refresh failed", exception)
                onRetryableFailure(R.string.common_error)
            }
            .withTimeout(DRIVER_REFRESH_TIMEOUT_MS) {
                if (!isActiveAttempt(attemptId)) {
                    return@withTimeout
                }

                Log.w(TAG, "Driver refresh timed out attemptId=$attemptId")
                onRetryableFailure(R.string.error_timeout)
            }
    }

    private fun validateServiceAndApply(
        attemptId: Long,
        routeEstimate: ApplyViewModel.RouteEstimate
    ) {
        if (!ApplyViewModel.isReadyToApply(currentPresenceState)) {
            onBlockedByConnection()
            return
        }

        service.validateForApply()
            .addOnSuccessListener { validatedService ->
                if (!isActiveAttempt(attemptId)) {
                    return@addOnSuccessListener
                }

                service = validatedService
                addApplicant(
                    attemptId = attemptId,
                    routeEstimate = routeEstimate
                )
            }
            .addOnFailureListener { exception ->
                if (!isActiveAttempt(attemptId)) {
                    return@addOnFailureListener
                }

                Log.e(TAG, "Service validation failed", exception)
                finishAttempt()
                val errorMessage = when {
                    exception.message?.contains("does not exist", ignoreCase = true) == true ->
                        R.string.service_not_exists
                    exception.message?.contains("already has a driver", ignoreCase = true) == true ->
                        R.string.service_already_assigned
                    exception.message?.contains("no longer available", ignoreCase = true) == true ->
                        R.string.service_not_available
                    else -> R.string.common_error
                }

                showToastIfAvailable(errorMessage, Toast.LENGTH_LONG)
                navigateHomeIfCurrent()
            }
            .withTimeout(SERVICE_VALIDATION_TIMEOUT_MS) {
                if (!isActiveAttempt(attemptId)) {
                    return@withTimeout
                }

                Log.w(TAG, "Service validation timed out attemptId=$attemptId")
                onRetryableFailure(R.string.error_timeout)
            }
    }

    private fun addApplicant(
        attemptId: Long,
        routeEstimate: ApplyViewModel.RouteEstimate
    ) {
        if (!ApplyViewModel.isReadyToApply(currentPresenceState)) {
            onBlockedByConnection()
            return
        }

        applicantWriteAttemptId = attemptId
        val connection = mainViewModel.currentService.value?.let { service.id }

        service.addApplicant(
            driver = driver,
            distance = routeEstimate.distanceMeters,
            time = routeEstimate.timeSeconds,
            connection = connection
        ).addOnSuccessListener {
            val shouldCancelLateSuccess = writeAttemptsToCancelOnSuccess.remove(attemptId)
            if (!isActiveAttempt(attemptId) || shouldCancelLateSuccess) {
                finishAttempt()
                cancelApplyInBackground()
                return@addOnSuccessListener
            }

            finishAttempt()
            applyViewModel.showAppliedWaitingAssignment(service.start_loc.name)
        }.addOnFailureListener { exception ->
            if (!isActiveAttempt(attemptId)) {
                return@addOnFailureListener
            }

            Log.e(TAG, "Applicant write failed", exception)
            onRetryableFailure(R.string.common_error)
        }.withTimeout(APPLICANT_WRITE_TIMEOUT_MS) {
            if (!isActiveAttempt(attemptId)) {
                return@withTimeout
            }

            Log.w(TAG, "Applicant write timed out attemptId=$attemptId")
            writeAttemptsToCancelOnSuccess.add(attemptId)
            onRetryableFailure(R.string.error_timeout)
        }
    }

    private fun startCancelFlow(navigateOnSuccess: Boolean) {
        applyViewModel.showCanceling()
        binding.btnCancel.isEnabled = false
        mainViewModel.setLoading(true)

        cancelApply()
            .addOnSuccessListener {
                mainViewModel.setLoading(false)
                showToastIfAvailable(R.string.cancelApply, Toast.LENGTH_SHORT)
                if (navigateOnSuccess) {
                    navigateHomeIfCurrent()
                }
            }
            .addOnFailureListener { exception ->
                mainViewModel.setLoading(false)
                Log.e(TAG, "Cancel apply failed", exception)
                showToastIfAvailable(R.string.common_error, Toast.LENGTH_LONG)
                applyViewModel.showAppliedWaitingAssignment(service.start_loc.name)
            }
            .withTimeout(CANCEL_TIMEOUT_MS) {
                mainViewModel.setLoading(false)
                Log.w(TAG, "Cancel apply timed out serviceId=${service.id}")
                showToastIfAvailable(R.string.error_timeout, Toast.LENGTH_LONG)
                applyViewModel.showAppliedWaitingAssignment(service.start_loc.name)
            }
    }

    private fun onBlockedByConnection() {
        finishAttempt()
        applyViewModel.showBlockedByConnection()
    }

    private fun onRetryableFailure(@StringRes messageRes: Int) {
        finishAttempt()
        applyViewModel.showFailed(
            messageRes = messageRes,
            canRetry = true
        )
    }

    private fun finishAttempt() {
        applicantWriteAttemptId = null
        mainViewModel.setLoading(false)
    }

    private fun isActiveAttempt(attemptId: Long): Boolean {
        return applyAttemptId == attemptId
    }

    private fun nextApplyAttemptId(): Long {
        applyAttemptId += 1
        return applyAttemptId
    }

    private fun cancelApply(): Task<Void> {
        return service.cancelApplicant(driver)
    }

    private fun cancelApplyInBackground() {
        cancelApply()
            .addOnFailureListener { exception ->
                Log.e(TAG, "Background cancel apply failed", exception)
            }
    }

    private fun render(state: ApplyViewModel.ApplyUiState) {
        val currentBinding = _binding ?: return
        val applyReady = ApplyViewModel.isReadyToApply(currentPresenceState)

        when (state) {
            ApplyViewModel.ApplyUiState.Preparing -> {
                currentBinding.progressBar.isVisible = true
                currentBinding.textView.text = getString(R.string.applying)
                currentBinding.btnCancel.text = getString(applyViewModel.primaryActionRes())
                currentBinding.btnCancel.isEnabled = true
                currentBinding.btnRetry.isGone = true
            }

            ApplyViewModel.ApplyUiState.BlockedByConnection -> {
                currentBinding.progressBar.isGone = true
                currentBinding.textView.text = blockedMessage(applyReady)
                currentBinding.btnCancel.text = getString(R.string.back_to_services)
                currentBinding.btnCancel.isEnabled = true
                currentBinding.btnRetry.isVisible = true
                currentBinding.btnRetry.isEnabled = applyReady
                currentBinding.btnRetry.text = getString(R.string.retry_application)
            }

            ApplyViewModel.ApplyUiState.Applying -> {
                currentBinding.progressBar.isVisible = true
                currentBinding.textView.text = getString(R.string.applying)
                currentBinding.btnCancel.text = getString(R.string.back_to_services)
                currentBinding.btnCancel.isEnabled = true
                currentBinding.btnRetry.isGone = true
            }

            is ApplyViewModel.ApplyUiState.AppliedWaitingAssignment -> {
                currentBinding.progressBar.isGone = true
                currentBinding.textView.text = waitForAssignMessage(state.serviceName)
                currentBinding.btnCancel.text = getString(R.string.cancel_application)
                currentBinding.btnCancel.isEnabled = true
                currentBinding.btnRetry.isGone = true
            }

            is ApplyViewModel.ApplyUiState.Failed -> {
                currentBinding.progressBar.isGone = true
                currentBinding.textView.text = failedMessage(
                    messageRes = state.messageRes,
                    canRetry = applyReady && state.canRetry
                )
                currentBinding.btnCancel.text = getString(R.string.back_to_services)
                currentBinding.btnCancel.isEnabled = true
                currentBinding.btnRetry.isVisible = true
                currentBinding.btnRetry.isEnabled = applyReady && state.canRetry
                currentBinding.btnRetry.text = getString(R.string.retry_application)
            }

            ApplyViewModel.ApplyUiState.Canceling -> {
                currentBinding.progressBar.isVisible = true
                currentBinding.textView.text = getString(R.string.canceling_application)
                currentBinding.btnCancel.text = getString(R.string.cancel_application)
                currentBinding.btnCancel.isEnabled = false
                currentBinding.btnRetry.isGone = true
            }
        }
    }

    private fun blockedMessage(applyReady: Boolean): CharSequence {
        return if (applyReady) {
            getString(R.string.connection_restored_retry_apply) + "\n" +
                getString(R.string.not_applied_yet)
        } else {
            getString(R.string.reconnecting_dispatch_not_applied)
        }
    }

    private fun failedMessage(
        @StringRes messageRes: Int,
        canRetry: Boolean
    ): CharSequence {
        return if (canRetry) {
            getString(R.string.connection_restored_retry_apply) + "\n" +
                getString(messageRes) + "\n" +
                getString(R.string.not_applied_yet)
        } else {
            getString(messageRes) + "\n" + getString(R.string.not_applied_yet)
        }
    }

    private fun waitForAssignMessage(serviceName: String): Spanned {
        val text = getString(R.string.wait_for_assign, serviceName)
        return StringHelper.getString(text)
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
