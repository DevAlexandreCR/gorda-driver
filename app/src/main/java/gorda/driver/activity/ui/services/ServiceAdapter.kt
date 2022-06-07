package gorda.driver.activity.ui.services

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.R
import gorda.driver.models.Service

class ServiceAdapter() :
    ListAdapter<Service, ServiceAdapter.ViewHolder>(ServiceDiffCallback) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView

        init {
            // Define click listener for the ViewHolder's View.
            textView = view.findViewById(R.id.service_address)
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.services_row, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textView.text = getItem(position).start_address
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