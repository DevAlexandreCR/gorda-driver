package gorda.godriver.ui.service

import android.content.Context
import android.location.Location
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gorda.godriver.R
import gorda.godriver.interfaces.LocType
import gorda.godriver.maps.Map
import gorda.godriver.models.Service
import java.util.*

class ServiceAdapter(
    private val context: Context,
    private val showMap: (location: LocType) -> Unit,
    private val apply: (service: Service, location: LocType) -> Unit
) :
    ListAdapter<Service, ServiceAdapter.ViewHolder>(ServiceDiffCallback) {

    var lastLocation: Location? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textAddress: TextView = view.findViewById(R.id.service_address)
        val textDestination: TextView = view.findViewById(R.id.service_destination)
        val imageB: ImageView = view.findViewById(R.id.imageB)
        val textName: TextView = view.findViewById(R.id.text_user_name)
        val textDis: TextView = view.findViewById(R.id.textDis)
        val textComment: TextView = view.findViewById(R.id.text_comment)
        val serviceTimer: Chronometer = view.findViewById(R.id.service_timer)
        val btnShowMap: Button = view.findViewById(R.id.btn_show_map)
        val btnApply: Button = view.findViewById(R.id.btn_apply)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.services_row, parent, false)

        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.textAddress.text = getItem(position).start_loc.name
        if (getItem(position).end_loc != null) {
            holder.textDestination.text = getItem(position).end_loc!!.name
        } else {
            holder.textDestination.visibility = View.GONE
            holder.imageB.visibility = View.GONE
        }
        holder.btnShowMap.setOnClickListener {
            showMap(getItem(position).start_loc)
        }
        holder.btnApply.setOnClickListener {
            if (position < itemCount) {
                apply(getItem(position), getItem(position).start_loc)
            }
        }
        val service = getItem(position)
        val time = Date().time - (service.created_at * 1000)
        holder.serviceTimer.base = SystemClock.elapsedRealtime() - time
        holder.serviceTimer.start()
        holder.serviceTimer.format = context.resources.getString(R.string.ago) + " %s"
        holder.textName.text = service.name
        holder.textComment.text = service.comment
        holder.textDis.text = lastLocation?.let { Map.distanceToString(Map.calculateDistance(service.start_loc, it))}
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