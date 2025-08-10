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
import android.widget.Chronometer.OnChronometerTickListener
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.edit
import androidx.core.net.toUri
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
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


class CurrentServiceFragment : Fragment(), OnChronometerTickListener {

    companion object {
        const val TAG = "CurrentServiceFragment"
    }

    private var _binding: FragmentCurrentServiceBinding? = null
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var btnStatus: Button
    private lateinit var imgBtnMaps: ImageButton
    private lateinit var imgButtonWaze: ImageButton
    private lateinit var textName: TextView
    private lateinit var textPhone: TextView
    private lateinit var textAddress: TextView
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
    private lateinit var textTotalFee: TextView
    private lateinit var layoutFees: ConstraintLayout
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
    private lateinit var binding: FragmentCurrentServiceBinding
    private var feesService: FeesService = FeesService()
    private lateinit var chronometer: Chronometer
    private var fees: RideFees = RideFees()
    private var totalRide: Double = 0.0
    private var feeMultiplier: Double = 1.0
    private var totalDistance = 0.0
    private var startingRide = false
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var fragmentManager: FragmentManager
    private lateinit var transaction: FragmentTransaction
    private lateinit var homeFragment: HomeFragment
    private var isServiceBound = false
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            isServiceBound = true
            val binder = service as FeesService.ChronometerBinder
            feesService = binder.getService()
            mainViewModel.changeConnectTripService(true)
            chronometer.base = feesService.getBaseTime()
            chronometer.start()
            chronometer.onChronometerTickListener = this@CurrentServiceFragment

            updateUIFromService()
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
        binding = FragmentCurrentServiceBinding.inflate(inflater, container, false)
        val root: View = binding.root

        context?.let {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(it)
        }

        haveArrived = getString(R.string.service_have_arrived)
        startTrip = getString(R.string.service_start_trip)
        endTrip = getString(R.string.service_end_trip)

        requireActivity().onBackPressedDispatcher.addCallback(this) {}
        textName = binding.serviceLayout.currentServiceName
        textPhone = binding.serviceLayout.currentPhone
        textAddress = binding.serviceLayout.currentAddress
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
        textTotalFee = binding.textPrice
        btnStatus = binding.serviceLayout.btnServiceStatus
        imgBtnMaps = binding.serviceLayout.imgBtnMaps
        imgButtonWaze = binding.serviceLayout.imgBtnWaze
        chronometer = binding.chronometer
        layoutFees = binding.layoutFees
        scrollViewFees = binding.scrollViewFees
        feeDetailsHeader = binding.feeDetailsHeader
        feeDetailsContent = binding.feeDetailsContent
        expandIcon = binding.expandIcon
        homeFragment = HomeFragment()
        fragmentManager = childFragmentManager
        connectionServiceButton = binding.connectedServiceButton
        mainViewModel.isLoading.observe(viewLifecycleOwner) { loading ->
            btnStatus.isEnabled = !loading
        }
        mainViewModel.currentService.observe(viewLifecycleOwner) { service ->
            if (service != null) {
                setOnClickListener(service)
                textName.text = service.name
                textPhone.text = service.phone
                textAddress.text = service.start_loc.name
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
                    } catch (e: ActivityNotFoundException) {
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
                                sharedPreferences.edit(commit = true) { remove(Constants.MULTIPLIER) }
                                sharedPreferences.edit(commit = true) { remove(Constants.POINTS) }
                                sharedPreferences.edit(commit = true) { remove(Constants.START_TIME) }
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
            this.fees = fees
            feeMultiplier = fees.feeMultiplier
            textPriceBase.text = NumberHelper.toCurrency(fees.feesBase)
            textPriceMinFee.text = NumberHelper.toCurrency(fees.priceMinFee)
            textPriceAddFee.text = NumberHelper.toCurrency(fees.priceAddFee)
            textDistancePrice.text = NumberHelper.toCurrency(fees.priceKm)
            textTimePrice.text = NumberHelper.toCurrency(fees.priceMin)
            textFareMultiplier.text = feeMultiplier.toString()
            if (isServiceBound) {
                feesService.setMultiplier(feeMultiplier)
            }
        }
        mainViewModel.nextService.observe(viewLifecycleOwner) { service ->
            if (service != null) {
                connectionDialog = ConnectionServiceDialog(service)
                connectionServiceButton.visibility = ConstraintLayout.VISIBLE
            } else {
                connectionDialog = null
                connectionServiceButton.visibility = ConstraintLayout.INVISIBLE
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

            // Get current multiplier from service if bound, otherwise use local value
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

        toggleFragmentButton.setOnClickListener {
            toggleFragment()
        }
        return root
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
        transaction = fragmentManager.beginTransaction()

        if (homeFragment.isAdded) {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_CLOSE)
            transaction.remove(homeFragment)
            toggleFragmentButton.setImageResource(R.drawable.service_list_24)
        } else {
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN)
            transaction.replace(binding.root.id, homeFragment)
            toggleFragmentButton.setImageResource(R.drawable.current_return_24)
        }

        transaction.commit()
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
                        }.withTimeout {
                            mainViewModel.setLoading(false)
                            service.metadata.arrived_at = null
                            mainViewModel.setErrorTimeout(true)
                            btnStatus.text = haveArrived
                        }
                }

                startTrip -> {
                    val builder = AlertDialog.Builder(requireContext())
                    val dialogLayout: View = LayoutInflater.from(activity).inflate(R.layout.multiplier_feed, null)
                    val editFeeMultiplier = dialogLayout.findViewById<EditText>(R.id.dialog_fee_multiplier)
                    editFeeMultiplier.text = Editable.Factory.getInstance().newEditable(feeMultiplier.toString())
                    service.metadata.start_trip_at = now
                    builder.setTitle(R.string.start_ride)
                        .setCancelable(false)
                        .setView(dialogLayout)
                        .setMessage(R.string.start_ride_message)
                        .setPositiveButton(R.string.start_ride) { _, _ ->
                            startingRide = true
                            service.updateMetadata()
                                .addOnSuccessListener {
                                    mainViewModel.setLoading(false)
                                    val inputMultiplier = editFeeMultiplier.text.toString().toDoubleOrNull() ?: 1.0
                                    feeMultiplier = if (inputMultiplier < 1.0) 1.0 else inputMultiplier
                                    textFareMultiplier.text = feeMultiplier.toString()
                                    sharedPreferences.edit(commit = true) {
                                        putString(
                                            Constants.MULTIPLIER,
                                            feeMultiplier.toString()
                                        )
                                    }
                                    startServiceFee(service.start_loc.name)
                                }
                                .addOnFailureListener {
                                    btnStatus.text = startTrip
                                    mainViewModel.setLoading(false)
                                    it.message?.let { message -> Log.e(TAG, message) }
                                    Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                                    service.metadata.start_trip_at = null
                                }.withTimeout {
                                    btnStatus.text = startTrip
                                    mainViewModel.setLoading(false)
                                    service.metadata.start_trip_at = null
                                    mainViewModel.setErrorTimeout(true)
                                }
                        }
                        .setNegativeButton(R.string.cancel) { dialog, _ ->
                            dialog.dismiss()
                            mainViewModel.setLoading(false)
                        }
                    val dialog: AlertDialog = builder.create()
                    dialog.show()
                }

                else -> {
                    if (service.metadata.start_trip_at != null && now - service.metadata.start_trip_at!! > this.fees.timeoutToComplete) {
                        val builderFinalize = AlertDialog.Builder(requireContext())
                        val message = getString(R.string.finalizing_message, NumberHelper.toCurrency(getTotalFee()))
                        builderFinalize.setTitle(R.string.finalize_service)
                            .setCancelable(false)
                            .setMessage(StringHelper.getString(message))
                            .setPositiveButton(R.string.yes) { _, _ ->
                                val tripDistance = NumberHelper.roundDouble(totalDistance).toInt()
                                val tripFee = getTotalFee().toInt()
                                val route = ServiceMetadata.serializeRoute(feesService.getPoints())
                                val tripMultiplier = feeMultiplier
                                service.terminate(route, tripDistance, tripFee, tripMultiplier)
                                    .addOnSuccessListener {
                                        stopFeeService()
                                        mainViewModel.setLoading(false)
                                        mainViewModel.completeCurrentService()
                                        Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
                                        findNavController().navigate(R.id.nav_home)
                                    }
                                    .addOnFailureListener {
                                        mainViewModel.setLoading(false)
                                        btnStatus.text = endTrip
                                        service.metadata.end_trip_at = null
                                        it.message?.let { message -> Log.e(TAG, message) }
                                        Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                                    }.withTimeout {
                                        mainViewModel.setLoading(false)
                                        btnStatus.text = endTrip
                                        service.metadata.end_trip_at = null
                                        mainViewModel.setErrorTimeout(true)
                                    }
                            }
                            .setNegativeButton(R.string.no) { dialog, _ ->
                                dialog.dismiss()
                                mainViewModel.setLoading(false)
                            }
                        val dialogFinalize: AlertDialog = builderFinalize.create()
                        dialogFinalize.show()
                    } else {
                        service.metadata.end_trip_at = now
                        service.updateMetadata()
                            .addOnSuccessListener {
                                stopFeeService()
                                mainViewModel.setLoading(false)
                                mainViewModel.completeCurrentService()
                                Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
                                findNavController().navigate(R.id.nav_home)
                            }
                            .addOnFailureListener {
                                mainViewModel.setLoading(false)
                                btnStatus.text = endTrip
                                service.metadata.end_trip_at = null
                                it.message?.let { message -> Log.e(TAG, message) }
                                Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                            }.withTimeout {
                                mainViewModel.setLoading(false)
                                btnStatus.text = endTrip
                                service.metadata.end_trip_at = null
                                mainViewModel.setErrorTimeout(true)
                            }
                    }
                }
            }
        }
    }

    private fun startServiceFee(origin: String, resumeRide: Boolean = false) {
        val intentFee = Intent(requireContext(), FeesService::class.java)
        intentFee.putExtra(ORIGIN, origin)
        intentFee.putExtra(FEE_MULTIPLIER, feeMultiplier)

        val gson = com.google.gson.Gson()
        val feesJson = gson.toJson(fees)
        sharedPreferences.edit(commit = true) {
            putString(CURRENT_FEES, feesJson)
        }

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
        }

        mainViewModel.changeConnectTripService(false)
    }

    private fun getTotalFee(): Double {
        return if (isServiceBound) {
            feesService.getTotalFee()
        } else {
            totalRide
        }
    }

    private fun updateUIFromService() {
        if (isServiceBound) {
            totalRide = feesService.getTotalFee()
            totalDistance = feesService.getTotalDistance()

            textTotalFee.text = NumberHelper.toCurrency(totalRide)
            textCurrentTimePrice.text = NumberHelper.toCurrency(feesService.getTimeFee())
            textCurrentDistancePrice.text = NumberHelper.toCurrency(feesService.getDistanceFee())
            textCurrentDistance.text = getString(R.string.distance_km, totalDistance / 1000)

            if (feesService.getElapsedSeconds() > this.fees.timeoutToConnection && !toggleFragmentButton.isVisible && mainViewModel.nextService.value == null) {
                toggleFragmentButton.visibility = View.VISIBLE
            }
        }
    }

    override fun onChronometerTick(chronometer: Chronometer?) {
        updateUIFromService()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
