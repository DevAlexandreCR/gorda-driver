package gorda.driver.ui.service.current

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import gorda.driver.R
import gorda.driver.databinding.FragmentCurrentServiceBinding
import gorda.driver.ui.MainViewModel

class CurrentServiceFragment : Fragment() {

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
        currentServiceViewModel.text.observe(viewLifecycleOwner) {
            textName.text = it
        }

        mainViewModel.currentService.observe(viewLifecycleOwner) { service ->
            if (service != null) {
                textName.text = service.name
                textPhone.text = service.phone
                textAddress.text = service.start_loc.name
                textComment.text = service.comment
                if (service.metadata.arrivedAt == null) {
                    btnStatus.text = requireActivity().resources.getString(R.string.service_have_arrived)
                } else if (service.metadata.startTripAt == null){
                    btnStatus.text = requireActivity().resources.getString(R.string.service_start_trip)
                } else {
                    btnStatus.text =  requireActivity().resources.getString(R.string.service_end_trip)
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