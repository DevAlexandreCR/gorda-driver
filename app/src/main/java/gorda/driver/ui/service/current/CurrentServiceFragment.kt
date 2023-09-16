package gorda.driver.ui.service.current

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Context.BIND_NOT_FOREGROUND
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.SystemClock
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.Chronometer.OnChronometerTickListener
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import gorda.driver.R
import gorda.driver.background.FeesService
import gorda.driver.databinding.FragmentCurrentServiceBinding
import gorda.driver.interfaces.RideFees
import gorda.driver.maps.Map
import gorda.driver.models.Service
import gorda.driver.ui.MainViewModel
import gorda.driver.utils.Constants
import gorda.driver.utils.Utils
import java.text.NumberFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.round


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
    private lateinit var haveArrived: String
    private lateinit var startTrip: String
    private lateinit var endTrip: String
    private lateinit var binding: FragmentCurrentServiceBinding
    private lateinit var feesService: FeesService
    private lateinit var chronometer: Chronometer
    private lateinit var fees: RideFees
    private lateinit var currencyFormat: NumberFormat
    private var totalRide: Double = 0.0
    private var feeMultiplier: Double = 1.0
    private var totalDistance = 0.0
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FeesService.ChronometerBinder
            feesService = binder.getService()
            mainViewModel.changeConnectTripService(true)
            chronometer.base = feesService.getBaseTime()
            chronometer.start()
            chronometer.onChronometerTickListener = this@CurrentServiceFragment
        }

        override fun onServiceDisconnected(name: ComponentName?) {
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

        requireActivity().onBackPressedDispatcher.addCallback(this) {}
        textName = binding.currentServiceName
        textPhone = binding.currentPhone
        textAddress = binding.currentAddress
        textComment = binding.serviceComment
        textPriceBase = binding.textBaseFare
        textPriceMinFee = binding.textFareMin
        textPriceAddFee = binding.textFees
        textDistancePrice = binding.textDistanceFare
        textCurrentDistance = binding.textCurrentDistance
        textCurrentDistancePrice = binding.textPriceByDistance
        textTimePrice = binding.textTimeFare
        textCurrentTimePrice = binding.textPriceByTime
        textFareMultiplier = binding.textFareMultiplier
        textTotalFee = binding.textPrice
        btnStatus = binding.btnServiceStatus
        imgBtnMaps = binding.imgBtnMaps
        imgButtonWaze = binding.imgBtnWaze
        chronometer = binding.chronometer
        layoutFees = binding.layoutFees
        currencyFormat = NumberFormat.getCurrencyInstance()
        mainViewModel.currentService.observe(viewLifecycleOwner) { service ->
            if (service != null) {
                setOnClickListener(service)
                textName.text = service.name
                textPhone.text = service.phone
                textAddress.text = service.start_loc.name
                textComment.text = service.comment
                textPhone.setOnClickListener {
                    val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + service.phone))
                    startActivity(intent)
                }
                imgBtnMaps.setOnClickListener {
                    val uri: String = String.format(Locale.ENGLISH, "google.navigation:q=%f,%f",
                        service.start_loc.lat, service.start_loc.lng)
                    val mapIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
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
                    val wazeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
                    try {
                        startActivity(wazeIntent)
                    } catch (e: ActivityNotFoundException) {
                        Toast.makeText(requireContext(), R.string.not_waze, Toast.LENGTH_SHORT).show()
                    }
                }
                if (service.metadata.arrived_at == null) {
                    btnStatus.text = haveArrived
                } else if (service.metadata.start_trip_at == null){
                    btnStatus.text = startTrip
                } else {
                    btnStatus.text = endTrip
                    layoutFees.visibility = ConstraintLayout.VISIBLE
                }

                if (service.status == Service.STATUS_TERMINATED) {

                }
            } else {
                findNavController().navigate(R.id.nav_home)
            }
        }
        mainViewModel.rideFees.value?.let { fees ->
            this.fees = fees
            feeMultiplier = getFeeMultiplier()
            textPriceBase.text = currencyFormat.format(this.fees.feesBase)
            textPriceMinFee.text = currencyFormat.format(this.fees.priceMinFee)
            textPriceAddFee.text = currencyFormat.format(this.fees.priceAddFee)
            textDistancePrice.text = currencyFormat.format(this.fees.priceKm)
            textTimePrice.text = currencyFormat.format(this.fees.priceMin)
            textFareMultiplier.text = feeMultiplier.toString()
        }
        return root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        haveArrived = context.resources.getString(R.string.service_have_arrived)
        startTrip = context.resources.getString(R.string.service_start_trip)
        endTrip = context.resources.getString(R.string.service_end_trip)
        Intent(requireContext(), FeesService::class.java).also { intentFee ->
            requireContext().bindService(intentFee, serviceConnection, BIND_NOT_FOREGROUND)
            mainViewModel.changeConnectTripService(true)
        }
    }

    override fun onDetach() {
        super.onDetach()
        if (mainViewModel.isTripStarted.value == true) {
            requireContext().unbindService(serviceConnection)
            mainViewModel.changeConnectTripService(false)
        }
    }

    private fun setOnClickListener(service: Service) {
        btnStatus.setOnClickListener {
            val now = Date().time / 1000

            when (btnStatus.text) {
                haveArrived -> {
                    service.metadata.arrived_at = now
                    service.updateMetadata()
                        .addOnSuccessListener {
                            Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            it.message?.let { message -> Log.e(TAG, message) }
                            Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                        }
                }

                startTrip -> {
                    service.metadata.start_trip_at = now
                    service.updateMetadata()
                        .addOnSuccessListener {
                            Intent(requireContext(), FeesService::class.java).also { intentFee ->
                                intentFee.putExtra(Constants.LOCATION_EXTRA, service.start_loc.name)
                                if (Utils.isNewerVersion(Build.VERSION_CODES.O)) {
                                    requireContext().startForegroundService(intentFee)
                                } else {
                                    requireContext().startService(intentFee)
                                }
                            }
                            Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
                        }
                        .addOnFailureListener {
                            it.message?.let { message -> Log.e(TAG, message) }
                            Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                        }
                }

                else -> {
                    if (service.metadata.start_trip_at != null && now - service.metadata.start_trip_at!! > 240) {
                        val builder = AlertDialog.Builder(requireContext())
                        builder.setTitle(R.string.finalize_service)
                            .setCancelable(false)
                            .setMessage(getString(R.string.finalizing_message, getTotalFee()))
                            .setPositiveButton(R.string.yes) { _, _ ->
                                service.metadata.end_trip_at = now
                                service.status = Service.STATUS_TERMINATED
                                service.metadata.trip_distance = round(totalDistance).toInt()
                                service.metadata.trip_fee = getTotalFee().toInt()
                                service.updateMetadata()
                                    .addOnSuccessListener {
                                        Intent(
                                            requireContext(),
                                            FeesService::class.java
                                        ).also { intentFee ->
                                            requireContext().stopService(intentFee)
                                        }
                                        mainViewModel.completeCurrentService()
                                        Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
                                    }
                                    .addOnFailureListener {
                                        it.message?.let { message -> Log.e(TAG, message) }
                                        Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                                    }
                            }
                            .setNegativeButton(R.string.no) { dialog, _ ->
                                dialog.dismiss()
                            }
                        val dialog: AlertDialog = builder.create()
                        dialog.show()
                    } else {
                        Toast.makeText(
                            requireContext(),
                            R.string.cannot_complete_service_yet,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onChronometerTick(chrono: Chronometer) {
        var distance = 0.0
        for (i in 0 until feesService.getPoints().size - 1) {
            distance += Map.calculateDistanceBetween(feesService.getPoints()[i], feesService.getPoints()[i + 1])
        }
        val priceSec = fees.priceMin / 60
        val priceMeter = fees.priceKm / 1000
        totalDistance = distance * priceMeter
        val time = priceSec * feesService.getElapsedSeconds()
        totalRide = (distance + time + fees.feesBase) * feeMultiplier
        textTotalFee.text = currencyFormat.format(totalRide)
        textCurrentDistance.text = String.format("%.2f", totalDistance / 1000)
        textCurrentTimePrice.text = currencyFormat.format(time)
        textCurrentDistancePrice.text = currencyFormat.format(distance)
    }

    private fun getFeeMultiplier(): Double {
        val calendar = Calendar.getInstance()
        return if (isFestive()) {
            when(calendar.get(Calendar.HOUR_OF_DAY)) {
                in 0..5 -> fees.priceFestiveNight
                in 19..23 -> fees.priceFestiveNight
                else -> fees.priceFestive
            }
        } else {
            when(calendar.get(Calendar.HOUR_OF_DAY)) {
                in 0..5 -> fees.priceFestiveNight
                in 19..23 -> fees.priceNightFee
                else -> 1.0
            }
        }
    }

    private fun getTotalFee(): String {
        return if (totalRide < fees.priceMinFee) {
            currencyFormat.format(fees.priceMinFee)
        } else {
            currencyFormat.format(round(totalRide / 100) * 100)
        }
    }

    private fun isFestive(): Boolean {
        val calendar = Calendar.getInstance()
        val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)

        return dayOfWeek == 1
    }
}