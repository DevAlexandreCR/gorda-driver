package gorda.driver.ui.history

import android.content.Context
import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import gorda.driver.R
import gorda.driver.databinding.FragmentHistoryBinding
import gorda.driver.models.Service
import java.util.Calendar

class HistoryRecyclerViewAdapter(
    private val values: MutableList<Service>
) : RecyclerView.Adapter<HistoryRecyclerViewAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(
            FragmentHistoryBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = values[position]
        holder.createdAt.text = formatTimeFromSeconds(item.created_at)
        holder.place.text = item.start_loc.name
        holder.status.text = formatStatus(item.status, holder.itemView.context)
    }

    private fun formatStatus(status: String, context: Context): String {
        return if (status == Service.STATUS_TERMINATED) context.getString(R.string.completed)
        else context.getString(R.string.canceled)
    }
    private fun formatTimeFromSeconds(currentTimeInSeconds: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTimeInSeconds * 1000
        return "%02d:%02d:%02d".format(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND)
        )
    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(binding: FragmentHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val createdAt: TextView = binding.time
        val place: TextView = binding.place
        val status: TextView = binding.status
    }

}