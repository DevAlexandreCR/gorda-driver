package gorda.go.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import com.bumptech.glide.Glide
import gorda.go.R
import gorda.go.databinding.FragmentProfileBinding
import gorda.go.models.Driver
import gorda.go.ui.MainViewModel

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private lateinit var textName: TextView
    private lateinit var textEmail: TextView
    private lateinit var textPhone: TextView
    private lateinit var textPlate: TextView
    private lateinit var image: ImageView
    private val mainViewModel: MainViewModel by activityViewModels()
    private val viewModel: ProfileViewModel by viewModels()

    // This property is only valid between onCreateView and
    // onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {

        _binding = FragmentProfileBinding.inflate(inflater, container, false)
        val root: View = binding.root

        textName = binding.profileName
        textEmail = binding.profileEmail
        textPhone = binding.profilePhone
        textPlate = binding.profileVehiclePlate
        image = binding.imageProfile

        mainViewModel.driver.observe(viewLifecycleOwner) { driver ->
            when (driver) {
                is Driver -> {
                    viewModel.setDriver(driver)
                }
                else -> {}
            }
        }

        viewModel.driver.observe(viewLifecycleOwner) { driver ->
            setDriver(driver)
        }

        return root
    }

    private fun setDriver(driver: Driver) {
        textName.text = driver.name
        textEmail.text = driver.email
        textPhone.text = driver.phone
        textPlate.text = driver.vehicle.plate
        driver.photoUrl?.let { url ->
            Glide
                .with(this)
                .load(url)
                .placeholder(R.mipmap.ic_profile)
                .into(image)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}