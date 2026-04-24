package gorda.driver.ui.service.current

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context.BIND_NOT_FOREGROUND
import android.content.Intent
import android.content.ServiceConnection
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.text.Editable
import android.text.Spanned
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import gorda.driver.R
import gorda.driver.background.FeesService
import gorda.driver.background.FeesService.Companion.CURRENT_FEES
import gorda.driver.background.FeesService.Companion.FEE_MULTIPLIER
import gorda.driver.background.FeesService.Companion.ORIGIN
import gorda.driver.background.FeesService.Companion.RESUME_RIDE
import gorda.driver.databinding.FragmentCurrentServiceBinding
import gorda.driver.helpers.withTimeout
import gorda.driver.interfaces.RideFees
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import gorda.driver.repositories.ServiceRepository
import gorda.driver.repositories.SettingsRepository
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.history.ServiceDialogFragment
import gorda.driver.ui.home.HomeFragment
import gorda.driver.ui.service.ConnectionServiceDialog
import gorda.driver.utils.Constants
import gorda.driver.utils.NumberHelper
import gorda.driver.utils.ServiceHelper
import gorda.driver.utils.StringHelper
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale

class CurrentServiceFragment : Fragment() {

    companion object {
        const val TAG = "CurrentServiceFragment"
        private const val RIDE_FEES_TIMEOUT_MS = 8_000L
        private const val START_VALIDATION_TIMEOUT_MS = 8_000L
        private const val START_WRITE_TIMEOUT_MS = 8_000L
        private const val END_VALIDATION_TIMEOUT_MS = 8_000L
        private const val END_WRITE_TIMEOUT_MS = 8_000L
    }

    private var _binding: FragmentCurrentServiceBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private val currentServiceViewModel: CurrentServiceViewModel by viewModels()

    private lateinit var btnStatus: Button
    private lateinit var btnRetryAction: MaterialButton
    private lateinit var imgBtnMaps: ImageButton
    private lateinit var imgButtonWaze: ImageButton
    private lateinit var textName: TextView
    private lateinit var textPhone: TextView
    private lateinit var textAddress: TextView
    private lateinit var textDestination: TextView
    private lateinit var textAddressPreview: TextView
    private lateinit var textDestinationPreview: TextView
    private lateinit var textComment: TextView
    private lateinit var textTimePrice: TextView
    private lateinit var textCurrentTimePrice: TextView
    private lateinit var textDistancePrice: TextView
    private lateinit var textCurrentDistancePrice: TextView
    private lateinit var textCurrentDistance: TextView
    private lateinit var textPriceAddFee: TextView
    private lateinit var textPriceMinFee: TextView
    private lateinit var textPriceBase: TextView
    private lateinit var textFareMultiplier: TextView
    private lateinit var textFareMultiplierDisplay: TextView
    private lateinit var textTotalFee: TextView
    private lateinit var textActionStatus: TextView
    private lateinit var scrollViewFees: ScrollView
    private lateinit var feeDetailsHeader: LinearLayout
    private lateinit var feeDetailsContent: LinearLayout
    private lateinit var feedbackContainer: LinearLayout
    private lateinit var expandIcon: ImageView
    private lateinit var toggleFragmentButton: FloatingActionButton
    private lateinit var connectionServiceButton: FloatingActionButton
    private lateinit var chronometer: Chronometer
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var homeFragment: HomeFragment

    private var isExpanded = false
    private var connectionDialog: ConnectionServiceDialog? = null
    private var feesService: FeesService = FeesService()
    private var fees: RideFees = RideFees()
    private var totalRide: Double = 0.0
    private var feeMultiplier: Double = 1.0
    private var totalDistance = 0.0
    private var startingRide = false
    private var isServiceBound = false
    private var currentService: Service? = null
    private var currentPresenceState = MainViewModel.DriverPresenceState()
    private lateinit var haveArrived: String
    private lateinit var startTrip: String
    private lateinit var endTrip: String

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isServiceBound = true
            val binder = service as FeesService.ChronometerBinder
            feesService = binder.getService()
            mainViewModel.changeConnectTripService(true)
            chronometer.base = feesService.getBaseTime()
            feesService.setFeeUpdateCallback { totalFee, timeFee, distanceFee, totalDistance, elapsedSeconds ->
                mainViewModel.updateFeeData(totalFee, timeFee, distanceFee, totalDistance, elapsedSeconds)
            }
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isServiceBound = false
            mainViewModel.changeConnectTripService(false)
            chronometer.base = SystemClock.elapsedRealtime()
            chronometer.stop()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCurrentServiceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        loadFrozenFeesFromStorage()

        haveArrived = getString(R.string.service_have_arrived)
        startTrip = getString(R.string.service_start_trip)
        endTrip = getString(R.string.service_end_trip)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {}

        bindViews()
        setupStaticListeners()
        observeCurrentService()
        observeRideFees()
        observeNextService()
        observeCurrentFeeData()
        observePresence()
        observeActionUiState()
    }

    override fun onResume() {
        super.onResume()
        checkAndBindToExistingService()
        if (btnStatus.text == endTrip) {
            setupBottomSheetBehavior(BottomSheetBehavior.STATE_COLLAPSED)
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun bindViews() {
        textName = binding.serviceLayout.currentServiceName
        textPhone = binding.serviceLayout.currentPhone
        textAddress = binding.serviceLayout.currentAddress
        textDestination = binding.serviceLayout.currentDestination
        textAddressPreview = binding.serviceLayout.currentAddressPreview
        textDestinationPreview = binding.serviceLayout.currentDestinationPreview
        textComment = binding.serviceLayout.serviceComment
        textPriceBase = binding.textBaseFare
        textPriceMinFee = binding.textFareMin
        textPriceAddFee = binding.textFees
        textDistancePrice = binding.textDistanceFare
        textCurrentDistance = binding.textCurrentDistance
        textCurrentDistancePrice = binding.textPriceByDistance
        toggleFragmentButton = binding.toggleButton
        textTimePrice = binding.textTimeFare
        textCurrentTimePrice = binding.textPriceByTime
        textFareMultiplier = binding.textFareMultiplier
        textFareMultiplierDisplay = binding.textFareMultiplierDisplay
        textTotalFee = binding.textPrice
        btnStatus = binding.btnServiceStatus
        btnRetryAction = binding.btnRetryServiceAction
        imgBtnMaps = binding.serviceLayout.imgBtnMaps
        imgButtonWaze = binding.serviceLayout.imgBtnWaze
        chronometer = binding.chronometer
        scrollViewFees = binding.scrollViewFees
        feeDetailsHeader = binding.feeDetailsHeader
        feeDetailsContent = binding.feeDetailsContent
        feedbackContainer = binding.serviceActionFeedback
        textActionStatus = binding.textServiceActionStatus
        expandIcon = binding.expandIcon
        homeFragment = HomeFragment()
        connectionServiceButton = binding.connectedServiceButton
    }

    private fun setupStaticListeners() {
        btnStatus.setOnClickListener {
            val service = currentService ?: return@setOnClickListener

            when (baseActionText(service)) {
                haveArrived -> handleHaveArrived(service)
                startTrip -> beginStartTrip(service)
                else -> beginEndTrip(service)
            }
        }

        btnRetryAction.setOnClickListener {
            retryCurrentAction()
        }

        connectionServiceButton.setOnClickListener {
            connectionDialog?.let { dialog ->
                if (dialog.isAdded) {
                    dialog.dismiss()
                } else {
                    dialog.show(childFragmentManager, ServiceDialogFragment::javaClass.toString())
                }
            }
        }

        textFareMultiplier.setOnClickListener {
            showEditMultiplierDialog()
        }

        setupFeeDetailsCollapse()
        setupBottomSheetBehavior(BottomSheetBehavior.STATE_EXPANDED)

        toggleFragmentButton.setOnClickListener {
            toggleFragment()
        }
    }

    private fun observeCurrentService() {
        mainViewModel.currentService.observe(viewLifecycleOwner) { service ->
            currentService = service
            toggleFragmentButton.visibility = View.GONE

            if (service == null) {
                currentServiceViewModel.reset()
                stopFeeService()
                renderServiceActionUi(null)
                return@observe
            }

            bindServiceDetails(service)
            bindTripStage(service)
            renderServiceActionUi(service)
        }
    }

    private fun observeRideFees() {
        mainViewModel.rideFees.observe(viewLifecycleOwner) { rideFees ->
            applyRideFees(rideFees)
        }
    }

    private fun observeNextService() {
        mainViewModel.nextService.observe(viewLifecycleOwner) { service ->
            if (service != null) {
                connectionDialog = ConnectionServiceDialog(service)
                connectionServiceButton.visibility = View.VISIBLE
                toggleFragmentButton.visibility = View.GONE
            } else {
                connectionDialog = null
                connectionServiceButton.visibility = View.INVISIBLE
            }
        }
    }

    private fun observeCurrentFeeData() {
        mainViewModel.currentFeeData.observe(viewLifecycleOwner) { feeData ->
            totalRide = feeData.totalFee
            totalDistance = feeData.totalDistance

            chronometer.base = SystemClock.elapsedRealtime() - (feeData.elapsedSeconds * 1000)

            textTotalFee.text = NumberHelper.toCurrency(feeData.totalFee)
            textCurrentTimePrice.text = NumberHelper.toCurrency(feeData.timeFee)
            textCurrentDistancePrice.text = NumberHelper.toCurrency(feeData.distanceFee)
            textCurrentDistance.text = getString(R.string.distance_km, feeData.totalDistance / 1000)
            textFareMultiplierDisplay.text = feeMultiplier.toString()

            if (mainViewModel.nextService.value == null) {
                if (feeData.elapsedSeconds > fees.timeoutToConnection && !toggleFragmentButton.isVisible) {
                    toggleFragmentButton.visibility = View.VISIBLE
                }
            } else {
                toggleFragmentButton.visibility = View.GONE
            }
        }
    }

    private fun observePresence() {
        currentPresenceState = mainViewModel.presenceState.value
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.presenceState.collect { presence ->
                    currentPresenceState = presence
                    renderServiceActionUi(currentService)
                }
            }
        }
    }

    private fun observeActionUiState() {
        currentServiceViewModel.uiState.observe(viewLifecycleOwner) {
            renderServiceActionUi(currentService)
        }
    }

    private fun bindServiceDetails(service: Service) {
        textName.text = service.name
        textPhone.text = service.phone
        textAddress.text = service.start_loc.name
        textAddressPreview.text = service.start_loc.name
        textComment.text = service.comment

        val destinationName = service.end_loc?.name?.takeIf { it.isNotBlank() }
        if (destinationName != null) {
            binding.serviceLayout.destinationContainer.visibility = View.VISIBLE
            binding.serviceLayout.destinationDivider.visibility = View.VISIBLE
            textDestination.text = destinationName
            textDestinationPreview.visibility = View.VISIBLE
            textDestinationPreview.text = destinationName
        } else {
            binding.serviceLayout.destinationContainer.visibility = View.GONE
            binding.serviceLayout.destinationDivider.visibility = View.GONE
            textDestination.text = ""
            textDestinationPreview.visibility = View.GONE
        }

        textPhone.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, ("tel:" + service.phone).toUri())
            startActivity(intent)
        }

        imgBtnMaps.setOnClickListener {
            val uri = String.format(
                Locale.ENGLISH,
                "google.navigation:q=%f,%f",
                service.start_loc.lat,
                service.start_loc.lng
            )
            val mapIntent = Intent(Intent.ACTION_VIEW, uri.toUri())
            mapIntent.setPackage("com.google.android.apps.maps")
            activity?.let { fragmentActivity ->
                mapIntent.resolveActivity(fragmentActivity.packageManager)?.let {
                    startActivity(mapIntent)
                }
            }
        }

        imgButtonWaze.setOnClickListener {
            val uri = String.format(
                Locale.ENGLISH,
                "waze://?ll=%f,%f&navigate=yes",
                service.start_loc.lat,
                service.start_loc.lng
            )
            val wazeIntent = Intent(Intent.ACTION_VIEW, uri.toUri())
            try {
                startActivity(wazeIntent)
            } catch (_: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.not_waze, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun bindTripStage(service: Service) {
        if (!service.isInProgress()) {
            return
        }

        when {
            service.metadata.arrived_at == null -> {
                btnStatus.text = haveArrived
                scrollViewFees.visibility = View.INVISIBLE
                stopFeeService()
            }
            service.metadata.start_trip_at == null -> {
                btnStatus.text = startTrip
                scrollViewFees.visibility = View.INVISIBLE
                stopFeeService()
            }
            else -> {
                btnStatus.text = endTrip
                currentServiceViewModel.onTripStartedObserved()
                maybeRestoreOngoingTrip(service)
                scrollViewFees.visibility = View.VISIBLE
            }
        }
    }

    private fun maybeRestoreOngoingTrip(service: Service) {
        if (ServiceHelper.isServiceRunning(requireContext(), FeesService::class.java) || startingRide) {
            return
        }

        val builder = AlertDialog.Builder(requireContext())
        builder.setTitle(R.string.service_start_trip)
        builder.setCancelable(false)
        builder.setMessage(R.string.ride_in_progress)
        builder.setNegativeButton(R.string.no) { dialog, _ ->
            dialog.dismiss()
            scrollViewFees.visibility = View.VISIBLE
            sharedPreferences.edit(commit = true) {
                remove(Constants.MULTIPLIER)
                remove(Constants.POINTS)
                remove(Constants.START_TIME)
                remove(FeesService.TOTAL_DISTANCE)
            }
            totalRide = 0.0
            startServiceFee(service.start_loc.name)
            startingRide = true
        }
        builder.setPositiveButton(R.string.yes) { _, _ ->
            startServiceFee(service.start_loc.name, true)
            scrollViewFees.visibility = View.VISIBLE
            startingRide = false
        }
        builder.create().show()
    }

    private fun handleHaveArrived(service: Service) {
        val now = Date().time / 1000
        mainViewModel.setLoading(true)
        service.metadata.arrived_at = now
        service.updateMetadata()
            .addOnSuccessListener {
                mainViewModel.setLoading(false)
                Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                mainViewModel.setLoading(false)
                btnStatus.text = haveArrived
                Log.e(TAG, exception.message ?: "Unable to mark arrived")
                Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
            }
    }

    private fun beginStartTrip(service: Service) {
        if (!CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)) {
            currentServiceViewModel.clearStartTripRequest()
            currentServiceViewModel.showBlockedStartByConnection()
            return
        }

        val attemptId = currentServiceViewModel.newAttempt()
        currentServiceViewModel.showPreparingStart()
        mainViewModel.setLoading(true)

        SettingsRepository.getRideFeesTask()
            .addOnSuccessListener { liveFees ->
                if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                    return@addOnSuccessListener
                }

                val resolution = CurrentServiceViewModel.resolveStartRideFees(
                    liveFees = liveFees,
                    inMemoryFees = fees,
                    storedFees = getStoredRideFeesSnapshot(),
                    currentMultiplier = feeMultiplier,
                    storedMultiplier = sharedPreferences.getString(Constants.MULTIPLIER, null)?.toDoubleOrNull()
                )
                onStartRideFeesResolved(service, attemptId, resolution)
            }
            .addOnFailureListener { exception ->
                if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                    return@addOnFailureListener
                }

                Log.w(TAG, "Unable to refresh ride fees before trip start", exception)
                val resolution = CurrentServiceViewModel.resolveStartRideFees(
                    liveFees = null,
                    inMemoryFees = fees,
                    storedFees = getStoredRideFeesSnapshot(),
                    currentMultiplier = feeMultiplier,
                    storedMultiplier = sharedPreferences.getString(Constants.MULTIPLIER, null)?.toDoubleOrNull()
                )
                onStartRideFeesResolved(service, attemptId, resolution)
            }
            .withTimeout(RIDE_FEES_TIMEOUT_MS) {
                if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                    return@withTimeout
                }

                Log.w(TAG, "Ride fees refresh timed out attemptId=$attemptId")
                val resolution = CurrentServiceViewModel.resolveStartRideFees(
                    liveFees = null,
                    inMemoryFees = fees,
                    storedFees = getStoredRideFeesSnapshot(),
                    currentMultiplier = feeMultiplier,
                    storedMultiplier = sharedPreferences.getString(Constants.MULTIPLIER, null)?.toDoubleOrNull()
                )
                onStartRideFeesResolved(service, attemptId, resolution)
            }
    }

    private fun onStartRideFeesResolved(
        service: Service,
        attemptId: Long,
        resolution: CurrentServiceViewModel.StartRideFeesResolution
    ) {
        if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
            return
        }

        when (resolution.source) {
            CurrentServiceViewModel.StartRideFeesSource.LIVE -> {
                resolution.fees?.let {
                    applyRideFees(it)
                    mainViewModel.setRideFees(it)
                    persistRideFeesSnapshot(it)
                }
                mainViewModel.setLoading(false)
                currentServiceViewModel.showIdle()
                setupBottomSheetBehavior(BottomSheetBehavior.STATE_COLLAPSED)
                showStartTripDialog(service)
            }
            CurrentServiceViewModel.StartRideFeesSource.FALLBACK -> {
                resolution.fees?.let {
                    applyRideFees(it)
                    mainViewModel.setRideFees(it)
                }
                mainViewModel.setLoading(false)
                currentServiceViewModel.showIdle()
                showToast(R.string.using_saved_pricing_snapshot)
                setupBottomSheetBehavior(BottomSheetBehavior.STATE_COLLAPSED)
                showStartTripDialog(service)
            }
            CurrentServiceViewModel.StartRideFeesSource.UNAVAILABLE -> {
                mainViewModel.setLoading(false)
                currentServiceViewModel.showStartFailed(
                    messageRes = R.string.start_trip_pricing_unavailable,
                    canRetry = true
                )
            }
        }
    }

    private fun showStartTripDialog(service: Service) {
        val dialogLayout: View = LayoutInflater.from(activity).inflate(R.layout.multiplier_feed, null)
        val editFeeMultiplier = dialogLayout.findViewById<EditText>(R.id.dialog_fee_multiplier)
        editFeeMultiplier.text = Editable.Factory.getInstance().newEditable(feeMultiplier.toString())

        showTripActionDialog(
            titleRes = R.string.start_ride,
            message = getText(R.string.start_ride_message),
            primaryTextRes = R.string.start_ride_action,
            secondaryTextRes = R.string.cancel,
            iconRes = R.drawable.add_24,
            primaryIconRes = R.drawable.assign_24,
            secondaryIconRes = R.drawable.cancel_24,
            customBody = dialogLayout
        ) { confirmed ->
            if (!confirmed) {
                currentServiceViewModel.showIdle()
                return@showTripActionDialog
            }

            val inputMultiplier = editFeeMultiplier.text.toString().toDoubleOrNull() ?: 1.0
            feeMultiplier = if (inputMultiplier < 1.0) 1.0 else inputMultiplier
            textFareMultiplier.text = feeMultiplier.toString()
            sharedPreferences.edit(commit = true) {
                putString(Constants.MULTIPLIER, feeMultiplier.toString())
            }

            val request = CurrentServiceViewModel.StartTripRequest(
                serviceId = service.id,
                startedAt = Date().time / 1000,
                multiplier = feeMultiplier,
                origin = service.start_loc.name
            )
            currentServiceViewModel.rememberStartTripRequest(request)
            executeConfirmedStartTrip(request)
        }
    }

    private fun executeConfirmedStartTrip(request: CurrentServiceViewModel.StartTripRequest) {
        if (!CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)) {
            currentServiceViewModel.showBlockedStartByConnection()
            return
        }

        val driverId = mainViewModel.driver.value?.id ?: return
        val attemptId = currentServiceViewModel.newAttempt()
        currentServiceViewModel.showStartingTrip()
        mainViewModel.setLoading(true)

        ServiceRepository.validateServiceForStart(request.serviceId, driverId)
            .addOnSuccessListener { validatedService ->
                if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                    return@addOnSuccessListener
                }

                val metadata = validatedService.metadata.copy(start_trip_at = request.startedAt)
                ServiceRepository.updateMetadata(validatedService.id, metadata, validatedService.status)
                    .addOnSuccessListener {
                        if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                            return@addOnSuccessListener
                        }

                        startingRide = true
                        startServiceFee(request.origin)
                        mainViewModel.setLoading(false)
                    }
                    .addOnFailureListener { exception ->
                        if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                            return@addOnFailureListener
                        }

                        Log.e(TAG, "Trip start write failed", exception)
                        mainViewModel.setLoading(false)
                        currentServiceViewModel.showStartFailed(
                            messageRes = R.string.common_error,
                            canRetry = true
                        )
                    }
                    .withTimeout(START_WRITE_TIMEOUT_MS) {
                        if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                            return@withTimeout
                        }

                        Log.w(TAG, "Trip start write timed out attemptId=$attemptId")
                        mainViewModel.setLoading(false)
                        currentServiceViewModel.showStartFailed(
                            messageRes = R.string.error_timeout,
                            canRetry = true
                        )
                    }
            }
            .addOnFailureListener { exception ->
                if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                    return@addOnFailureListener
                }

                handleStartValidationFailure(exception)
            }
            .withTimeout(START_VALIDATION_TIMEOUT_MS) {
                if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                    return@withTimeout
                }

                Log.w(TAG, "Trip start validation timed out attemptId=$attemptId")
                mainViewModel.setLoading(false)
                currentServiceViewModel.showStartFailed(
                    messageRes = R.string.error_timeout,
                    canRetry = true
                )
            }
    }

    private fun handleStartValidationFailure(exception: Exception) {
        mainViewModel.setLoading(false)
        val message = exception.message.orEmpty()
        val messageRes = when {
            message.contains("does not exist", ignoreCase = true) -> R.string.service_not_exists
            message.contains("another driver", ignoreCase = true) -> R.string.service_not_available
            message.contains("no longer in progress", ignoreCase = true) -> R.string.service_not_available
            message.contains("already terminated", ignoreCase = true) -> R.string.service_not_available
            message.contains("already started", ignoreCase = true) -> R.string.service_trip_already_started
            else -> null
        }

        if (messageRes != null) {
            currentServiceViewModel.clearStartTripRequest()
            currentServiceViewModel.showIdle()
            showToast(messageRes)
        } else {
            Log.e(TAG, "Trip start validation failed", exception)
            currentServiceViewModel.showStartFailed(
                messageRes = R.string.common_error,
                canRetry = true
            )
        }
    }

    private fun beginEndTrip(service: Service) {
        val now = Date().time / 1000
        if (service.metadata.start_trip_at == null ||
            now - service.metadata.start_trip_at!! <= fees.timeoutToComplete
        ) {
            Toast.makeText(
                requireContext(),
                getString(R.string.cannot_complete_service_yet, fees.timeoutToComplete / 60),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        currentServiceViewModel.showPreparingEnd()
        if (!CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)) {
            currentServiceViewModel.showBlockedEndByConnection()
            return
        }

        currentServiceViewModel.showIdle()
        val message = getString(R.string.finalizing_message, NumberHelper.toCurrency(getTotalFee(), true))
        showTripActionDialog(
            titleRes = R.string.finalize_service,
            message = StringHelper.getString(message),
            primaryTextRes = R.string.yes,
            secondaryTextRes = R.string.no,
            iconRes = R.drawable.ic_monetization_on_24,
            primaryIconRes = R.drawable.assign_24,
            secondaryIconRes = R.drawable.cancel_24
        ) { confirmed ->
            if (!confirmed) {
                currentServiceViewModel.showIdle()
                return@showTripActionDialog
            }

            val request = CurrentServiceViewModel.EndTripRequest(
                serviceId = service.id,
                endedAt = now,
                route = if (isServiceBound) {
                    ServiceMetadata.serializeRoute(feesService.getPoints())
                } else {
                    ServiceMetadata.serializeRoute(arrayListOf())
                },
                tripDistance = NumberHelper.roundDouble(totalDistance).toInt(),
                tripFee = NumberHelper.roundDouble(getTotalFee()).toInt(),
                multiplier = feeMultiplier
            )
            currentServiceViewModel.rememberEndTripRequest(request)
            executeConfirmedEndTrip(request)
        }
    }

    private fun executeConfirmedEndTrip(request: CurrentServiceViewModel.EndTripRequest) {
        if (!CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)) {
            currentServiceViewModel.showBlockedEndByConnection()
            return
        }

        val driverId = mainViewModel.driver.value?.id ?: return
        val attemptId = currentServiceViewModel.newAttempt()
        currentServiceViewModel.showEndingTrip()
        mainViewModel.setLoading(true)

        ServiceRepository.validateServiceForEnd(request.serviceId, driverId)
            .addOnSuccessListener { validatedService ->
                if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                    return@addOnSuccessListener
                }

                val metadata = validatedService.metadata.copy(
                    end_trip_at = request.endedAt,
                    route = request.route,
                    trip_distance = request.tripDistance,
                    trip_fee = request.tripFee,
                    trip_multiplier = request.multiplier
                )
                ServiceRepository.updateMetadata(validatedService.id, metadata, Service.STATUS_TERMINATED)
                    .addOnSuccessListener {
                        if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                            return@addOnSuccessListener
                        }

                        mainViewModel.setLoading(false)
                        if (CurrentServiceViewModel.endTripOutcome(writeSucceeded = true) ==
                            CurrentServiceViewModel.EndTripLocalOutcome.FINISH_LOCALLY
                        ) {
                            currentServiceViewModel.reset()
                            stopFeeService()
                            mainViewModel.completeCurrentService()
                            Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener { exception ->
                        if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                            return@addOnFailureListener
                        }

                        Log.e(TAG, "Trip end write failed", exception)
                        mainViewModel.setLoading(false)
                        currentServiceViewModel.showEndFailed(
                            messageRes = R.string.common_error,
                            canRetry = true
                        )
                    }
                    .withTimeout(END_WRITE_TIMEOUT_MS) {
                        if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                            return@withTimeout
                        }

                        Log.w(TAG, "Trip end write timed out attemptId=$attemptId")
                        mainViewModel.setLoading(false)
                        currentServiceViewModel.showEndFailed(
                            messageRes = R.string.error_timeout,
                            canRetry = true
                        )
                    }
            }
            .addOnFailureListener { exception ->
                if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                    return@addOnFailureListener
                }

                handleEndValidationFailure(exception)
            }
            .withTimeout(END_VALIDATION_TIMEOUT_MS) {
                if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                    return@withTimeout
                }

                Log.w(TAG, "Trip end validation timed out attemptId=$attemptId")
                mainViewModel.setLoading(false)
                currentServiceViewModel.showEndFailed(
                    messageRes = R.string.error_timeout,
                    canRetry = true
                )
            }
    }

    private fun handleEndValidationFailure(exception: Exception) {
        mainViewModel.setLoading(false)
        val message = exception.message.orEmpty()

        when {
            message.contains("already terminated", ignoreCase = true) -> {
                currentServiceViewModel.reset()
                stopFeeService()
                mainViewModel.completeCurrentService()
            }
            message.contains("does not exist", ignoreCase = true) -> {
                currentServiceViewModel.clearEndTripRequest()
                currentServiceViewModel.showIdle()
                showToast(R.string.service_not_exists)
            }
            message.contains("another driver", ignoreCase = true) ||
                message.contains("no longer in progress", ignoreCase = true) -> {
                currentServiceViewModel.clearEndTripRequest()
                currentServiceViewModel.showIdle()
                showToast(R.string.service_not_available)
            }
            message.contains("has not started yet", ignoreCase = true) -> {
                currentServiceViewModel.clearEndTripRequest()
                currentServiceViewModel.showIdle()
                showToast(R.string.service_trip_not_started)
            }
            else -> {
                Log.e(TAG, "Trip end validation failed", exception)
                currentServiceViewModel.showEndFailed(
                    messageRes = R.string.common_error,
                    canRetry = true
                )
            }
        }
    }

    private fun retryCurrentAction() {
        when (currentServiceViewModel.uiState.value) {
            CurrentServiceViewModel.ServiceActionUiState.BlockedStartByConnection,
            is CurrentServiceViewModel.ServiceActionUiState.StartFailed -> {
                currentServiceViewModel.getStartTripRequest()?.let {
                    executeConfirmedStartTrip(it)
                } ?: currentService?.let { service ->
                    beginStartTrip(service)
                }
            }
            CurrentServiceViewModel.ServiceActionUiState.BlockedEndByConnection,
            is CurrentServiceViewModel.ServiceActionUiState.EndFailed -> {
                currentServiceViewModel.getEndTripRequest()?.let {
                    executeConfirmedEndTrip(it)
                } ?: currentService?.let { service ->
                    beginEndTrip(service)
                }
            }
            else -> {}
        }
    }

    private fun renderServiceActionUi(service: Service?) {
        val state = currentServiceViewModel.uiState.value ?: CurrentServiceViewModel.ServiceActionUiState.Idle
        if (service == null) {
            feedbackContainer.isGone = true
            binding.serviceActionProgress.isGone = true
            textActionStatus.isGone = true
            btnRetryAction.isGone = true
            btnStatus.isEnabled = false
            return
        }

        val baseAction = baseActionText(service)
        btnStatus.text = baseAction

        when (state) {
            CurrentServiceViewModel.ServiceActionUiState.Idle -> {
                feedbackContainer.isGone = true
                btnStatus.isEnabled = true
            }
            CurrentServiceViewModel.ServiceActionUiState.PreparingStart -> {
                showFeedback(
                    statusText = getString(R.string.starting_trip),
                    showProgress = true,
                    retryTextRes = null,
                    retryEnabled = false
                )
                btnStatus.isEnabled = false
            }
            CurrentServiceViewModel.ServiceActionUiState.BlockedStartByConnection -> {
                showFeedback(
                    statusText = blockedStartMessage(),
                    showProgress = false,
                    retryTextRes = R.string.retry_start_trip,
                    retryEnabled = CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)
                )
                btnStatus.isEnabled = false
            }
            CurrentServiceViewModel.ServiceActionUiState.StartingTrip -> {
                showFeedback(
                    statusText = getString(R.string.starting_trip),
                    showProgress = true,
                    retryTextRes = null,
                    retryEnabled = false
                )
                btnStatus.isEnabled = false
            }
            is CurrentServiceViewModel.ServiceActionUiState.StartFailed -> {
                showFeedback(
                    statusText = failedStartMessage(state.messageRes),
                    showProgress = false,
                    retryTextRes = R.string.retry_start_trip,
                    retryEnabled = state.canRetry && CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)
                )
                btnStatus.isEnabled = false
            }
            CurrentServiceViewModel.ServiceActionUiState.PreparingEnd -> {
                showFeedback(
                    statusText = getString(R.string.ending_trip),
                    showProgress = true,
                    retryTextRes = null,
                    retryEnabled = false
                )
                btnStatus.isEnabled = false
            }
            CurrentServiceViewModel.ServiceActionUiState.BlockedEndByConnection -> {
                showFeedback(
                    statusText = blockedEndMessage(),
                    showProgress = false,
                    retryTextRes = R.string.retry_end_trip,
                    retryEnabled = CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)
                )
                btnStatus.isEnabled = false
            }
            CurrentServiceViewModel.ServiceActionUiState.EndingTrip -> {
                showFeedback(
                    statusText = getString(R.string.ending_trip),
                    showProgress = true,
                    retryTextRes = null,
                    retryEnabled = false
                )
                btnStatus.isEnabled = false
            }
            is CurrentServiceViewModel.ServiceActionUiState.EndFailed -> {
                showFeedback(
                    statusText = failedEndMessage(state.messageRes),
                    showProgress = false,
                    retryTextRes = R.string.retry_end_trip,
                    retryEnabled = state.canRetry && CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)
                )
                btnStatus.isEnabled = false
            }
        }
    }

    private fun showFeedback(
        statusText: CharSequence,
        showProgress: Boolean,
        @StringRes retryTextRes: Int?,
        retryEnabled: Boolean
    ) {
        feedbackContainer.isVisible = true
        textActionStatus.isVisible = true
        textActionStatus.text = statusText
        binding.serviceActionProgress.isVisible = showProgress

        if (retryTextRes != null) {
            btnRetryAction.isVisible = true
            btnRetryAction.isEnabled = retryEnabled
            btnRetryAction.setText(retryTextRes)
        } else {
            btnRetryAction.isGone = true
        }
    }

    private fun blockedStartMessage(): String {
        return if (CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)) {
            getString(R.string.connection_restored_retry_start)
        } else {
            getString(R.string.reconnecting_dispatch_trip_not_started)
        }
    }

    private fun blockedEndMessage(): String {
        return if (CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)) {
            getString(R.string.connection_restored_retry_end)
        } else {
            getString(R.string.reconnecting_dispatch_trip_not_ended)
        }
    }

    private fun failedStartMessage(@StringRes messageRes: Int): String {
        return if (CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)) {
            getString(R.string.connection_restored_retry_start) + "\n" +
                getString(messageRes) + "\n" +
                getString(R.string.start_trip_not_started_yet)
        } else {
            getString(messageRes) + "\n" + getString(R.string.start_trip_not_started_yet)
        }
    }

    private fun failedEndMessage(@StringRes messageRes: Int): String {
        return if (CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)) {
            getString(R.string.connection_restored_retry_end) + "\n" +
                getString(messageRes) + "\n" +
                getString(R.string.end_trip_not_ended_yet)
        } else {
            getString(messageRes) + "\n" + getString(R.string.end_trip_not_ended_yet)
        }
    }

    private fun baseActionText(service: Service): String {
        return when {
            service.metadata.arrived_at == null -> haveArrived
            service.metadata.start_trip_at == null -> startTrip
            else -> endTrip
        }
    }

    private fun checkAndBindToExistingService() {
        if (!isServiceBound && ServiceHelper.isServiceRunning(requireContext(), FeesService::class.java)) {
            val intentFee = Intent(requireContext(), FeesService::class.java)
            requireContext().bindService(intentFee, serviceConnection, BIND_NOT_FOREGROUND)
        }
    }

    private fun setupBottomSheetBehavior(state: Int) {
        val serviceLayoutView = binding.serviceLayout.root
        BottomSheetBehavior.from(serviceLayoutView).state = state
    }

    private fun setupFeeDetailsCollapse() {
        feeDetailsHeader.setOnClickListener {
            toggleFeeDetails()
        }
    }

    private fun toggleFeeDetails() {
        isExpanded = !isExpanded
        if (isExpanded) {
            feeDetailsContent.visibility = View.VISIBLE
            expandIcon.animate().rotation(180f).setDuration(200).start()
        } else {
            feeDetailsContent.visibility = View.GONE
            expandIcon.animate().rotation(0f).setDuration(200).start()
        }
    }

    private fun toggleFragment() {
        val ft = childFragmentManager.beginTransaction()

        if (homeFragment.isAdded) {
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
            ft.remove(homeFragment)
            toggleFragmentButton.setImageResource(R.drawable.service_list_24)
        } else {
            ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            ft.replace(binding.root.id, homeFragment)
            toggleFragmentButton.setImageResource(R.drawable.current_return_24)
        }
        ft.commit()
    }

    private fun startServiceFee(origin: String, resumeRide: Boolean = false) {
        val intentFee = Intent(requireContext(), FeesService::class.java)
        intentFee.putExtra(ORIGIN, origin)
        intentFee.putExtra(FEE_MULTIPLIER, feeMultiplier)

        persistRideFeesSnapshot(fees)

        if (resumeRide && sharedPreferences.contains(Constants.MULTIPLIER)) {
            intentFee.putExtra(RESUME_RIDE, true)
            val savedMultiplier = sharedPreferences.getString(Constants.MULTIPLIER, "1.0")?.toDoubleOrNull() ?: 1.0
            feeMultiplier = savedMultiplier
            textFareMultiplier.text = feeMultiplier.toString()
        }

        if (ServiceHelper.isServiceRunning(requireContext(), FeesService::class.java)) {
            val stopIntent = Intent(requireContext(), FeesService::class.java)
            requireContext().stopService(stopIntent)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            requireContext().startForegroundService(intentFee)
        } else {
            requireContext().startService(intentFee)
        }

        requireContext().bindService(intentFee, serviceConnection, BIND_NOT_FOREGROUND)
    }

    private fun applyRideFees(rideFees: RideFees) {
        fees = rideFees
        feeMultiplier = rideFees.feeMultiplier
        textPriceBase.text = NumberHelper.toCurrency(rideFees.feesBase)
        textPriceMinFee.text = NumberHelper.toCurrency(rideFees.priceMinFee)
        textPriceAddFee.text = NumberHelper.toCurrency(rideFees.priceAddFee)
        textDistancePrice.text = NumberHelper.toCurrency(rideFees.priceKm)
        textTimePrice.text = NumberHelper.toCurrency(rideFees.priceMin)
        textFareMultiplier.text = feeMultiplier.toString()
        if (isServiceBound) {
            feesService.setMultiplier(feeMultiplier)
        }
    }

    private fun persistRideFeesSnapshot(rideFees: RideFees) {
        val feesJson = com.google.gson.Gson().toJson(rideFees)
        sharedPreferences.edit(commit = true) {
            putString(CURRENT_FEES, feesJson)
        }
    }

    private fun getStoredRideFeesSnapshot(): RideFees? {
        val feesJson = sharedPreferences.getString(CURRENT_FEES, null) ?: return null

        return try {
            com.google.gson.Gson().fromJson(feesJson, RideFees::class.java)
        } catch (exception: Exception) {
            Log.e(TAG, exception.message ?: "Unable to restore pricing snapshot")
            null
        }
    }

    private fun loadFrozenFeesFromStorage() {
        try {
            val loadedFees = getStoredRideFeesSnapshot() ?: return
            applyRideFees(loadedFees)
            mainViewModel.setRideFees(loadedFees)
        } catch (exception: Exception) {
            Log.e(TAG, exception.message ?: "Unable to restore pricing snapshot")
        }
    }

    private fun showEditMultiplierDialog() {
        val builder = AlertDialog.Builder(requireContext())
        val dialogLayout: View = LayoutInflater.from(activity).inflate(R.layout.multiplier_feed, null)
        val editFeeMultiplier = dialogLayout.findViewById<EditText>(R.id.dialog_fee_multiplier)

        val currentMultiplier = if (isServiceBound) {
            feesService.getMultiplier()
        } else {
            feeMultiplier
        }

        editFeeMultiplier.text = Editable.Factory.getInstance().newEditable(currentMultiplier.toString())
        builder.setTitle(R.string.edit_multiplier).setView(dialogLayout)
        builder.setNegativeButton(R.string.cancel) { dialog, _ ->
            dialog.dismiss()
        }
        builder.setPositiveButton(R.string.save) { _, _ ->
            val inputValue = editFeeMultiplier.text.toString().toDoubleOrNull() ?: 1.0
            val newMultiplier = if (inputValue < 1.0) {
                Toast.makeText(requireContext(), R.string.multiplier_minimum_value, Toast.LENGTH_SHORT).show()
                1.0
            } else {
                inputValue
            }

            feeMultiplier = newMultiplier
            textFareMultiplier.text = newMultiplier.toString()

            if (isServiceBound) {
                feesService.setMultiplier(newMultiplier)
            }

            sharedPreferences.edit(commit = true) {
                putString(Constants.MULTIPLIER, newMultiplier.toString())
            }

            Toast.makeText(
                requireContext(),
                getString(R.string.multiplier_updated, newMultiplier),
                Toast.LENGTH_SHORT
            ).show()
        }
        builder.create().show()
    }

    private fun showTripActionDialog(
        titleRes: Int,
        message: CharSequence,
        primaryTextRes: Int,
        secondaryTextRes: Int,
        iconRes: Int,
        primaryIconRes: Int,
        secondaryIconRes: Int,
        customBody: View? = null,
        onActionSelected: (Boolean) -> Unit
    ) {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_trip_action, null)
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .create()

        dialog.setCancelable(false)
        dialog.setCanceledOnTouchOutside(false)

        val iconView = dialogView.findViewById<ImageView>(R.id.dialogIcon)
        val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
        val messageView = dialogView.findViewById<TextView>(R.id.dialogMessage)
        val bodyContainer = dialogView.findViewById<FrameLayout>(R.id.dialogBodyContainer)
        val primaryButton = dialogView.findViewById<MaterialButton>(R.id.btnPrimary)
        val secondaryButton = dialogView.findViewById<MaterialButton>(R.id.btnSecondary)

        iconView.setImageResource(iconRes)
        titleView.setText(titleRes)
        messageView.text = message
        primaryButton.setText(primaryTextRes)
        primaryButton.setIconResource(primaryIconRes)
        secondaryButton.setText(secondaryTextRes)
        secondaryButton.setIconResource(secondaryIconRes)

        if (customBody != null) {
            (customBody.parent as? ViewGroup)?.removeView(customBody)
            bodyContainer.visibility = View.VISIBLE
            bodyContainer.addView(customBody)
        } else {
            bodyContainer.visibility = View.GONE
        }

        primaryButton.setOnClickListener {
            dialog.dismiss()
            onActionSelected(true)
        }

        secondaryButton.setOnClickListener {
            dialog.dismiss()
            onActionSelected(false)
        }

        dialog.show()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    private fun stopFeeService() {
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection)
            isServiceBound = false
        }

        val intentFee = Intent(requireContext(), FeesService::class.java)
        requireContext().stopService(intentFee)

        chronometer.stop()
        chronometer.base = SystemClock.elapsedRealtime()

        sharedPreferences.edit(commit = true) {
            remove(Constants.MULTIPLIER)
            remove(Constants.POINTS)
            remove(Constants.START_TIME)
            remove(CURRENT_FEES)
            remove(FeesService.TOTAL_DISTANCE)
        }

        mainViewModel.changeConnectTripService(false)
        toggleFragmentButton.visibility = View.GONE
    }

    private fun getTotalFee(): Double {
        return if (isServiceBound) {
            feesService.getTotalFee()
        } else {
            totalRide
        }
    }

    private fun showToast(@StringRes messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }
}
