package gorda.driver.activity.ui.service

import android.location.Location
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.R
import gorda.driver.models.Service

class ServiceAdapter :
    ListAdapter<Service, ServiceAdapter.ViewHolder>(ServiceDiffCallback) {

    var lastLocation: Location? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textAddress: TextView
        val textLocInfo: TextView

        init {
            textAddress = view.findViewById(R.id.service_address)
            textLocInfo = view.findViewById(R.id.location_info)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.services_row, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textAddress.text = getItem(position).start_loc.name

        lastLocation?.let { location ->
            holder.textLocInfo.text =
                (getItem(position).start_loc.lat?.minus(location.latitude)).toString() + "m"
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