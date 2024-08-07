package gorda.driver.ui.service

import android.app.AlertDialog
import android.app.Dialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import gorda.driver.R
import gorda.driver.models.Service
import java.util.Locale

class ConnectionServiceDialog(private val service: Service): DialogFragment() {

    private lateinit var imgBtnMaps: ImageButton
    private lateinit var imgButtonWaze: ImageButton
    private lateinit var textName: TextView
    private lateinit var textPhone: TextView
    private lateinit var textAddress: TextView
    private lateinit var textComment: TextView
    private lateinit var btnStatus: Button

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.connection_service_layout, null)

        imgBtnMaps = view.findViewById(R.id.img_btn_maps)
        imgButtonWaze = view.findViewById(R.id.img_btn_waze)
        textName = view.findViewById(R.id.current_service_name)
        textPhone = view.findViewById(R.id.current_phone)
        textAddress = view.findViewById(R.id.current_address)
        textComment = view.findViewById(R.id.service_comment)
        btnStatus = view.findViewById(R.id.btn_service_status)

        textName.text = service.name
        textPhone.text = service.phone
        textAddress.text = service.start_loc.name
        textComment.text = service.comment
        btnStatus.visibility = View.GONE
        textPhone.setOnClickListener {
            val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + service.phone))
            startActivity(intent)
        }
        imgBtnMaps.setOnClickListener {
            val uri: String = String.format(
                Locale.ENGLISH, "google.navigation:q=%f,%f",
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
            val uri: String = String.format(
                Locale.ENGLISH, "waze://?ll=%f,%f&navigate=yes",
                service.start_loc.lat, service.start_loc.lng)
            val wazeIntent = Intent(Intent.ACTION_VIEW, Uri.parse(uri))
            try {
                startActivity(wazeIntent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(requireContext(), R.string.not_waze, Toast.LENGTH_SHORT).show()
            }
        }

        builder.setView(view)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }

        return builder.create()
    }

}