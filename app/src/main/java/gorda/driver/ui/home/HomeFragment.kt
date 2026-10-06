package gorda.driver.ui.home

import android.app.AlertDialog
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.SharedPreferences
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import gorda.driver.R
import gorda.driver.background.FeesService
import gorda.driver.background.FeesService.Companion.FEE_MULTIPLIER
import gorda.driver.background.FeesService.Companion.ORIGIN
import gorda.driver.databinding.FragmentHomeBinding
import gorda.driver.helpers.withTimeout
import gorda.driver.interfaces.LocType
import gorda.driver.interfaces.RideFees
import gorda.driver.models.Service
import gorda.driver.repositories.SettingsRepository
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.driver.DriverUpdates
import gorda.driver.ui.service.ServiceAdapter
import gorda.driver.ui.service.current.CurrentServiceViewModel
import gorda.driver.ui.service.dataclasses.LocationUpdates
import gorda.driver.ui.service.dataclasses.ServiceUpdates
import gorda.driver.utils.Constants
import gorda.driver.utils.RideRecoveryStore
import gorda.driver.utils.StringHelper
import gorda.driver.utils.Utils
import gorda.driver.utils.showTripActionDialog
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID

class HomeFragment : Fragment() {

    companion object {
        private const val TAG = "HomeFragment"
        private const val RIDE_FEES_TIMEOUT_MS = 8_000L
    }

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val mainViewModel: MainViewModel by activityViewModels()
    private val homeViewModel: HomeViewModel by viewModels()
    private var location: Location? = null
    private lateinit var recyclerView: RecyclerView
    private lateinit var startOwnTripButton: FloatingActionButton
    private lateinit var alertReceiver: BroadcastReceiver
    private lateinit var preferences: SharedPreferences
    private var alertsHandler: Handler? = null
    private var alertsRunnable: Runnable? = null
    private var wasOnline: Boolean = false
    private var applyConfirmDialog: AlertDialog? = null
    private var selfServiceStartDialog: AlertDialog? = null
    private var activeTripBlockedDialog: AlertDialog? = null
    private var inMemoryRideFees: RideFees = RideFees()
    private var selfServiceFeeMultiplier: Double = 1.0
    private var selfServiceRideFeesAttemptId: Long = 0L

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textView: TextView = binding.textHome
        this.recyclerView = binding.listServices
        this.startOwnTripButton = binding.startOwnTripButton
        this.startOwnTripButton.setOnClickListener {
            handleStartOwnTripTapped()
        }
        val showMapFromService: (location: LocType) -> Unit = { location ->
            mainViewModel.setServiceUpdateStartLocation(location)
            findNavController().navigate(R.id.nav_map)
        }
        val apply: (service: Service, location: LocType) -> Unit = { service, location ->
            showApplyConfirmation(service, location)
        }
        val serviceAdapter = ServiceAdapter(requireContext(), showMapFromService, apply)
        this.recyclerView.adapter = serviceAdapter
        homeViewModel.text.observe(viewLifecycleOwner) {
            textView.text = getString(it)
        }

        homeViewModel.serviceList.observe(viewLifecycleOwner) { updates ->
            when (updates) {
                is ServiceUpdates.SetList -> {
                    val services = updates.services.toMutableList()
                    location?.let { loc ->
                        services.sortWith(compareBy { service ->
                            val location = Location("last")
                            location.latitude = service.start_loc.lat
                            location.longitude = service.start_loc.lng

                            loc.distanceTo(location).toInt()
                        })
                    }
                    serviceAdapter.submitList(services)
                }
                is ServiceUpdates.StopListen -> {
                    serviceAdapter.submitList(emptyList())
                }
                else -> {}
            }
        }

        mainViewModel.lastLocation.observe(viewLifecycleOwner) {
            when (it) {
                is LocationUpdates.LastLocation -> {
                    location = it.location
                    serviceAdapter.lastLocation = it.location
                    serviceAdapter.notifyDataSetChanged()
                }
            }
        }

        mainViewModel.rideFees.observe(viewLifecycleOwner) { rideFees ->
            inMemoryRideFees = rideFees
        }

        // "Start own trip" entry (add-driver-self-service; fix-driver-fee-service-zombie-ticker
        // D6): visible only while connected, availability.canGoOnline is true, and there is no
        // active trip; react to driver, presence, current-service, and self-service-rejection
        // changes, since a terminal rejection clears the pending trip on its own.
        mainViewModel.driver.observe(viewLifecycleOwner) {
            refreshSelfServiceEligibility()
        }

        mainViewModel.currentService.observe(viewLifecycleOwner) {
            refreshSelfServiceEligibility()
        }

        mainViewModel.selfServiceRejection.observe(viewLifecycleOwner) {
            refreshSelfServiceEligibility()
        }

        homeViewModel.selfServiceEntryVisible.observe(viewLifecycleOwner) { visible ->
            startOwnTripButton.visibility = if (visible) View.VISIBLE else View.GONE
        }

        // Observe StateFlow for driver status
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.driverStatus.collect { driverUpdates ->
                    when (driverUpdates) {
                        is DriverUpdates.IsConnected -> {
                            if (driverUpdates.connected) {
                                recyclerView.visibility = View.VISIBLE
                                homeViewModel.startListenServices()
                            } else {
                                homeViewModel.stopListenServices()
                                recyclerView.visibility = View.INVISIBLE
                            }
                        }
                        else -> {}
                    }
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                mainViewModel.presenceState.collect { presence ->
                    if (presence.actualOnline && !wasOnline) {
                        homeViewModel.restartListenServices()
                    }
                    wasOnline = presence.actualOnline
                    refreshSelfServiceEligibility()
                }
            }
        }

        return root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
        alertReceiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                showAlerts()
            }
        }
        // Start cron to call showAlerts every minute
        alertsHandler = Handler(Looper.getMainLooper())
        alertsRunnable = object : Runnable {
            override fun run() {
                showAlerts()
                alertsHandler?.postDelayed(this, 60_000)
            }
        }
        alertsHandler?.post(alertsRunnable!!)
    }

    override fun onResume() {
        super.onResume()
        if (Utils.isNewerVersion(Build.VERSION_CODES.O)) {
            ContextCompat.registerReceiver(
                requireContext(),
                alertReceiver,
                IntentFilter(Constants.ALERT_ACTION),
                ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } else {
            LocalBroadcastManager.getInstance(requireContext()).registerReceiver(
                alertReceiver,
                IntentFilter(Constants.ALERT_ACTION)
            )
        }
        showAlerts()
    }

    override fun onPause() {
        super.onPause()
        requireContext().unregisterReceiver(alertReceiver)
    }

    private fun showAlerts() {
        val notificationsJson = preferences.getString(Constants.ALERT_ACTION, "[]")
        val notificationsArray = try {
            org.json.JSONArray(notificationsJson)
        } catch (e: Exception) {
            org.json.JSONArray()
        }
        val now = System.currentTimeMillis()
        val validNotifications = org.json.JSONArray()
        val container = view?.findViewById<LinearLayout>(R.id.alerts_container)
        container?.removeAllViews()
        // Show all valid notifications, most recent first
        val validList = mutableListOf<org.json.JSONObject>()
        for (i in 0 until notificationsArray.length()) {
            val obj = notificationsArray.optJSONObject(i)
            val duration = obj?.optString("duration")?.toLongOrNull() ?: 15L
            val timestamp = obj?.optLong("timestamp") ?: System.currentTimeMillis()
            val minutesPassed = (now - timestamp) / 60000L
            if (minutesPassed <= duration) {
                validList.add(obj)
            }
        }
        // Sort by timestamp descending (most recent first)
        validList.sortByDescending { it.optLong("timestamp") }
        for (obj in validList) {
            val title = obj.optString("title", getString(R.string.app_name))
            val body = obj.optString("body", "")
            val alertView = layoutInflater.inflate(R.layout.custom_alert, container, false)
            alertView.findViewById<TextView>(R.id.alert_title).text = title
            alertView.findViewById<TextView>(R.id.alert_body).text = body
            val closeBtn = alertView.findViewById<View>(R.id.alert_close)
            closeBtn.setOnClickListener {
                removeNotificationFromPrefs(obj)
                container?.removeView(alertView)
            }
            container?.addView(alertView)
            validNotifications.put(obj)
        }
        preferences.edit(true) { putString(Constants.ALERT_ACTION, validNotifications.toString()) }
    }

    private fun removeNotificationFromPrefs(notification: org.json.JSONObject) {
        val notificationsJson = preferences.getString(Constants.ALERT_ACTION, "[]")
        val notificationsArray = try {
            org.json.JSONArray(notificationsJson)
        } catch (e: Exception) {
            org.json.JSONArray()
        }
        val newArray = org.json.JSONArray()
        for (i in 0 until notificationsArray.length()) {
            val obj = notificationsArray.optJSONObject(i)
            if (obj != null && obj.toString() != notification.toString()) {
                newArray.put(obj)
            }
        }
        preferences.edit(true) { putString(Constants.ALERT_ACTION, newArray.toString()) }
    }

    /**
     * Recomputes self-service entry visibility (design D6) from the latest snapshot of every
     * signal it depends on. Called from each observer that can change one of those signals.
     */
    private fun refreshSelfServiceEligibility() {
        homeViewModel.updateSelfServiceEligibility(
            connected = mainViewModel.presenceState.value.actualOnline,
            canGoOnline = mainViewModel.driver.value?.canGoOnline() ?: false,
            hasActiveTrip = mainViewModel.currentService.value != null || mainViewModel.hasPendingSelfServiceTrip()
        )
    }

    /**
     * Start-admission guard (design D6): blocks a self-service start while another trip is
     * active, without touching `RideRecoveryStore` or `FeesService`. Returns true when blocked.
     */
    private fun blockSelfServiceStartIfActiveTrip(): Boolean {
        val resolution = HomeViewModel.resolveSelfServiceStart(
            hasCurrentService = mainViewModel.currentService.value != null,
            hasPendingSelfServiceTrip = mainViewModel.hasPendingSelfServiceTrip()
        )
        if (resolution == HomeViewModel.SelfServiceStartResolution.ALLOW) {
            return false
        }
        showActiveTripBlockedDialog()
        return true
    }

    private fun showActiveTripBlockedDialog() {
        if (activeTripBlockedDialog?.isShowing == true) {
            return
        }
        activeTripBlockedDialog = showTripActionDialog(
            requireContext(),
            titleRes = R.string.self_service_blocked_title,
            message = getText(R.string.self_service_blocked_message),
            primaryTextRes = R.string.self_service_blocked_action,
            secondaryTextRes = R.string.cancel,
            iconRes = R.drawable.connected_service_24,
            primaryIconRes = R.drawable.ic_location_24,
            secondaryIconRes = R.drawable.cancel_24
        ) { confirmed ->
            if (confirmed && isAdded) {
                findNavController().navigate(R.id.nav_current_service)
            }
        }.apply {
            setOnDismissListener {
                if (activeTripBlockedDialog === this) {
                    activeTripBlockedDialog = null
                }
            }
        }
    }

    private fun navigateToApply(service: Service, location: LocType) {
        mainViewModel.setServiceUpdateApply(service)
        mainViewModel.setServiceUpdateStartLocation(location)
        val bundle = bundleOf("service" to service)
        findNavController().navigate(R.id.nav_apply, bundle)
    }

    private fun showApplyConfirmation(service: Service, location: LocType) {
        if (applyConfirmDialog?.isShowing == true) {
            return
        }
        val lines = mutableListOf(
            getString(R.string.apply_confirm_message),
            getString(R.string.apply_confirm_from, service.start_loc.name)
        )
        service.end_loc?.name?.takeIf { it.isNotBlank() }?.let { destinationName ->
            lines.add(getString(R.string.apply_confirm_to, destinationName))
        }
        val message = StringHelper.getString(lines.joinToString("<br>"))
        applyConfirmDialog = showTripActionDialog(
            requireContext(),
            titleRes = R.string.apply_confirm_title,
            message = message,
            primaryTextRes = R.string.apply_confirm_continue,
            secondaryTextRes = R.string.cancel,
            iconRes = R.drawable.ic_location_24,
            primaryIconRes = R.drawable.assign_24,
            secondaryIconRes = R.drawable.cancel_24
        ) { confirmed ->
            if (confirmed && isAdded && view != null) {
                navigateToApply(service, location)
            }
        }.apply {
            setOnDismissListener {
                if (applyConfirmDialog === this) {
                    applyConfirmDialog = null
                }
            }
        }
    }

    private fun handleStartOwnTripTapped() {
        if (blockSelfServiceStartIfActiveTrip()) {
            return
        }
        if (homeViewModel.selfServiceEntryVisible.value != true) {
            return
        }
        if (selfServiceStartDialog?.isShowing == true) {
            return
        }

        selfServiceRideFeesAttemptId += 1
        val attemptId = selfServiceRideFeesAttemptId
        SettingsRepository.getRideFeesTask()
            .addOnSuccessListener { liveFees ->
                if (attemptId != selfServiceRideFeesAttemptId || !isAdded) return@addOnSuccessListener
                onSelfServiceRideFeesResolved(liveFees)
            }
            .addOnFailureListener { exception ->
                if (attemptId != selfServiceRideFeesAttemptId || !isAdded) return@addOnFailureListener
                Log.w(TAG, "Unable to refresh ride fees for self-service start", exception)
                onSelfServiceRideFeesResolved(null)
            }
            .withTimeout(RIDE_FEES_TIMEOUT_MS) {
                if (attemptId != selfServiceRideFeesAttemptId || !isAdded) return@withTimeout
                Log.w(TAG, "Ride fees refresh timed out for self-service start attemptId=$attemptId")
                onSelfServiceRideFeesResolved(null)
            }
    }

    private fun onSelfServiceRideFeesResolved(liveFees: RideFees?) {
        // Reuses the same LIVE/FALLBACK/UNAVAILABLE ladder as a normal trip start
        // (CurrentServiceViewModel.resolveStartRideFees) — see design D5 open question
        // on the multiplier snapshot possibly being stale when starting offline.
        val resolution = CurrentServiceViewModel.resolveStartRideFees(
            liveFees = liveFees,
            inMemoryFees = inMemoryRideFees,
            storedFees = RideRecoveryStore.getRideFeesSnapshot(preferences),
            currentMultiplier = selfServiceFeeMultiplier,
            storedMultiplier = preferences.getString(Constants.MULTIPLIER, null)?.toDoubleOrNull()
        )

        when (resolution.source) {
            CurrentServiceViewModel.StartRideFeesSource.LIVE,
            CurrentServiceViewModel.StartRideFeesSource.FALLBACK -> {
                val fees = resolution.fees ?: return
                if (resolution.source == CurrentServiceViewModel.StartRideFeesSource.FALLBACK) {
                    Toast.makeText(requireContext(), R.string.using_saved_pricing_snapshot, Toast.LENGTH_SHORT).show()
                }
                showSelfServiceStartDialog(fees)
            }
            CurrentServiceViewModel.StartRideFeesSource.UNAVAILABLE -> {
                Toast.makeText(requireContext(), R.string.start_trip_pricing_unavailable, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showSelfServiceStartDialog(fees: RideFees) {
        // Reuses the existing multiplier confirmation widget (dialog_trip_action +
        // multiplier_feed), the same one CurrentServiceFragment.showStartTripDialog uses
        // to confirm a normal trip start.
        val dialogLayout: View = LayoutInflater.from(requireContext()).inflate(R.layout.multiplier_feed, null)
        val editFeeMultiplier = dialogLayout.findViewById<EditText>(R.id.dialog_fee_multiplier)
        editFeeMultiplier.text = Editable.Factory.getInstance().newEditable(fees.feeMultiplier.toString())

        selfServiceStartDialog = showTripActionDialog(
            requireContext(),
            titleRes = R.string.self_service_start_title,
            message = getText(R.string.self_service_start_message),
            primaryTextRes = R.string.start_ride_action,
            secondaryTextRes = R.string.cancel,
            iconRes = R.drawable.ic_baseline_directions_car_24,
            primaryIconRes = R.drawable.assign_24,
            secondaryIconRes = R.drawable.cancel_24,
            customBody = dialogLayout
        ) { confirmed ->
            if (!confirmed || !isAdded) {
                return@showTripActionDialog
            }

            val inputMultiplier = editFeeMultiplier.text.toString().toDoubleOrNull() ?: 1.0
            selfServiceFeeMultiplier = if (inputMultiplier < 1.0) 1.0 else inputMultiplier
            RideRecoveryStore.persistMultiplier(preferences, selfServiceFeeMultiplier)

            // GPS capture (design D3): silent, non-blocking — use whichever fix is
            // freshest in the session (live or restored last-known location); never
            // request a new fix here.
            onSelfServiceTripConfirmed(
                multiplier = selfServiceFeeMultiplier,
                startedAt = Date().time / 1000,
                gpsFix = location
            )
        }.apply {
            setOnDismissListener {
                if (selfServiceStartDialog === this) {
                    selfServiceStartDialog = null
                }
            }
        }
    }

    /**
     * Starts the local provisional trip (add-driver-self-service design D5, step 1): a
     * client-generated local id starts `FeesService` metering immediately — never blocked on
     * connectivity or on the GPS fix being available yet — while [MainViewModel] persists the
     * trip (surviving process death via `RideRecoveryStore`, reusing its existing snapshot
     * pattern) and queues `POST /driver-app/me/services`. Once that syncs, `MainViewModel`
     * rebinds metering/UI to the real service id; `CurrentServiceFragment` (which owns the
     * `FeesService` binding) reflects both that and any terminal rejection.
     */
    private fun onSelfServiceTripConfirmed(multiplier: Double, startedAt: Long, gpsFix: Location?) {
        if (blockSelfServiceStartIfActiveTrip()) {
            return
        }
        val localId = "local-" + UUID.randomUUID().toString()

        RideRecoveryStore.clearIfStale(preferences, localId)
        RideRecoveryStore.attachToService(preferences, localId)

        val intentFee = Intent(requireContext(), FeesService::class.java).apply {
            putExtra(FeesService.SERVICE_ID, localId)
            putExtra(ORIGIN, getString(R.string.self_service_notification_label))
            putExtra(FEE_MULTIPLIER, multiplier)
        }
        FeesService.launch(requireContext(), intentFee)

        mainViewModel.startSelfServiceTrip(
            localId = localId,
            startedAt = startedAt,
            multiplier = multiplier,
            gpsFix = gpsFix
        )

        Log.d(TAG, "Self-service trip started locally: localId=$localId multiplier=$multiplier")

        if (isAdded) {
            findNavController().navigate(R.id.nav_current_service)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        alertsHandler?.removeCallbacks(alertsRunnable!!)
        applyConfirmDialog?.dismiss()
        applyConfirmDialog = null
        selfServiceStartDialog?.dismiss()
        selfServiceStartDialog = null
        activeTripBlockedDialog?.dismiss()
        activeTripBlockedDialog = null
        _binding = null
    }
}
