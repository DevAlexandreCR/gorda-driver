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
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.button.MaterialButton
import gorda.driver.R
import gorda.driver.background.FeesService
import gorda.driver.background.FeesService.Companion.CURRENT_FEES
import gorda.driver.background.FeesService.Companion.FEE_MULTIPLIER
import gorda.driver.background.FeesService.Companion.ORIGIN
import gorda.driver.background.FeesService.Companion.RESUME_RIDE
import gorda.driver.databinding.FragmentCurrentServiceBinding
import gorda.driver.interfaces.RideFees
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import gorda.driver.repositories.SettingsRepository
import gorda.driver.ui.MainViewModel
import gorda.driver.ui.history.ServiceDialogFragment
import gorda.driver.ui.home.HomeFragment
import gorda.driver.ui.service.ConnectionServiceDialog
import gorda.driver.utils.Constants
import gorda.driver.utils.NumberHelper
import gorda.driver.utils.ServiceHelper
import gorda.driver.utils.StringHelper
import java.util.Date
import java.util.Locale

class CurrentServiceFragment : Fragment() {

    companion object {
        const val TAG = "CurrentServiceFragment"
    }

    private var _binding: FragmentCurrentServiceBinding? = null
    private val binding get() = _binding!!

    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var btnStatus: Button
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
    private lateinit var scrollViewFees: ScrollView
    private lateinit var feeDetailsHeader: LinearLayout
    private lateinit var feeDetailsContent: LinearLayout
    private lateinit var expandIcon: ImageView
    private var isExpanded = false
    private lateinit var haveArrived: String
    private lateinit var startTrip: String
    private lateinit var endTrip: String
    private lateinit var toggleFragmentButton: FloatingActionButton
    private lateinit var connectionServiceButton: FloatingActionButton
    private var connectionDialog: ConnectionServiceDialog? = null
    private var feesService: FeesService = FeesService()
    private lateinit var chronometer: Chronometer
    private var fees: RideFees = RideFees()
    private var totalRide: Double = 0.0
    private var feeMultiplier: Double = 1.0
    private var totalDistance = 0.0
    private var startingRide = false
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var homeFragment: HomeFragment
    private var isServiceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isServiceBound = true
            val binder = service as FeesService.ChronometerBinder
            feesService = binder.getService()
            mainViewModel.changeConnectTripService(true)
            chronometer.base = feesService.getBaseTime()

            // Set up callback to update MainViewModel with fee data
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
        val root: View = binding.root

        context?.let {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(it)
        }
        loadFrozenFeesFromStorage()

        haveArrived = getString(R.string.service_have_arrived)
        startTrip = getString(R.string.service_start_trip)
        endTrip = getString(R.string.service_end_trip)

        requireActivity().onBackPressedDispatcher.addCallback(this) {}

        // Initialize views
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
        imgBtnMaps = binding.serviceLayout.imgBtnMaps
        imgButtonWaze = binding.serviceLayout.imgBtnWaze
        chronometer = binding.chronometer
        scrollViewFees = binding.scrollViewFees
        feeDetailsHeader = binding.feeDetailsHeader
        feeDetailsContent = binding.feeDetailsContent
        expandIcon = binding.expandIcon

        homeFragment = HomeFragment()
        connectionServiceButton = binding.connectedServiceButton

        mainViewModel.currentService.observe(viewLifecycleOwner) { service ->
            toggleFragmentButton.visibility = View.GONE

            if (service != null) {
                setOnClickListener(service)
                textName.text = service.name
                textPhone.text = service.phone
                textAddress.text = service.start_loc.name
                textAddressPreview.text = service.start_loc.name
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
                textComment.text = service.comment
                textPhone.setOnClickListener {
                    val intent = Intent(Intent.ACTION_DIAL, ("tel:" + service.phone).toUri())
                    startActivity(intent)
                }
                imgBtnMaps.setOnClickListener {
                    val uri: String = String.format(Locale.ENGLISH, "google.navigation:q=%f,%f",
                        service.start_loc.lat, service.start_loc.lng)
                    val mapIntent = Intent(Intent.ACTION_VIEW, uri.toUri())
                    mapIntent.setPackage("com.google.android.apps.maps")
                    activity?.let { fragmentActivity ->
                        mapIntent.resolveActivity(fragmentActivity.packageManager)?.let {
                            startActivity(mapIntent)
                        }
                    }
                }
                imgButtonWaze.setOnClickListener {
                    val uri: String = String.format(Locale.ENGLISH, "waze://?ll=%f,%f&navigate=yes",
                        service.start_loc.lat, service.start_loc.lng)
                    val wazeIntent = Intent(Intent.ACTION_VIEW, uri.toUri())
                    try {
                        startActivity(wazeIntent)
                    } catch (_: ActivityNotFoundException) {
                        Toast.makeText(requireContext(), R.string.not_waze, Toast.LENGTH_SHORT).show()
                    }
                }
                if (service.isInProgress()) {
                    if (service.metadata.arrived_at == null) {
                        btnStatus.text = haveArrived
                        scrollViewFees.visibility = View.INVISIBLE
                        stopFeeService()
                    } else if (service.metadata.start_trip_at == null) {
                        btnStatus.text = startTrip
                        scrollViewFees.visibility = View.INVISIBLE
                        stopFeeService()
                    } else {
                        btnStatus.text = endTrip
                        if (!ServiceHelper.isServiceRunning(requireContext(), FeesService::class.java) && !startingRide){
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
                            val dialog: AlertDialog = builder.create()
                            dialog.show()
                        }
                        scrollViewFees.visibility = View.VISIBLE
                    }
                }
            }
        }

        mainViewModel.rideFees.observe(viewLifecycleOwner) { fees ->
            applyRideFees(fees)
        }

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

        // Add observer for fee data that persists across fragment navigation
        mainViewModel.currentFeeData.observe(viewLifecycleOwner) { feeData ->
            totalRide = feeData.totalFee
            totalDistance = feeData.totalDistance

            chronometer.base = SystemClock.elapsedRealtime() - (feeData.elapsedSeconds * 1000)

            textTotalFee.text = NumberHelper.toCurrency(feeData.totalFee)
            textCurrentTimePrice.text = NumberHelper.toCurrency(feeData.timeFee)
            textCurrentDistancePrice.text = NumberHelper.toCurrency(feeData.distanceFee)
            textCurrentDistance.text = getString(R.string.distance_km, feeData.totalDistance / 1000)
            textFareMultiplierDisplay.text = feeMultiplier.toString()

            // Show toggle button based on elapsed time, but only if there's no next service
            if (mainViewModel.nextService.value == null) {
                if (feeData.elapsedSeconds > this.fees.timeoutToConnection && !toggleFragmentButton.isVisible) {
                    toggleFragmentButton.visibility = View.VISIBLE
                }
            } else {
                toggleFragmentButton.visibility = View.GONE
            }
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
            val builder = AlertDialog.Builder(requireContext())
            val dialogLayout: View = LayoutInflater.from(activity).inflate(R.layout.multiplier_feed, null)
            val editFeeMultiplier = dialogLayout.findViewById<EditText>(R.id.dialog_fee_multiplier)

            val currentMultiplier = if (isServiceBound) {
                feesService.getMultiplier()
            } else {
                feeMultiplier
            }

            editFeeMultiplier.text = Editable.Factory.getInstance().newEditable(currentMultiplier.toString())
            builder.setTitle(R.string.edit_multiplier)
                .setView(dialogLayout)
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

                // Update local value
                feeMultiplier = newMultiplier
                textFareMultiplier.text = newMultiplier.toString()

                // Update service if bound
                if (isServiceBound) {
                    feesService.setMultiplier(newMultiplier)
                }

                // Save to SharedPreferences
                sharedPreferences.edit(commit = true) {
                    putString(Constants.MULTIPLIER, newMultiplier.toString())
                }

                Toast.makeText(requireContext(), getString(R.string.multiplier_updated, newMultiplier), Toast.LENGTH_SHORT).show()
            }
            val dialog: AlertDialog = builder.create()
            dialog.show()
        }

        // Setup collapsible fee details
        setupFeeDetailsCollapse()

        // Setup bottom sheet behavior to start expanded
        setupBottomSheetBehavior(BottomSheetBehavior.STATE_EXPANDED)

        toggleFragmentButton.setOnClickListener {
            toggleFragment()
        }
        return root
    }

    override fun onResume() {
        super.onResume()
        checkAndBindToExistingService()
        if (btnStatus.text == endTrip) setupBottomSheetBehavior(BottomSheetBehavior.STATE_COLLAPSED)
    }

    private fun checkAndBindToExistingService() {
        if (!isServiceBound && ServiceHelper.isServiceRunning(requireContext(), FeesService::class.java)) {
            val intentFee = Intent(requireContext(), FeesService::class.java)
            requireContext().bindService(intentFee, serviceConnection, BIND_NOT_FOREGROUND)
        }
    }

    private fun setupBottomSheetBehavior(state: Int) {
        val serviceLayoutView = binding.serviceLayout.root
        val bottomSheetBehavior = BottomSheetBehavior.from(serviceLayoutView)

        bottomSheetBehavior.state = state
    }

    private fun setupFeeDetailsCollapse() {
        feeDetailsHeader.setOnClickListener {
            toggleFeeDetails()
        }
    }

    private fun toggleFeeDetails() {
        isExpanded = !isExpanded

        if (isExpanded) {
            // Expand
            feeDetailsContent.visibility = View.VISIBLE
            expandIcon.animate().rotation(180f).setDuration(200).start()
        } else {
            // Collapse
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

    private fun setOnClickListener(service: Service) {
        btnStatus.setOnClickListener {
            val now = Date().time / 1000
            mainViewModel.setLoading(true)
            when (btnStatus.text) {
                haveArrived -> {
                    service.metadata.arrived_at = now
                    service.updateMetadata()
                        .addOnSuccessListener {
                            mainViewModel.setLoading(false)
                            Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            mainViewModel.setLoading(false)
                            btnStatus.text = haveArrived
                            it.message?.let { message -> Log.e(TAG, message) }
                            Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                        }
                }

                startTrip -> {
                    syncRideFeesBeforeStart {
                        mainViewModel.setLoading(false)
                        setupBottomSheetBehavior(BottomSheetBehavior.STATE_COLLAPSED)
                        showStartTripDialog(service, now)
                    }
                }

                else -> {
                    if (service.metadata.start_trip_at != null && now - service.metadata.start_trip_at!! > this.fees.timeoutToComplete) {
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
                            if (confirmed) {
                                val tripDistance = NumberHelper.roundDouble(totalDistance).toInt()
                                val tripFee = NumberHelper.roundDouble(getTotalFee()).toInt()
                                val route = ServiceMetadata.serializeRoute(feesService.getPoints())
                                val tripMultiplier = feeMultiplier
                                stopFeeService()
                                mainViewModel.setLoading(false)
                                mainViewModel.completeCurrentService()
                                Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
                                findNavController().navigate(R.id.nav_home)
                                service.terminate(route, tripDistance, tripFee, tripMultiplier)
                                    .addOnFailureListener {
                                        mainViewModel.setLoading(false)
                                        btnStatus.text = endTrip
                                        service.metadata.end_trip_at = null
                                        it.message?.let { message -> Log.e(TAG, message) }
                                        Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                                    }
                            } else {
                                mainViewModel.setLoading(false)
                            }
                        }
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.cannot_complete_service_yet, this.fees.timeoutToComplete / 60), Toast.LENGTH_SHORT).show()
                        mainViewModel.setLoading(false)
                    }
                }
            }
        }
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
        this.fees = rideFees
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

    private fun syncRideFeesBeforeStart(onReady: () -> Unit) {
        SettingsRepository.getRideFees(
            onSuccess = { rideFees ->
                applyRideFees(rideFees)
                mainViewModel.setRideFees(rideFees)
                persistRideFeesSnapshot(rideFees)
                onReady()
            },
            onError = { message ->
                Log.w(TAG, "Unable to refresh ride fees before trip start: $message")
                resolveRideFeesFallback()?.let { fallbackFees ->
                    applyRideFees(fallbackFees)
                    mainViewModel.setRideFees(fallbackFees)
                }
                onReady()
            }
        )
    }

    private fun resolveRideFeesFallback(): RideFees? {
        if (hasUsableRideFees(fees)) {
            return fees.copy(feeMultiplier = feeMultiplier)
        }

        val storedRideFees = getStoredRideFeesSnapshot() ?: return null
        val storedMultiplier = sharedPreferences.getString(Constants.MULTIPLIER, null)?.toDoubleOrNull()

        return if (storedMultiplier != null) {
            storedRideFees.copy(feeMultiplier = storedMultiplier)
        } else {
            storedRideFees
        }
    }

    private fun hasUsableRideFees(rideFees: RideFees): Boolean {
        return rideFees != RideFees()
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

    private fun showStartTripDialog(service: Service, now: Long) {
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
            if (confirmed) {
                val inputMultiplier = editFeeMultiplier.text.toString().toDoubleOrNull() ?: 1.0
                feeMultiplier = if (inputMultiplier < 1.0) 1.0 else inputMultiplier
                textFareMultiplier.text = feeMultiplier.toString()
                sharedPreferences.edit(commit = true) {
                    putString(Constants.MULTIPLIER, feeMultiplier.toString())
                }
                service.metadata.start_trip_at = now
                startingRide = true
                service.updateMetadata()
                    .addOnSuccessListener {
                        startServiceFee(service.start_loc.name)
                        mainViewModel.setLoading(false)
                    }
                    .addOnFailureListener {
                        btnStatus.text = startTrip
                        mainViewModel.setLoading(false)
                        it.message?.let { message -> Log.e(TAG, message) }
                        Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                        service.metadata.start_trip_at = null
                    }
            } else {
                mainViewModel.setLoading(false)
            }
        }
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
