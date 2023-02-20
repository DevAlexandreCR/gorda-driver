package gorda.driver.ui.service

import android.content.Context
import android.location.Location
import android.os.SystemClock
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.Chronometer
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import gorda.driver.R
import gorda.driver.interfaces.LocType
import gorda.driver.maps.Map
import gorda.driver.models.Service
import java.util.*

class ServiceAdapter(
    private val context: Context,
    private val showMap: (location: LocType) -> Unit,
    private val apply: (service: Service, location: LocType) -> Unit
) :
    ListAdapter<Service, ServiceAdapter.ViewHolder>(ServiceDiffCallback) {

    var lastLocation: Location? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textAddress: TextView
        val textName: TextView
        val textDis: TextView
        val textComment: TextView
        val serviceTimer: Chronometer
        val btnShowMap: Button
        val btnApply: Button

        init {
            textAddress = view.findViewById(R.id.service_address)
            textDis = view.findViewById(R.id.textDis)
            textName = view.findViewById(R.id.text_user_name)
            textComment = view.findViewById(R.id.text_comment)
            serviceTimer = view.findViewById(R.id.service_timer)
            btnShowMap = view.findViewById(R.id.btn_show_map)
            btnApply = view.findViewById(R.id.btn_apply)
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
        holder.btnApply.setOnClickListener {
            apply(getItem(position), getItem(position).start_loc)
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