package gorda.driver.ui.history

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.databinding.HistoryLayoutBinding
import gorda.driver.models.Service
import gorda.driver.utils.DateHelper
import gorda.driver.utils.StringHelper

class HistoryRecyclerViewAdapter(
    private val values: MutableList<Service>,
    private val onItemClick: (service: Service) -> Unit
) : RecyclerView.Adapter<HistoryRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            HistoryLayoutBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        holder.createdAt.text = DateHelper.formatTimeFromSeconds(item.created_at)
        holder.place.text = item.start_loc.name
        holder.status.text = StringHelper.formatStatus(item.status, holder.itemView.context)
        holder.itemView.setOnClickListener {
            onItemClick(item)
        }
    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(binding: HistoryLayoutBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val createdAt: TextView = binding.time
        val place: TextView = binding.place
        val status: TextView = binding.status
    }

}