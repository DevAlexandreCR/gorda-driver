package gorda.driver.ui.service.current

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.navigation.fragment.findNavController
import gorda.driver.R
import gorda.driver.databinding.FragmentCurrentServiceBinding
import gorda.driver.models.Service
import gorda.driver.ui.MainViewModel
import java.util.*


class CurrentServiceFragment : Fragment() {

    companion object {
        const val TAG = "gorda.driver.ui.service.current.CurrentServiceFragment"
    }

    private var _binding: FragmentCurrentServiceBinding? = null
    private val mainViewModel: MainViewModel by activityViewModels()
    private lateinit var btnStatus: Button
    private lateinit var textName: TextView
    private lateinit var textPhone: TextView
    private lateinit var textAddress: TextView
    private lateinit var textComment: TextView
    private lateinit var haveArrived: String
    private lateinit var startTrip: String
    private lateinit var endTrip: String
    private lateinit var binding: FragmentCurrentServiceBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentCurrentServiceBinding.inflate(inflater, container, false)
        val root: View = binding.root

        textName = binding.currentServiceName
        textPhone = binding.currentPhone
        textAddress = binding.currentAddress
        textComment = binding.serviceComment
        btnStatus = binding.btnServiceStatus
        mainViewModel.currentService.observe(viewLifecycleOwner) { service ->
            if (service != null) {
                setOnClickListener(service)
                textName.text = service.name
                textPhone.text = service.phone
                textAddress.text = service.start_loc.name
                textComment.text = service.comment
                if (service.metadata.arrivedAt == null) {
                    btnStatus.text = haveArrived
                } else if (service.metadata.startTripAt == null){
                    btnStatus.text = startTrip
                } else {
                    btnStatus.text = endTrip
                }
            } else {
                findNavController().navigate(R.id.nav_home)
            }
        }
        textPhone.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + textPhone.text))
            startActivity(intent)
        }
        return root
    }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        haveArrived = context.resources.getString(R.string.service_have_arrived)
        startTrip = context.resources.getString(R.string.service_start_trip)
        endTrip = context.resources.getString(R.string.service_end_trip)
    }

    private fun setOnClickListener(service: Service) {
        btnStatus.setOnClickListener {
            val now = Date().time / 1000
            when (btnStatus.text) {
                haveArrived -> {
                    service.metadata.arrivedAt = now
                }
                startTrip -> {
                    service.metadata.startTripAt = now
                }
                else -> {
                    service.metadata.endTripAt = now
                    service.status = Service.STATUS_TERMINATED
                }
            }
            service.updateMetadata()
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
                }
                .addOnFailureListener {
                    it.message?.let { message -> Log.e(TAG, message) }
                    Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}