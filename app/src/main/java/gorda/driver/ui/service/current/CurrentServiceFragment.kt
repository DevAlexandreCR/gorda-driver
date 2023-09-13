package gorda.driver.ui.service.current

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
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import gorda.driver.R
import gorda.driver.background.FeesService
import gorda.driver.databinding.FragmentCurrentServiceBinding
import gorda.driver.models.Service
import gorda.driver.ui.MainViewModel
import gorda.driver.utils.Constants
import gorda.driver.utils.Utils
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
    private lateinit var haveArrived: String
    private lateinit var startTrip: String
    private lateinit var endTrip: String
    private lateinit var binding: FragmentCurrentServiceBinding
    private lateinit var feesService: FeesService
    private lateinit var chronometer: Chronometer
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as FeesService.ChronometerBinder
            feesService = binder.getService()
            mainViewModel.changeConnectTripService(true)
            chronometer.base = feesService.getElapsedTime()
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
        btnStatus = binding.btnServiceStatus
        imgBtnMaps = binding.imgBtnMaps
        imgButtonWaze = binding.imgBtnWaze
        chronometer = binding.chronometer
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
                }
            } else {
                findNavController().navigate(R.id.nav_home)
            }
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
            var canUpdate = true

            when (btnStatus.text) {
                haveArrived -> {
                    service.metadata.arrived_at = now
                }
                startTrip -> {
                    service.metadata.start_trip_at = now
                    Intent(requireContext(), FeesService::class.java).also { intentFee ->
                        intentFee.putExtra(Constants.START_TRIP, doubleArrayOf(service.start_loc.lat, service.start_loc.lng))
                        if (Utils.isNewerVersion(Build.VERSION_CODES.O)) {
                            requireContext().startForegroundService(intentFee)
                        } else {
                            requireContext().startService(intentFee)
                        }
                    }
                }
                else -> {
                    canUpdate = if (service.metadata.start_trip_at != null) (now - service.metadata.start_trip_at!!) > 240
                    else false

                    if (canUpdate) {
                        service.metadata.end_trip_at = now
                        service.status = Service.STATUS_TERMINATED
                        Intent(requireContext(), FeesService::class.java).also { intentFee ->
                            requireContext().stopService(intentFee)
                        }
                    }
                }
            }
            if (canUpdate) service.updateMetadata()
                .addOnSuccessListener {
                    if (service.status == Service.STATUS_TERMINATED) mainViewModel.completeCurrentService()

                    Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    it.message?.let { message -> Log.e(TAG, message) }
                    Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                }
            else Toast.makeText(requireContext(), R.string.cannot_complete_service_yet, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    override fun onChronometerTick(chrono: Chronometer) {
        //TODO: calculate service fees
    }
}