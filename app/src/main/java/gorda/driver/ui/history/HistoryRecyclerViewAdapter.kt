package gorda.driver.ui.history

import androidx.recyclerview.widget.RecyclerView
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import gorda.driver.R

import gorda.driver.ui.history.placeholder.PlaceholderContent.PlaceholderItem
import gorda.driver.databinding.FragmentHistoryBinding

class HistoryRecyclerViewAdapter(
    private val values: List<PlaceholderItem>
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
        holder.idView.text = item.id
        holder.place.text = item.place
        holder.time.text = item.time
        holder.status.text = item.status
        holder.price.text = holder.itemView.context.getString(R.string.AmountCurrency, item.price)
    }

    override fun getItemCount(): Int = values.size

    inner class ViewHolder(binding: FragmentHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val idView: TextView = binding.itemNumber
        val place: TextView = binding.place
        val time: TextView = binding.time
        val status: TextView = binding.status
        val price: TextView = binding.price
    }

}