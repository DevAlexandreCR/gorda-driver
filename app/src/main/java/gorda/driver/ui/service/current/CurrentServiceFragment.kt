package gorda.driver.ui.service.current

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
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
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
    private val currentServiceViewModel: CurrentServiceViewModel by viewModels()
    private val mainViewModel: MainViewModel by activityViewModels()

    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCurrentServiceBinding.inflate(inflater, container, false)
        val root: View = binding.root

        val textName: TextView = binding.currentServiceName
        val textPhone: TextView = binding.currentPhone
        val textAddress: TextView = binding.currentAddress
        val textComment: TextView = binding.serviceComment
        val btnStatus: Button = binding.btnServiceStatus
        val haveArrived = requireActivity().resources.getString(R.string.service_have_arrived)
        val startTrip = requireActivity().resources.getString(R.string.service_start_trip)
        val endTrip = requireActivity().resources.getString(R.string.service_end_trip)
        val onSuccess = OnSuccessListener<Void> {
            Toast.makeText(requireContext(), R.string.service_updated, Toast.LENGTH_SHORT).show()
        }
        val onFailure = OnFailureListener {
            it.message?.let { message -> Log.e(TAG, message) }
            Toast.makeText(requireContext(), R.string.common_error, Toast.LENGTH_SHORT).show()
        }
        mainViewModel.currentService.observe(viewLifecycleOwner) { service ->
            if (service != null) {
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
                            service.status = Service.STATUS_COMPLETED
                        }
                    }
                    service.update()
                        .addOnSuccessListener(onSuccess)
                        .addOnFailureListener(onFailure)
                }
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
        return root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}