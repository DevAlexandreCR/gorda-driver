package gorda.driver.ui.history

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import gorda.driver.R
import gorda.driver.models.Service
import gorda.driver.utils.DateHelper
import gorda.driver.utils.StringHelper

class ServiceDialogFragment(private val service: Service): DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val builder = AlertDialog.Builder(requireContext())
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.service_history_dialog, null)

        val createdAt: TextView = view.findViewById(R.id.dialog_created_at)
        val name: TextView = view.findViewById(R.id.dialog_name)
        val place: TextView = view.findViewById(R.id.dialog_place)
        val price: TextView = view.findViewById(R.id.dialog_price)
        val status: TextView = view.findViewById(R.id.dialog_status)
        val comment: TextView = view.findViewById(R.id.dialog_comment)

        createdAt.text = DateHelper.formatDateFromSeconds(service.created_at)
        name.text = service.name
        place.text = service.start_loc.name
        price.text = view.context.getString(R.string.AmountCurrency, service.amount.toString())
        status.text = StringHelper.formatStatus(service.status, view.context)
        comment.text = service.comment

        builder.setView(view)
            .setPositiveButton("OK") { dialog, _ ->
                dialog.dismiss()
            }

        return builder.create()
    }
}