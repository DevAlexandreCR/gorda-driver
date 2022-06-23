package gorda.driver.ui.service

import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.R
import gorda.driver.interfaces.LocType
import gorda.driver.models.Service
import java.util.*

class ServiceAdapter(private val showMap: (location: LocType) -> Unit) :
    ListAdapter<Service, ServiceAdapter.ViewHolder>(ServiceDiffCallback) {

    var lastLocation: Location? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textAddress: TextView
        val textLocInfo: TextView
        val btnShowMap: Button

        init {
            textAddress = view.findViewById(R.id.service_address)
            textLocInfo = view.findViewById(R.id.location_info)
            btnShowMap = view.findViewById(R.id.btn_show_map)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.services_row, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textAddress.text = getItem(position).start_loc.name
        holder.btnShowMap.setOnClickListener {
            showMap(getItem(position).start_loc)
        }
        lastLocation?.let {
            // TODO: Show time incrementing each second
            val service = getItem(position)
            val time = (Date().time / 1000) - service.created_at
            holder.textLocInfo.text = (time / 60).toString() + " m"
        }
    }

    object ServiceDiffCallback : DiffUtil.ItemCallback<Service>() {
        override fun areItemsTheSame(oldItem: Service, newItem: Service): Boolean {
            return oldItem == newItem
        }

        override fun areContentsTheSame(oldItem: Service, newItem: Service): Boolean {
            return oldItem.id == newItem.id
        }
    }
}