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
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.annotation.StringRes
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.net.toUri
import androidx.core.view.doOnLayout
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
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
import gorda.driver.background.FeesService.Companion.FEE_MULTIPLIER
import gorda.driver.background.FeesService.Companion.ORIGIN
import gorda.driver.background.FeesService.Companion.RESUME_RIDE
import gorda.driver.databinding.FragmentCurrentServiceBinding
import gorda.driver.helpers.withTimeout
import gorda.driver.interfaces.RideFees
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import gorda.driver.repositories.ServiceRepository
import gorda.driver.repositories.ServiceRepository.EndTransitionPayload
import gorda.driver.repositories.ServiceRepository.StartTransitionPayload
import gorda.driver.repositories.SettingsRepository
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.home.HomeFragment
import gorda.driver.ui.service.ConnectionServiceDialog
import gorda.driver.utils.Constants
import gorda.driver.utils.NumberHelper
import gorda.driver.utils.RideRecoveryPolicy
import gorda.driver.utils.RideRecoveryStore
import gorda.driver.utils.ServiceHelper
import gorda.driver.utils.showTripActionDialog
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
    private lateinit var btnCancelSelfService: MaterialButton
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
    private lateinit var fareMultiplierChip: View
    private lateinit var scrollViewFees: ScrollView
    private lateinit var feeDetailsHeader: LinearLayout
    private lateinit var feeDetailsContent: LinearLayout
    private lateinit var collapsedHeader: View
    private lateinit var feedbackContainer: LinearLayout
    private lateinit var expandIcon: ImageView
    private lateinit var toggleFragmentButton: FloatingActionButton
    private lateinit var connectionServiceButton: FloatingActionButton
    private lateinit var chronometer: Chronometer
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var homeFragment: HomeFragment

    private var isExpanded = false
    private var ongoingTripRecoveryDialog: AlertDialog? = null
    private var ongoingTripRecoveryServiceId: String? = null
    private var feesService: FeesService = FeesService()
    private var fees: RideFees = RideFees()
    private var totalRide: Double = 0.0
    private var feeMultiplier: Double = 1.0
    private var totalDistance = 0.0
    private var currentRideElapsedSeconds = 0L
    private var baseScrollViewBottomPadding = 0
    private var startingRide = false
    private var isServiceBound = false
    private var currentService: Service? = null
    private var currentPresenceState = MainViewModel.DriverPresenceState()
    private var shouldReconcileRestoredAction = true
    private var deferredInitialNullRecoveryClear = false
    private var cancelingSelfService = false
    private lateinit var haveArrived: String
    private lateinit var startTrip: String
    private lateinit var endTrip: String
    private lateinit var bottomSheetBehavior: BottomSheetBehavior<ConstraintLayout>

    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onStateChanged(bottomSheet: View, newState: Int) {
            updateFeesScrollBottomPadding()
            val serviceId = currentService?.id ?: return
            when (newState) {
                BottomSheetBehavior.STATE_EXPANDED -> {
                    currentServiceViewModel.updateBottomSheetExpanded(serviceId, true)
                    syncRecoveryStore()
                }
                BottomSheetBehavior.STATE_COLLAPSED -> {
                    currentServiceViewModel.updateBottomSheetExpanded(serviceId, false)
                    syncRecoveryStore()
                }
            }
        }

        override fun onSlide(bottomSheet: View, slideOffset: Float) {
            updateFeesScrollBottomPadding()
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isServiceBound = true
            startingRide = false
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

        haveArrived = getString(R.string.service_have_arrived)
        startTrip = getString(R.string.service_start_trip)
        endTrip = getString(R.string.service_end_trip)

        requireActivity().onBackPressedDispatcher.addCallback(viewLifecycleOwner) {}

        bindViews()
        hydrateRecoveryState()
        applyFeeDetailsExpandedState(currentServiceViewModel.getCurrentServiceUiSnapshot()?.isFeeDetailsExpanded == true)
        loadFrozenFeesFromStorage()
        setupStaticListeners()
        observeCurrentService()
        observeRideFees()
        observeNextService()
        observeCurrentFeeData()
        observePresence()
        observeActionUiState()
        observeSelfServiceRejection()
    }

    override fun onResume() {
        super.onResume()
        checkAndBindToExistingService()
        shouldReconcileRestoredAction = true
        currentService?.let { service ->
            reconcileRecoveredActionIfNeeded(service, force = true)
            resumePendingActionSync(service)
        }
    }

    override fun onDestroyView() {
        clearOngoingTripRecoveryDialog(dismiss = true)
        if (::bottomSheetBehavior.isInitialized) {
            bottomSheetBehavior.removeBottomSheetCallback(bottomSheetCallback)
        }
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
        fareMultiplierChip = binding.fareMultiplierChip
        toggleFragmentButton = binding.toggleButton
        textTimePrice = binding.textTimeFare
        textCurrentTimePrice = binding.textPriceByTime
        textFareMultiplier = binding.textFareMultiplier
        textFareMultiplierDisplay = binding.textFareMultiplierDisplay
        textTotalFee = binding.textPrice
        btnStatus = binding.btnServiceStatus
        btnRetryAction = binding.btnRetryServiceAction
        btnCancelSelfService = binding.btnCancelSelfService
        imgBtnMaps = binding.serviceLayout.imgBtnMaps
        imgButtonWaze = binding.serviceLayout.imgBtnWaze
        chronometer = binding.chronometer
        scrollViewFees = binding.scrollViewFees
        feeDetailsHeader = binding.feeDetailsHeader
        feeDetailsContent = binding.feeDetailsContent
        collapsedHeader = binding.serviceLayout.collapsedHeader
        feedbackContainer = binding.serviceActionFeedback
        textActionStatus = binding.textServiceActionStatus
        expandIcon = binding.expandIcon
        homeFragment = HomeFragment()
        connectionServiceButton = binding.connectedServiceButton
        baseScrollViewBottomPadding = scrollViewFees.paddingBottom
    }

    private fun setupStaticListeners() {
        btnStatus.setOnClickListener {
            val service = currentService
            if (service == null) {
                // No RTDB-backed service yet: either idle, or a self-service trip still queued
                // for creation sync (add-driver-self-service task 4.4). Ending is the only action
                // reachable before that sync — see renderUnsyncedSelfServiceActionUi.
                if (mainViewModel.hasPendingSelfServiceTrip()) {
                    beginEndUnsyncedSelfServiceTrip()
                }
                return@setOnClickListener
            }

            when (baseActionText(service)) {
                haveArrived -> handleHaveArrived(service)
                startTrip -> beginStartTrip(service)
                else -> beginEndTrip(service)
            }
        }

        btnRetryAction.setOnClickListener {
            retryCurrentAction()
        }

        btnCancelSelfService.setOnClickListener {
            beginCancelSelfServiceTrip()
        }

        connectionServiceButton.setOnClickListener {
            showNextServiceDialog()
        }

        textFareMultiplier.setOnClickListener {
            showEditMultiplierDialog()
        }

        fareMultiplierChip.setOnClickListener {
            showEditMultiplierDialog()
        }

        setupFeeDetailsCollapse()
        setupBottomSheetBehavior()

        collapsedHeader.setOnClickListener {
            toggleBottomSheet()
        }

        toggleFragmentButton.setOnClickListener {
            toggleFragment()
        }
    }

    private fun observeCurrentService() {
        mainViewModel.currentService.observe(viewLifecycleOwner) { service ->
            if (service == null || (
                    ongoingTripRecoveryServiceId != null &&
                        ongoingTripRecoveryServiceId != service.id
                    )
            ) {
                clearOngoingTripRecoveryDialog(dismiss = true)
            }

            currentService = service

            if (service == null) {
                val pendingAction = currentServiceViewModel.getPendingActionSnapshot()
                if (pendingAction?.actionType == PendingServiceActionType.END) {
                    stopFeeService(clearPersistedState = false)
                    currentServiceViewModel.reset()
                    shouldReconcileRestoredAction = true
                    deferredInitialNullRecoveryClear = false
                    renderServiceActionUi(null)
                    syncRecoveryStore()
                    updateServicesFabVisibility()
                    return@observe
                }

                if (mainViewModel.hasPendingSelfServiceTrip()) {
                    // Self-service creation still syncing (add-driver-self-service design D5,
                    // steps 1-3): there is no RTDB-backed service yet. Metering keeps running
                    // against the local id until the driver ends it (task 4.4) or the create
                    // syncs and onSelfServiceTripCreated rebinds it; observeSelfServiceRejection
                    // handles the terminal-rejection case.
                    renderUnsyncedSelfServiceActionUi()
                    return@observe
                }

                if (!deferredInitialNullRecoveryClear && currentServiceViewModel.hasRestorableState()) {
                    deferredInitialNullRecoveryClear = true
                    renderServiceActionUi(null)
                    updateServicesFabVisibility()
                    return@observe
                }

                stopFeeService()
                currentServiceViewModel.reset()
                shouldReconcileRestoredAction = true
                deferredInitialNullRecoveryClear = false
                renderServiceActionUi(null)
                syncRecoveryStore()
                updateServicesFabVisibility()
                return@observe
            }

            deferredInitialNullRecoveryClear = false
            currentServiceViewModel.discardStaleStateForService(service.id)
            reconcileRecoveredActionIfNeeded(service)

            if (!service.isInProgress()) {
                currentServiceViewModel.onTripEndedObserved()
                stopFeeService(clearPersistedState = false)
                currentServiceViewModel.reset()
                mainViewModel.completeCurrentService()
                syncRecoveryStore()
                updateServicesFabVisibility()
                return@observe
            }

            bindServiceDetails(service)
            bindTripStage(service)
            renderServiceActionUi(service)
            restorePresentationStateForService(service)
            syncRecoveryStore()
            updateServicesFabVisibility()
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
                connectionServiceButton.visibility = View.VISIBLE
                refreshNextServiceDialog(service)
            } else {
                connectionServiceButton.visibility = View.INVISIBLE
                dismissNextServiceDialog()
            }
            updateServicesFabVisibility()
        }
    }

    private fun showNextServiceDialog() {
        val service = mainViewModel.nextService.value ?: return
        dismissNextServiceDialog()
        childFragmentManager.executePendingTransactions()
        ConnectionServiceDialog.newInstance(service)
            .show(childFragmentManager, ConnectionServiceDialog.TAG)
    }

    private fun dismissNextServiceDialog() {
        (childFragmentManager.findFragmentByTag(ConnectionServiceDialog.TAG) as? ConnectionServiceDialog)
            ?.dismissAllowingStateLoss()
    }

    private fun refreshNextServiceDialog(service: Service) {
        val currentDialog =
            childFragmentManager.findFragmentByTag(ConnectionServiceDialog.TAG) as? ConnectionServiceDialog
                ?: return

        currentDialog.dismissAllowingStateLoss()
        childFragmentManager.executePendingTransactions()
        ConnectionServiceDialog.newInstance(service)
            .show(childFragmentManager, ConnectionServiceDialog.TAG)
    }

    private fun observeCurrentFeeData() {
        mainViewModel.currentFeeData.observe(viewLifecycleOwner) { feeData ->
            totalRide = feeData.totalFee
            totalDistance = feeData.totalDistance
            currentRideElapsedSeconds = feeData.elapsedSeconds

            chronometer.base = SystemClock.elapsedRealtime() - (feeData.elapsedSeconds * 1000)

            textTotalFee.text = NumberHelper.toCurrency(feeData.totalFee)
            textCurrentTimePrice.text = NumberHelper.toCurrency(feeData.timeFee)
            textCurrentDistancePrice.text = NumberHelper.toCurrency(feeData.distanceFee)
            textCurrentDistance.text = getString(R.string.distance_km, feeData.totalDistance / 1000)
            textFareMultiplierDisplay.text = feeMultiplier.toString()
            updateServicesFabVisibility()
        }
    }

    private fun observePresence() {
        currentPresenceState = mainViewModel.presenceState.value
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.presenceState.collect { presence ->
                    val previousPresence = currentPresenceState
                    currentPresenceState = presence
                    val recoverySignalRestored =
                        (!previousPresence.hasNetwork && presence.hasNetwork) ||
                            (!previousPresence.actualOnline && presence.actualOnline)
                    if (recoverySignalRestored) {
                        shouldReconcileRestoredAction = true
                        currentService?.let { service ->
                            reconcileRecoveredActionIfNeeded(service, force = true)
                            resumePendingActionSync(service)
                        }
                    }
                    renderServiceActionUi(currentService)
                }
            }
        }
    }

    /**
     * A queued self-service creation was terminally rejected (add-driver-self-service design D5,
     * step 2 — e.g. `driver_already_in_service`, `driver_disabled`). No server record was ever
     * created, so there is nothing to settle: stop the local meter, surface the reason, and let
     * the driver retry from Home. `driver_not_connected` is not terminal and never reaches here —
     * it stays queued in [SelfServiceTripManager] and retries once presence self-heals.
     */
    private fun observeSelfServiceRejection() {
        mainViewModel.selfServiceRejection.observe(viewLifecycleOwner) { messageRes ->
            if (messageRes == null) return@observe
            stopFeeService(clearPersistedState = true)
            currentServiceViewModel.reset()
            Toast.makeText(requireContext(), messageRes, Toast.LENGTH_LONG).show()
            mainViewModel.consumeSelfServiceRejection()
        }
    }

    private fun observeActionUiState() {
        currentServiceViewModel.uiState.observe(viewLifecycleOwner) {
            renderServiceActionUi(currentService)
            syncRecoveryStore()
            updateServicesFabVisibility()
        }
    }

    private fun bindServiceDetails(service: Service) {
        textName.text = service.name
        textPhone.text = service.phone
        textAddress.text = service.start_loc.name
        textAddressPreview.text = service.start_loc.name
        textComment.text = service.comment

        val completedCount = service.client_completed_services_count
        if (completedCount != null && completedCount > 0) {
            binding.serviceLayout.badgeClientCompleted.text = completedCount.toString()
            binding.serviceLayout.badgeClientCompleted.visibility = View.VISIBLE
        } else {
            binding.serviceLayout.badgeClientCompleted.visibility = View.GONE
        }

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
        val override = currentServiceViewModel.serviceStageOverrideFor(service)
        // Self-service trips (add-driver-self-service) never go through an "arrived" stage —
        // they're born in_progress with start_trip_at already stamped — so the arrived_at gate
        // below never applies to them.
        val requiresArrivalStage = !service.isSelfService()

        when {
            requiresArrivalStage && service.metadata.arrived_at == null -> {
                btnStatus.text = haveArrived
                scrollViewFees.visibility = View.INVISIBLE
                stopFeeService(clearPersistedState = false)
            }
            override.treatAsEnded -> {
                btnStatus.text = endTrip
                scrollViewFees.visibility = View.INVISIBLE
                stopFeeService(clearPersistedState = false)
            }
            override.treatAsStarted || service.metadata.start_trip_at != null -> {
                btnStatus.text = endTrip
                if (service.metadata.start_trip_at != null) {
                    currentServiceViewModel.onTripStartedObserved()
                    maybeRestoreOngoingTrip(service)
                }
                scrollViewFees.visibility = View.VISIBLE
            }
            else -> {
                btnStatus.text = startTrip
                scrollViewFees.visibility = View.INVISIBLE
                stopFeeService(clearPersistedState = false)
            }
        }

        updateServicesFabVisibility()
        updateFeesScrollBottomPadding()
    }

    private fun maybeRestoreOngoingTrip(service: Service) {
        val serviceRunning = ServiceHelper.isServiceRunning(requireContext(), FeesService::class.java)
        if (serviceRunning) {
            return
        }

        ongoingTripRecoveryDialog?.let { dialog ->
            if (dialog.isShowing) {
                return
            }
            clearOngoingTripRecoveryDialog(dismiss = false)
        }

        val storedServiceId = RideRecoveryStore.getTrackedServiceId(sharedPreferences)
        if (RideRecoveryPolicy.shouldClearStaleRecovery(storedServiceId, service.id)) {
            RideRecoveryStore.clear(sharedPreferences)
            return
        }

        if (!RideRecoveryPolicy.shouldOfferRecovery(
                hasStartedTrip = service.metadata.start_trip_at != null,
                isServiceRunning = serviceRunning,
                isStartingFreshTransition = startingRide,
                storedServiceId = storedServiceId,
                currentServiceId = service.id
            )
        ) {
            return
        }

        ongoingTripRecoveryServiceId = service.id
        ongoingTripRecoveryDialog = showTripActionDialog(
            requireContext(),
            titleRes = R.string.service_start_trip,
            message = getText(R.string.ride_in_progress),
            primaryTextRes = R.string.yes,
            secondaryTextRes = R.string.no,
            iconRes = R.drawable.connected_service_24,
            primaryIconRes = R.drawable.assign_24,
            secondaryIconRes = R.drawable.cancel_24
        ) { confirmed ->
            if (!confirmed) {
                scrollViewFees.visibility = View.VISIBLE
                RideRecoveryStore.clear(sharedPreferences)
                totalRide = 0.0
                startServiceFee(service.id, service.start_loc.name)
                startingRide = true
                return@showTripActionDialog
            }

            startServiceFee(service.id, service.start_loc.name, true)
            scrollViewFees.visibility = View.VISIBLE
            startingRide = false
        }.apply {
            setOnDismissListener {
                if (ongoingTripRecoveryDialog === this) {
                    clearOngoingTripRecoveryDialog(dismiss = false)
                }
            }
        }
    }

    private fun handleHaveArrived(service: Service) {
        val now = Date().time / 1000
        service.metadata.arrived_at = now
        service.updateMetadata()
            .addOnSuccessListener {
                Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { exception ->
                btnStatus.text = haveArrived
                Log.e(TAG, exception.message ?: "Unable to mark arrived")
                Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
            }
    }

    private fun beginStartTrip(service: Service) {
        if (!canSubmitTripAction()) {
            currentServiceViewModel.clearStartTripRequest()
            currentServiceViewModel.showBlockedStartByConnection()
            syncRecoveryStore()
            return
        }

        val attemptId = currentServiceViewModel.newAttempt()
        currentServiceViewModel.showPreparingStart()
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
                currentServiceViewModel.showIdle()
                setBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED)
                showStartTripDialog(service)
            }
            CurrentServiceViewModel.StartRideFeesSource.FALLBACK -> {
                resolution.fees?.let {
                    applyRideFees(it)
                    mainViewModel.setRideFees(it)
                }
                currentServiceViewModel.showIdle()
                showToast(R.string.using_saved_pricing_snapshot)
                setBottomSheetState(BottomSheetBehavior.STATE_COLLAPSED)
                showStartTripDialog(service)
            }
            CurrentServiceViewModel.StartRideFeesSource.UNAVAILABLE -> {
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
            requireContext(),
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
            updateMultiplierViews(feeMultiplier)
            RideRecoveryStore.persistMultiplier(sharedPreferences, feeMultiplier)

            val request = CurrentServiceViewModel.StartTripRequest(
                serviceId = service.id,
                startedAt = Date().time / 1000,
                multiplier = feeMultiplier,
                origin = service.start_loc.name
            )
            currentServiceViewModel.rememberStartTripRequest(request, service)
            executeConfirmedStartTrip(request)
        }
    }

    private fun executeConfirmedStartTrip(request: CurrentServiceViewModel.StartTripRequest) {
        feeMultiplier = request.multiplier
        updateMultiplierViews(feeMultiplier)
        RideRecoveryStore.persistMultiplier(sharedPreferences, feeMultiplier)

        if (!canSubmitTripAction()) {
            currentServiceViewModel.showBlockedStartByConnection()
            syncRecoveryStore()
            return
        }

        val driverId = mainViewModel.driver.value?.id ?: return
        val attemptId = currentServiceViewModel.newAttempt()
        currentServiceViewModel.showStartingTrip()
        if (CurrentServiceViewModel.shouldQueueInsteadOfBlocking(currentPresenceState)) {
            val localResult = ServiceRepository.validateObservedServiceForStart(currentService, driverId)
            localResult
                .onSuccess { service ->
                    if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                        return@onSuccess
                    }

                    applyOptimisticStart(request)
                    submitQueuedStart(service, request)
                }
                .onFailure { exception ->
                    if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                        return@onFailure
                    }
                    handleStartValidationFailure(exception as Exception)
                }
            return
        }

        ServiceRepository.validateServiceForStart(request.serviceId, driverId)
            .addOnSuccessListener { validatedService ->
                if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                    return@addOnSuccessListener
                }

                ServiceRepository.submitTripStart(
                    validatedService,
                    StartTransitionPayload(startedAt = request.startedAt)
                ).addOnSuccessListener {
                    if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                        return@addOnSuccessListener
                    }

                    onTripStartWriteSucceeded(request)
                }.addOnFailureListener { exception ->
                    if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                        return@addOnFailureListener
                    }

                    Log.e(TAG, "Trip start write failed", exception)
                    currentServiceViewModel.showStartFailed(
                        messageRes = R.string.common_error,
                        canRetry = true
                    )
                }.withTimeout(START_WRITE_TIMEOUT_MS) {
                    if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                        return@withTimeout
                    }

                    Log.w(TAG, "Trip start write timed out attemptId=$attemptId")
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
                currentServiceViewModel.showStartFailed(
                    messageRes = R.string.error_timeout,
                    canRetry = true
                )
            }
    }

    private fun handleStartValidationFailure(exception: Exception) {
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
            if (currentServiceViewModel.hasPendingSyncAction()) {
                currentServiceViewModel.showStartFailed(
                    messageRes = messageRes,
                    canRetry = false
                )
            } else {
                currentServiceViewModel.clearStartTripRequest()
                currentServiceViewModel.showIdle()
                showToast(messageRes)
            }
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
        if (!canSubmitTripAction()) {
            currentServiceViewModel.showBlockedEndByConnection()
            syncRecoveryStore()
            return
        }

        currentServiceViewModel.showIdle()
        val message = getString(R.string.finalizing_message, NumberHelper.toCurrency(getTotalFee(), true))
        showTripActionDialog(
            requireContext(),
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

            val rawFee = NumberHelper.roundDouble(getTotalFee()).toInt()
            val tripFee: Int? = if (rawFee == 0) null else rawFee
            val request = CurrentServiceViewModel.EndTripRequest(
                serviceId = service.id,
                endedAt = now,
                route = if (isServiceBound) {
                    ServiceMetadata.serializeRoute(feesService.getPoints())
                } else {
                    ServiceMetadata.serializeRoute(arrayListOf())
                },
                tripDistance = NumberHelper.roundDouble(totalDistance).toInt(),
                tripFee = tripFee,
                multiplier = feeMultiplier
            )
            currentServiceViewModel.rememberEndTripRequest(request, service)
            executeConfirmedEndTrip(request)
        }
    }

    /**
     * Ends a self-service trip whose creation request hasn't synced yet (add-driver-self-service
     * task 4.4): there is no RTDB [Service] to drive the normal `beginEndTrip`/`EndTripRequest`
     * machinery, so this stops `FeesService` and hands the terminal data straight to
     * [MainViewModel.endUnsyncedSelfServiceTrip], which folds it into the still-queued creation —
     * exactly one request reaches the API (design D5, step 2). Metering, the completion timeout,
     * the finalize confirmation, and the zero-fee-is-null rule all mirror [beginEndTrip] so ending
     * a self trip behaves the same whether or not it has synced yet.
     */
    private fun beginEndUnsyncedSelfServiceTrip() {
        val trip = mainViewModel.getPendingSelfServiceTrip() ?: return
        if (trip.terminal != null) {
            // Already ended locally, only awaiting sync — nothing left for a tap to do.
            return
        }

        val now = Date().time / 1000
        if (now - trip.startedAt <= fees.timeoutToComplete) {
            Toast.makeText(
                requireContext(),
                getString(R.string.cannot_complete_service_yet, fees.timeoutToComplete / 60),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        val message = getString(R.string.finalizing_message, NumberHelper.toCurrency(getTotalFee(), true))
        showTripActionDialog(
            requireContext(),
            titleRes = R.string.finalize_service,
            message = StringHelper.getString(message),
            primaryTextRes = R.string.yes,
            secondaryTextRes = R.string.no,
            iconRes = R.drawable.ic_monetization_on_24,
            primaryIconRes = R.drawable.assign_24,
            secondaryIconRes = R.drawable.cancel_24
        ) { confirmed ->
            if (!confirmed) {
                return@showTripActionDialog
            }

            val rawFee = NumberHelper.roundDouble(getTotalFee()).toInt()
            val tripFee: Int? = if (rawFee == 0) null else rawFee
            val route = if (isServiceBound) {
                ServiceMetadata.serializeRoute(feesService.getPoints())
            } else {
                ServiceMetadata.serializeRoute(arrayListOf())
            }
            val tripDistance = NumberHelper.roundDouble(totalDistance).toInt()

            stopFeeService(clearPersistedState = false)
            mainViewModel.endUnsyncedSelfServiceTrip(
                endedAt = now,
                route = route,
                tripDistance = tripDistance,
                tripFee = tripFee
            )
            renderUnsyncedSelfServiceActionUi()
        }
    }

    /**
     * Windowed cancel for self-service trips only (add-driver-self-service task 4.5, design D6):
     * [btnCancelSelfService] is only ever visible while [updateSelfServiceCancelVisibility] finds
     * an eligible self-service trip within the cached-snapshot window, so reaching this function
     * already implies one of [currentService] (synced) or a pending provisional trip (unsynced)
     * exists. Confirmation mirrors the existing finalize-trip idiom (yes/no via
     * [showTripActionDialog]).
     */
    private fun beginCancelSelfServiceTrip() {
        if (cancelingSelfService) {
            return
        }

        val service = currentService
        val pendingTrip = if (service == null) mainViewModel.getPendingSelfServiceTrip() else null
        if (service == null && pendingTrip == null) {
            return
        }

        showTripActionDialog(
            requireContext(),
            titleRes = R.string.self_service_cancel_title,
            message = getText(R.string.self_service_cancel_message),
            primaryTextRes = R.string.yes,
            secondaryTextRes = R.string.no,
            iconRes = R.drawable.cancel_24,
            primaryIconRes = R.drawable.assign_24,
            secondaryIconRes = R.drawable.cancel_24
        ) { confirmed ->
            if (!confirmed) {
                return@showTripActionDialog
            }

            if (service != null) {
                executeCancelSyncedSelfServiceTrip(service)
            } else {
                executeCancelUnsyncedSelfServiceTrip()
            }
        }
    }

    /**
     * Unsynced path (design D5, steps 1/3): no RTDB [Service] exists yet, so cancellation is a
     * purely local transition — the queued creation now carries `status="canceled"` and folds
     * into the next deferred sync via [MainViewModel.cancelUnsyncedSelfServiceTrip].
     */
    private fun executeCancelUnsyncedSelfServiceTrip() {
        stopFeeService(clearPersistedState = false)
        mainViewModel.cancelUnsyncedSelfServiceTrip()
        renderUnsyncedSelfServiceActionUi()
        updateSelfServiceCancelVisibility()
    }

    /**
     * Synced path (design D6): the server is authoritative for the window, so nothing is applied
     * optimistically. On success this mirrors the "already terminated" handling in
     * [handleEndValidationFailure]. On a typed rejection (or a network failure) local state is
     * left untouched and the button visibility is simply recomputed — there is no phantom
     * canceled state to walk back because none was ever applied.
     */
    private fun executeCancelSyncedSelfServiceTrip(service: Service) {
        cancelingSelfService = true
        updateSelfServiceCancelVisibility()
        mainViewModel.cancelSelfServiceTrip(service.id) { result ->
            cancelingSelfService = false
            when (result) {
                MainViewModel.SelfServiceCancelResult.Success -> {
                    currentServiceViewModel.onTripEndedObserved()
                    stopFeeService(clearPersistedState = false)
                    currentServiceViewModel.reset()
                    mainViewModel.completeCurrentService()
                }
                is MainViewModel.SelfServiceCancelResult.Rejected -> {
                    Toast.makeText(requireContext(), result.messageRes, Toast.LENGTH_LONG).show()
                    updateSelfServiceCancelVisibility()
                }
                MainViewModel.SelfServiceCancelResult.Failed -> {
                    Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                    updateSelfServiceCancelVisibility()
                }
            }
        }
    }

    private fun executeConfirmedEndTrip(request: CurrentServiceViewModel.EndTripRequest) {
        if (!canSubmitTripAction()) {
            currentServiceViewModel.showBlockedEndByConnection()
            syncRecoveryStore()
            return
        }

        val driverId = mainViewModel.driver.value?.id ?: return
        val attemptId = currentServiceViewModel.newAttempt()
        currentServiceViewModel.showEndingTrip()
        if (CurrentServiceViewModel.shouldQueueInsteadOfBlocking(currentPresenceState)) {
            val localResult = ServiceRepository.validateObservedServiceForEnd(currentService, driverId)
            localResult
                .onSuccess { service ->
                    if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                        return@onSuccess
                    }

                    applyOptimisticEnd()
                    submitQueuedEnd(service, request)
                }
                .onFailure { exception ->
                    if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                        return@onFailure
                    }
                    handleEndValidationFailure(exception as Exception)
                }
            return
        }

        ServiceRepository.validateServiceForEnd(request.serviceId, driverId)
            .addOnSuccessListener { validatedService ->
                if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                    return@addOnSuccessListener
                }

                ServiceRepository.submitTripEnd(
                    validatedService,
                    EndTransitionPayload(
                        endedAt = request.endedAt,
                        route = request.route,
                        tripDistance = request.tripDistance,
                        tripFee = request.tripFee,
                        multiplier = request.multiplier
                    )
                ).addOnSuccessListener {
                    if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                        return@addOnSuccessListener
                    }

                    applyOptimisticEnd()
                }.addOnFailureListener { exception ->
                    if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                        return@addOnFailureListener
                    }

                    Log.e(TAG, "Trip end write failed", exception)
                    currentServiceViewModel.showEndFailed(
                        messageRes = R.string.common_error,
                        canRetry = true
                    )
                }.withTimeout(END_WRITE_TIMEOUT_MS) {
                    if (!currentServiceViewModel.isActiveAttempt(attemptId)) {
                        return@withTimeout
                    }

                    Log.w(TAG, "Trip end write timed out attemptId=$attemptId")
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
                currentServiceViewModel.showEndFailed(
                    messageRes = R.string.error_timeout,
                    canRetry = true
                )
            }
    }

    private fun handleEndValidationFailure(exception: Exception) {
        val message = exception.message.orEmpty()

        when {
            message.contains("already terminated", ignoreCase = true) -> {
                currentServiceViewModel.onTripEndedObserved()
                stopFeeService(clearPersistedState = false)
                currentServiceViewModel.reset()
                mainViewModel.completeCurrentService()
            }
            message.contains("does not exist", ignoreCase = true) -> {
                if (currentServiceViewModel.hasPendingSyncAction()) {
                    currentServiceViewModel.showEndFailed(
                        messageRes = R.string.service_not_exists,
                        canRetry = false
                    )
                } else {
                    currentServiceViewModel.clearEndTripRequest()
                    currentServiceViewModel.showIdle()
                    showToast(R.string.service_not_exists)
                }
            }
            message.contains("another driver", ignoreCase = true) ||
                message.contains("no longer in progress", ignoreCase = true) -> {
                if (currentServiceViewModel.hasPendingSyncAction()) {
                    currentServiceViewModel.showEndFailed(
                        messageRes = R.string.service_not_available,
                        canRetry = false
                    )
                } else {
                    currentServiceViewModel.clearEndTripRequest()
                    currentServiceViewModel.showIdle()
                    showToast(R.string.service_not_available)
                }
            }
            message.contains("has not started yet", ignoreCase = true) -> {
                if (currentServiceViewModel.hasPendingSyncAction()) {
                    currentServiceViewModel.showEndFailed(
                        messageRes = R.string.service_trip_not_started,
                        canRetry = false
                    )
                } else {
                    currentServiceViewModel.clearEndTripRequest()
                    currentServiceViewModel.showIdle()
                    showToast(R.string.service_trip_not_started)
                }
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
            CurrentServiceViewModel.ServiceActionUiState.StartSyncing,
            is CurrentServiceViewModel.ServiceActionUiState.StartFailed -> {
                currentServiceViewModel.getStartTripRequest()?.let {
                    executeConfirmedStartTrip(it)
                } ?: currentService?.let { service ->
                    beginStartTrip(service)
                }
            }
            CurrentServiceViewModel.ServiceActionUiState.BlockedEndByConnection,
            CurrentServiceViewModel.ServiceActionUiState.EndSyncing,
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

    /**
     * Minimal action surface for a self-service trip that has no RTDB-backed [Service] yet
     * (add-driver-self-service task 4.4): [renderServiceActionUi]'s state machine is keyed off a
     * [Service], which doesn't exist during this window, so this only makes ending the trip
     * reachable and reflects whether it was already ended locally and is awaiting sync. Richer
     * offline presentation (progress states, cancel window) belongs to tasks 4.5/4.6.
     */
    private fun renderUnsyncedSelfServiceActionUi() {
        feedbackContainer.isGone = true
        binding.serviceActionProgress.isGone = true
        textActionStatus.isGone = true
        btnRetryAction.isGone = true

        val trip = mainViewModel.getPendingSelfServiceTrip()
        btnStatus.text = endTrip
        btnStatus.isEnabled = trip != null && trip.terminal == null
        updateSelfServiceCancelVisibility()
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
                    retryEnabled = canSubmitTripAction()
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
            CurrentServiceViewModel.ServiceActionUiState.StartSyncing -> {
                // Optimistic UX: writes queue via Firebase persistence. Don't show any sync UI;
                // local listeners reflect the new status immediately. Keep the button hidden
                // while the service's status transitions to in_progress.
                feedbackContainer.isVisible = false
                btnStatus.isEnabled = false
            }
            is CurrentServiceViewModel.ServiceActionUiState.StartFailed -> {
                showFeedback(
                    statusText = failedStartMessage(state.messageRes),
                    showProgress = false,
                    retryTextRes = R.string.retry_start_trip,
                    retryEnabled = state.canRetry && canSubmitTripAction()
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
                    retryEnabled = canSubmitTripAction()
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
            CurrentServiceViewModel.ServiceActionUiState.EndSyncing -> {
                // Optimistic UX: writes queue via Firebase persistence; nothing to show.
                feedbackContainer.isVisible = false
                btnStatus.isEnabled = false
            }
            is CurrentServiceViewModel.ServiceActionUiState.EndFailed -> {
                showFeedback(
                    statusText = failedEndMessage(state.messageRes),
                    showProgress = false,
                    retryTextRes = R.string.retry_end_trip,
                    retryEnabled = state.canRetry && canSubmitTripAction()
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
            btnRetryAction.isClickable = retryEnabled
            btnRetryAction.alpha = if (retryEnabled) 1f else 0.6f
            btnRetryAction.setText(retryTextRes)
        } else {
            btnRetryAction.isGone = true
            btnRetryAction.alpha = 1f
        }
    }

    private fun blockedStartMessage(): String {
        return if (canSubmitTripAction()) {
            getString(R.string.connection_restored_retry_start)
        } else {
            getString(R.string.trip_action_recovering_queueable)
        }
    }

    private fun blockedEndMessage(): String {
        return if (canSubmitTripAction()) {
            getString(R.string.connection_restored_retry_end)
        } else {
            getString(R.string.trip_action_recovering_queueable)
        }
    }

    private fun failedStartMessage(@StringRes messageRes: Int): String {
        return if (canSubmitTripAction()) {
            getString(R.string.connection_restored_retry_start) + "\n" +
                getString(messageRes) + "\n" +
                getString(R.string.start_trip_not_started_yet)
        } else {
            getString(messageRes) + "\n" + getString(R.string.start_trip_not_started_yet)
        }
    }

    private fun failedEndMessage(@StringRes messageRes: Int): String {
        return if (canSubmitTripAction()) {
            getString(R.string.connection_restored_retry_end) + "\n" +
                getString(messageRes) + "\n" +
                getString(R.string.end_trip_not_ended_yet)
        } else {
            getString(messageRes) + "\n" + getString(R.string.end_trip_not_ended_yet)
        }
    }

    private fun baseActionText(service: Service): String {
        val override = currentServiceViewModel.serviceStageOverrideFor(service)
        return when {
            override.treatAsEnded -> endTrip
            override.treatAsStarted -> endTrip
            !service.isSelfService() && service.metadata.arrived_at == null -> haveArrived
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

    private fun hydrateRecoveryState() {
        currentServiceViewModel.restoreFromStoreIfNeeded(
            pendingActionSnapshot = RideRecoveryStore.getPendingServiceActionSnapshot(sharedPreferences),
            currentServiceUiSnapshot = RideRecoveryStore.getCurrentServiceUiSnapshot(sharedPreferences),
            bottomSheetSnapshot = RideRecoveryStore.getBottomSheetPresentationSnapshot(sharedPreferences)
        )
    }

    private fun reconcileRecoveredActionIfNeeded(service: Service, force: Boolean = false) {
        if (!force && !shouldReconcileRestoredAction) {
            return
        }

        currentServiceViewModel.reconcileRestoredPendingAction(service, currentPresenceState)
        shouldReconcileRestoredAction = false
    }

    private fun restorePresentationStateForService(service: Service) {
        val feeDetailsSnapshot = currentServiceViewModel.getCurrentServiceUiSnapshot()
        applyFeeDetailsExpandedState(
            feeDetailsSnapshot?.takeIf { it.serviceId == service.id }?.isFeeDetailsExpanded == true
        )

        val expanded = currentServiceViewModel.getBottomSheetPresentationSnapshot()
            ?.takeIf { it.serviceId == service.id }
            ?.isExpanded
            ?: (baseActionText(service) != endTrip)

        setBottomSheetState(
            if (expanded) {
                BottomSheetBehavior.STATE_EXPANDED
            } else {
                BottomSheetBehavior.STATE_COLLAPSED
            }
        )
    }

    private fun setupBottomSheetBehavior() {
        val serviceLayoutView = binding.serviceLayout.root
        bottomSheetBehavior =                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       BottomSheetBehavior.from(serviceLayoutView).apply {
            isHideable = false
            skipCollapsed = false
            addBottomSheetCallback(bottomSheetCallback)
        }

        binding.coordinatorLayout.doOnLayout {
            updateFeesScrollBottomPadding()
        }
        scrollViewFees.doOnLayout {
            updateFeesScrollBottomPadding()
        }
        serviceLayoutView.doOnLayout {
            bottomSheetBehavior.peekHeight = collapsedHeader.bottom + serviceLayoutView.paddingBottom
            updateFeesScrollBottomPadding()
            currentService?.let { service ->
                restorePresentationStateForService(service)
            }
        }
    }

    private fun setupFeeDetailsCollapse() {
        feeDetailsHeader.setOnClickListener {
            toggleFeeDetails()
        }
    }

    private fun toggleFeeDetails() {
        val serviceId = currentService?.id
        applyFeeDetailsExpandedState(!isExpanded, animate = true)
        if (serviceId != null) {
            currentServiceViewModel.updateFeeDetailsExpanded(serviceId, isExpanded)
            syncRecoveryStore()
        }
    }

    private fun applyFeeDetailsExpandedState(expanded: Boolean, animate: Boolean = false) {
        isExpanded = expanded
        feeDetailsContent.visibility = if (expanded) View.VISIBLE else View.GONE
        if (animate) {
            expandIcon.animate().rotation(if (expanded) 180f else 0f).setDuration(200).start()
        } else {
            expandIcon.rotation = if (expanded) 180f else 0f
        }
    }

    private fun toggleBottomSheet() {
        if (!::bottomSheetBehavior.isInitialized) {
            return
        }

        setBottomSheetState(
            if (bottomSheetBehavior.state == BottomSheetBehavior.STATE_EXPANDED) {
                BottomSheetBehavior.STATE_COLLAPSED
            } else {
                BottomSheetBehavior.STATE_EXPANDED
            }
        )
    }

    private fun setBottomSheetState(state: Int) {
        if (!::bottomSheetBehavior.isInitialized) {
            return
        }

        bottomSheetBehavior.state = state
        updateFeesScrollBottomPadding()
        val serviceId = currentService?.id ?: return
        currentServiceViewModel.updateBottomSheetExpanded(
            serviceId = serviceId,
            isExpanded = state == BottomSheetBehavior.STATE_EXPANDED
        )
        syncRecoveryStore()
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
        startServiceFee(currentService?.id.orEmpty(), origin, resumeRide)
    }

    private fun startServiceFee(serviceId: String, origin: String, resumeRide: Boolean = false) {
        if (serviceId.isBlank()) {
            return
        }

        RideRecoveryStore.clearIfStale(sharedPreferences, serviceId)
        RideRecoveryStore.attachToService(sharedPreferences, serviceId)

        val intentFee = Intent(requireContext(), FeesService::class.java)
        intentFee.putExtra(FeesService.SERVICE_ID, serviceId)
        intentFee.putExtra(ORIGIN, origin)
        intentFee.putExtra(FEE_MULTIPLIER, feeMultiplier)

        persistRideFeesSnapshot(
            RideRecoveryPolicy.selectRideFeesSnapshotForPersistence(
                candidate = fees,
                stored = getStoredRideFeesSnapshot()
            )
        )

        if (resumeRide && RideRecoveryStore.hasRecoverableSession(sharedPreferences, serviceId)) {
            intentFee.putExtra(RESUME_RIDE, true)
            val savedMultiplier = sharedPreferences.getString(Constants.MULTIPLIER, "1.0")?.toDoubleOrNull() ?: 1.0
            feeMultiplier = savedMultiplier
            updateMultiplierViews(feeMultiplier)
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

    private fun canSubmitTripAction(): Boolean {
        return CurrentServiceViewModel.isReadyForServiceAction(currentPresenceState)
    }

    private fun onTripStartWriteSucceeded(request: CurrentServiceViewModel.StartTripRequest) {
        if (CurrentServiceViewModel.hasObservedStartAck(currentService, request)) {
            applyConfirmedStart(request)
            return
        }

        applyOptimisticStart(request)
    }

    private fun applyConfirmedStart(request: CurrentServiceViewModel.StartTripRequest) {
        startingRide = true
        startServiceFee(request.serviceId, request.origin)
        currentServiceViewModel.onTripStartedObserved()
        syncRecoveryStore()
    }

    private fun applyOptimisticStart(request: CurrentServiceViewModel.StartTripRequest) {
        startingRide = true
        startServiceFee(request.serviceId, request.origin)
        currentServiceViewModel.showStartSyncing()
        syncRecoveryStore()
    }

    private fun applyOptimisticEnd() {
        stopFeeService(clearPersistedState = false)
        currentServiceViewModel.showEndSyncing()
        syncRecoveryStore()
    }

    private fun submitQueuedStart(
        service: Service,
        request: CurrentServiceViewModel.StartTripRequest
    ) {
        ServiceRepository.submitTripStart(
            service,
            StartTransitionPayload(startedAt = request.startedAt)
        ).addOnFailureListener { exception ->
            Log.e(TAG, "Queued trip start write failed", exception)
        }
    }

    private fun submitQueuedEnd(
        service: Service,
        request: CurrentServiceViewModel.EndTripRequest
    ) {
        ServiceRepository.submitTripEnd(
            service,
            EndTransitionPayload(
                endedAt = request.endedAt,
                route = request.route,
                tripDistance = request.tripDistance,
                tripFee = request.tripFee,
                multiplier = request.multiplier
            )
        ).addOnFailureListener { exception ->
            Log.e(TAG, "Queued trip end write failed", exception)
        }
    }

    private fun resumePendingActionSync(service: Service) {
        val snapshot = currentServiceViewModel.getPendingActionSnapshot() ?: return
        if (!snapshot.optimisticApplied) {
            return
        }

        val driverId = mainViewModel.driver.value?.id ?: return
        currentServiceViewModel.bumpPendingActionAttempt()
        when (snapshot.actionType) {
            PendingServiceActionType.START -> {
                val request = snapshot.startRequest ?: return
                ServiceRepository.validateObservedServiceForStart(service, driverId)
                    .onSuccess { validatedService ->
                        submitQueuedStart(validatedService, request)
                    }
                    .onFailure { exception ->
                        handleStartValidationFailure(exception as Exception)
                    }
            }
            PendingServiceActionType.END -> {
                val request = snapshot.endRequest ?: return
                ServiceRepository.validateObservedServiceForEnd(service, driverId)
                    .onSuccess { validatedService ->
                        submitQueuedEnd(validatedService, request)
                    }
                    .onFailure { exception ->
                        handleEndValidationFailure(exception as Exception)
                    }
            }
        }
    }

    private fun applyRideFees(rideFees: RideFees) {
        fees = rideFees
        feeMultiplier = rideFees.feeMultiplier
        textPriceBase.text = NumberHelper.toCurrency(rideFees.feesBase)
        textPriceMinFee.text = NumberHelper.toCurrency(rideFees.priceMinFee)
        textPriceAddFee.text = NumberHelper.toCurrency(rideFees.priceAddFee)
        textDistancePrice.text = NumberHelper.toCurrency(rideFees.priceKm)
        textTimePrice.text = NumberHelper.toCurrency(rideFees.priceMin)
        updateMultiplierViews(feeMultiplier)
        if (isServiceBound) {
            feesService.setMultiplier(feeMultiplier)
        }
    }

    private fun updateMultiplierViews(multiplier: Double) {
        val displayValue = multiplier.toString()
        textFareMultiplier.text = displayValue
        textFareMultiplierDisplay.text = displayValue
    }

    private fun updateFeesScrollBottomPadding() {
        val currentBinding = _binding ?: return
        if (scrollViewFees.visibility != View.VISIBLE) {
            scrollViewFees.updatePadding(bottom = baseScrollViewBottomPadding)
            return
        }

        val serviceLayoutView = currentBinding.serviceLayout.root
        if (scrollViewFees.height == 0 || serviceLayoutView.height == 0) {
            scrollViewFees.doOnLayout {
                updateFeesScrollBottomPadding()
            }
            return
        }

        val obscuredHeight = (scrollViewFees.bottom - serviceLayoutView.top).coerceAtLeast(0)
        val extraSpacing = (24 * resources.displayMetrics.density).toInt()
        val targetBottomPadding = baseScrollViewBottomPadding + obscuredHeight + extraSpacing
        if (scrollViewFees.paddingBottom != targetBottomPadding) {
            scrollViewFees.updatePadding(bottom = targetBottomPadding)
        }
    }

    private fun persistRideFeesSnapshot(rideFees: RideFees?) {
        rideFees ?: return
        RideRecoveryStore.persistRideFeesSnapshot(sharedPreferences, rideFees)
    }

    private fun syncRecoveryStore() {
        currentServiceViewModel.getPendingActionSnapshot()?.let {
            RideRecoveryStore.persistPendingServiceActionSnapshot(sharedPreferences, it)
        } ?: RideRecoveryStore.clearPendingServiceActionSnapshot(sharedPreferences)

        currentServiceViewModel.getCurrentServiceUiSnapshot()?.let {
            RideRecoveryStore.persistCurrentServiceUiSnapshot(sharedPreferences, it)
        } ?: RideRecoveryStore.clearCurrentServiceUiSnapshot(sharedPreferences)

        currentServiceViewModel.getBottomSheetPresentationSnapshot()?.let {
            RideRecoveryStore.persistBottomSheetPresentationSnapshot(sharedPreferences, it)
        } ?: RideRecoveryStore.clearBottomSheetPresentationSnapshot(sharedPreferences)
    }

    private fun getStoredRideFeesSnapshot(): RideFees? {
        return RideRecoveryStore.getRideFeesSnapshot(sharedPreferences)
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
        val dialogLayout: View = LayoutInflater.from(activity).inflate(R.layout.multiplier_feed, null)
        val editFeeMultiplier = dialogLayout.findViewById<EditText>(R.id.dialog_fee_multiplier)

        val currentMultiplier = if (isServiceBound) {
            feesService.getMultiplier()
        } else {
            feeMultiplier
        }

        editFeeMultiplier.text = Editable.Factory.getInstance().newEditable(currentMultiplier.toString())

        showTripActionDialog(
            requireContext(),
            titleRes = R.string.edit_multiplier,
            message = getText(R.string.start_ride_message),
            primaryTextRes = R.string.save,
            secondaryTextRes = R.string.cancel,
            iconRes = R.drawable.add_24,
            primaryIconRes = R.drawable.assign_24,
            secondaryIconRes = R.drawable.cancel_24,
            customBody = dialogLayout
        ) { confirmed ->
            if (!confirmed) {
                return@showTripActionDialog
            }

            val inputValue = editFeeMultiplier.text.toString().toDoubleOrNull() ?: 1.0
            val newMultiplier = if (inputValue < 1.0) {
                Toast.makeText(requireContext(), R.string.multiplier_minimum_value, Toast.LENGTH_SHORT).show()
                1.0
            } else {
                inputValue
            }

            feeMultiplier = newMultiplier
            updateMultiplierViews(newMultiplier)

            if (isServiceBound) {
                feesService.setMultiplier(newMultiplier)
            }

            RideRecoveryStore.persistMultiplier(sharedPreferences, newMultiplier)

            Toast.makeText(
                requireContext(),
                getString(R.string.multiplier_updated, newMultiplier),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun clearOngoingTripRecoveryDialog(dismiss: Boolean) {
        val dialog = ongoingTripRecoveryDialog
        ongoingTripRecoveryDialog = null
        ongoingTripRecoveryServiceId = null

        if (dismiss && dialog?.isShowing == true) {
            dialog.dismiss()
        }
    }

    private fun stopFeeService(clearPersistedState: Boolean = true) {
        if (isServiceBound) {
            requireContext().unbindService(serviceConnection)
            isServiceBound = false
        }

        val intentFee = Intent(requireContext(), FeesService::class.java)
        requireContext().stopService(intentFee)

        chronometer.stop()
        chronometer.base = SystemClock.elapsedRealtime()

        if (clearPersistedState) {
            RideRecoveryStore.clear(sharedPreferences)
        } else {
            RideRecoveryStore.clearRideSession(sharedPreferences)
        }

        mainViewModel.changeConnectTripService(false)
        currentRideElapsedSeconds = 0L
        updateServicesFabVisibility()
    }

    private fun updateServicesFabVisibility() {
        val service = currentService
        val shouldShow = CurrentServiceViewModel.shouldShowServicesFab(
            currentService = service,
            hasNextService = mainViewModel.nextService.value != null,
            timeoutToConnectionSeconds = fees.timeoutToConnection,
            rideElapsedSeconds = currentRideElapsedSeconds,
            nowEpochSeconds = Date().time / 1000,
            serviceStageOverride = service?.let(currentServiceViewModel::serviceStageOverrideFor)
                ?: CurrentServiceViewModel.ServiceStageOverride()
        )

        toggleFragmentButton.visibility = if (shouldShow) View.VISIBLE else View.GONE
        updateSelfServiceCancelVisibility()
    }

    /**
     * Windowed cancel action for self-service trips only (add-driver-self-service task 4.5,
     * design D6). Piggybacks on the same triggers as [updateServicesFabVisibility] — including
     * the per-second `FeesService` tick relayed through [observeCurrentFeeData] — so the button
     * disappears on its own once `now - start_trip_at` exceeds `fees.selfServiceCancelWindow`,
     * without a dedicated timer. Covers both a synced self-service [currentService] and a
     * still-unsynced provisional trip (task 4.4/[renderUnsyncedSelfServiceActionUi]); normal
     * assigned services never satisfy [Service.isSelfService] and never show the button.
     */
    private fun updateSelfServiceCancelVisibility() {
        if (!::btnCancelSelfService.isInitialized) {
            return
        }

        val service = currentService
        val pendingTrip = if (service == null) mainViewModel.getPendingSelfServiceTrip() else null
        val override = service?.let(currentServiceViewModel::serviceStageOverrideFor)
            ?: CurrentServiceViewModel.ServiceStageOverride()

        val isSelfService = service?.isSelfService() == true || pendingTrip != null
        val isCancelable = when {
            service != null ->
                service.isInProgress() &&
                    !override.treatAsEnded &&
                    currentServiceViewModel.uiState.value == CurrentServiceViewModel.ServiceActionUiState.Idle
            pendingTrip != null -> pendingTrip.terminal == null
            else -> false
        }
        val startedAt = service?.metadata?.start_trip_at ?: pendingTrip?.startedAt

        val withinWindow = CurrentServiceViewModel.shouldShowSelfServiceCancel(
            isSelfService = isSelfService,
            isCancelable = isCancelable,
            startedAtEpochSeconds = startedAt,
            nowEpochSeconds = Date().time / 1000,
            cancelWindowSeconds = fees.selfServiceCancelWindow
        )

        btnCancelSelfService.isVisible = withinWindow
        btnCancelSelfService.isEnabled = withinWindow && !cancelingSelfService
    }

    private fun getTotalFee(): Double {
        if (isServiceBound) {
            return feesService.getTotalFee()
        }
        val storedFees = getStoredRideFeesSnapshot()
        val minFee = storedFees?.priceMinFee ?: 0.0
        return if (minFee > 0.0) {
            maxOf(totalRide, NumberHelper.roundToMultipleOf500(minFee * feeMultiplier))
        } else {
            totalRide
        }
    }

    private fun showToast(@StringRes messageRes: Int) {
        Toast.makeText(requireContext(), messageRes, Toast.LENGTH_SHORT).show()
    }
}
