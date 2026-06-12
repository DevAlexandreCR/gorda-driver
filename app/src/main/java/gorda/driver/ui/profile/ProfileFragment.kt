package gorda.driver.ui.profile

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.preference.PreferenceManager
import com.bumptech.glide.Glide
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import gorda.driver.R
import gorda.driver.databinding.FragmentProfileBinding
import gorda.driver.models.Driver
import gorda.driver.services.firebase.Auth
import gorda.driver.ui.MainViewModel
import gorda.driver.utils.Constants
import gorda.driver.utils.NumberHelper

class ProfileFragment : Fragment() {

    private var _binding: FragmentProfileBinding? = null
    private lateinit var textName: TextView
    private lateinit var textEmail: TextView
    private lateinit var textPhone: TextView
    private lateinit var textBalance: TextView
    private lateinit var image: ImageView
    private lateinit var btnLogout: MaterialButton
    private lateinit var vehicleRosterContainer: LinearLayout
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
        textBalance = binding.profileBalance
        image = binding.imageProfile
        btnLogout = binding.btnLogout
        vehicleRosterContainer = binding.vehicleRosterContainer

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
            val selectedId = driver.selected_vehicle?.id
                ?: driver.roster.firstOrNull { it.is_selected }?.id
            if (selectedId != null) {
                mainViewModel.setSelectedVehicleId(selectedId)
            }
        }

        viewModel.vehicleSelectInFlight.observe(viewLifecycleOwner) { inFlight ->
            binding.progressIndicator.visibility = if (inFlight) View.VISIBLE else View.GONE
        }

        viewModel.errorMessage.observe(viewLifecycleOwner) { msg ->
            if (msg != null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.vehicle_picker_select_error),
                    Toast.LENGTH_SHORT
                ).show()
                viewModel.clearErrorMessage()
            }
        }

        return root
    }

    private fun setDriver(driver: Driver) {
        textName.text = driver.name
        textEmail.text = driver.email
        textPhone.text = driver.phone
        textBalance.text = getString(R.string.driver_balance, NumberHelper.toCurrency(driver.balance))
        driver.photoUrl.let { url ->
            Glide
                .with(this)
                .load(url)
                .placeholder(R.mipmap.ic_profile)
                .into(image)
        }
        rebuildRoster(driver)
    }

    private fun rebuildRoster(driver: Driver) {
        vehicleRosterContainer.removeAllViews()
        if (driver.roster.isEmpty()) {
            val noVehicle = TextView(requireContext())
            noVehicle.text = getString(R.string.vehicle_picker_no_vehicles)
            noVehicle.setTextColor(resources.getColor(android.R.color.white, null))
            noVehicle.alpha = 0.7f
            vehicleRosterContainer.addView(noVehicle)
            return
        }
        driver.roster.forEach { vehicle ->
            val row = LayoutInflater.from(requireContext())
                .inflate(R.layout.item_vehicle_row, vehicleRosterContainer, false)
            val radio = row.findViewById<RadioButton>(R.id.vehicleRadio)
            val label = row.findViewById<TextView>(R.id.vehicleLabel)
            val displayParts = listOfNotNull(
                vehicle.plate.ifEmpty { null },
                listOfNotNull(vehicle.brand, vehicle.model).joinToString(" ").ifEmpty { null }
            )
            label.text = displayParts.joinToString(" · ")
            radio.isChecked = vehicle.is_selected
            row.setOnClickListener {
                if (!vehicle.is_selected) {
                    viewModel.selectVehicle(vehicle.id)
                }
            }
            vehicleRosterContainer.addView(row)
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
        // Cache selected vehicle id for the connect flow
        val selectedId = viewModel.driver.value?.selected_vehicle?.id
            ?: viewModel.driver.value?.roster?.firstOrNull { it.is_selected }?.id
        if (selectedId != null) {
            PreferenceManager.getDefaultSharedPreferences(requireContext())
                .edit()
                .putString(Constants.DRIVER_SELECTED_VEHICLE_ID, selectedId)
                .apply()
        }
        super.onDestroyView()
        _binding = null
    }
}
