package gorda.driver.ui.profile

import android.app.Dialog
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
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import gorda.driver.R
import gorda.driver.databinding.FragmentProfileBinding
import gorda.driver.models.Driver
import gorda.driver.services.firebase.Auth
import gorda.driver.ui.MainViewModel
import gorda.driver.utils.NumberHelper

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private lateinit var textName: TextView
    private lateinit var textEmail: TextView
    private lateinit var textPhone: TextView
    private lateinit var textPlate: TextView
    private lateinit var textBalance: TextView
    private lateinit var image: ImageView
    private lateinit var btnLogout: MaterialButton
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
        textBalance = binding.profileBalance
        image = binding.imageProfile
        btnLogout = binding.btnLogout

        // Setup logout button click listener
        btnLogout.setOnClickListener {
            showLogoutConfirmationDialog()
        }

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
        textBalance.text = getString(R.string.driver_balance, NumberHelper.toCurrency(driver.balance))
        driver.photoUrl.let { url ->
            Glide
                .with(this)
                .load(url)
                .placeholder(R.mipmap.ic_profile)
                .into(image)
        }
    }

    private fun showLogoutConfirmationDialog() {
        val dialogView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_logout_confirmation, null)
        val dialog = Dialog(requireContext())

        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(true)

        val btnCancel = dialogView.findViewById<MaterialButton>(R.id.btnCancel)
        val btnLogoutConfirm = dialogView.findViewById<MaterialButton>(R.id.btnLogoutConfirm)
        val progressIndicator = dialogView.findViewById<com.google.android.material.progressindicator.CircularProgressIndicator>(R.id.progressIndicator)

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnLogoutConfirm.setOnClickListener {
            // Show loading and disable buttons
            progressIndicator.visibility = View.VISIBLE
            btnCancel.isEnabled = false
            btnLogoutConfirm.isEnabled = false

            Auth.logOut(requireContext())
                .addOnCompleteListener { task ->
                    // Hide loading and re-enable buttons
                    progressIndicator.visibility = View.GONE
                    btnCancel.isEnabled = true
                    btnLogoutConfirm.isEnabled = true

                    if (task.isSuccessful) {
                        dialog.dismiss()
                        // Logout successful - the auth state listener in MainActivity will handle navigation
                    } else {
                        // Handle logout error - show error in the same dialog or create a new one
                        showLogoutErrorDialog()
                        dialog.dismiss()
                    }
                }
        }

        dialog.show()
    }

    private fun showLogoutErrorDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.logout_error_title)
            .setMessage(R.string.logout_error_message)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }
            .show()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}