package gorda.driver.ui.service

import android.app.AlertDialog
import android.app.Dialog
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.net.toUri
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import gorda.driver.R
import gorda.driver.models.Service

class ConnectionServiceDialog : DialogFragment() {

    companion object {
        const val TAG = "ConnectionServiceDialog"
        private const val ARG_SERVICE = "arg_service"

        fun newInstance(service: Service): ConnectionServiceDialog {
            return ConnectionServiceDialog().apply {
                arguments = bundleOf(ARG_SERVICE to service)
            }
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val service = requireService()
        val builder = AlertDialog.Builder(requireContext())
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.connection_service_layout, null)

        val textName = view.findViewById<TextView>(R.id.next_service_name)
        val textPhone = view.findViewById<TextView>(R.id.next_service_phone)
        val textPickup = view.findViewById<TextView>(R.id.next_service_pickup)
        val phoneRow = view.findViewById<LinearLayout>(R.id.next_service_phone_row)
        val callButton = view.findViewById<TextView>(R.id.next_service_call_button)

        textName.text = service.name
        textPhone.text = service.phone
        textPickup.text = service.start_loc.name

        val openDialer = {
            val intent = Intent(Intent.ACTION_DIAL, ("tel:" + service.phone).toUri())
            startActivity(intent)
        }

        textPhone.setOnClickListener { openDialer() }
        phoneRow.setOnClickListener { openDialer() }
        callButton.setOnClickListener { openDialer() }

        builder.setView(view)
            .setPositiveButton(R.string.ok) { dialog, _ ->
                dialog.dismiss()
            }

        return builder.create()
    }

    private fun requireService(): Service {
        val bundle = requireArguments()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            bundle.getSerializable(ARG_SERVICE, Service::class.java)
        } else {
            @Suppress("DEPRECATION")
            bundle.getSerializable(ARG_SERVICE) as? Service
        } ?: error("ConnectionServiceDialog requires a Service argument")
    }

}
