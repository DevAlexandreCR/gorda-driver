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
import com.google.android.gms.maps.model.LatLng
import gorda.driver.R
import gorda.driver.interfaces.LocType
import gorda.driver.maps.Map
import gorda.driver.models.Service
import java.util.Date

class ServiceAdapter(
    private val context: Context,
    private val showMap: (location: LocType) -> Unit,
    private val apply: (service: Service, location: LocType) -> Unit
) :
    ListAdapter<Service, ServiceAdapter.ViewHolder>(ServiceDiffCallback) {

    var lastLocation: Location? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textAddress: TextView = view.findViewById(R.id.service_address)
        val textName: TextView = view.findViewById(R.id.text_user_name)
        val textDis: TextView = view.findViewById(R.id.textDis)
        val textComment: TextView = view.findViewById(R.id.text_comment)
        val textDestination: TextView = view.findViewById(R.id.text_destination)
        val destinationRow: View = view.findViewById(R.id.destination_row)
        val destinationDivider: View = view.findViewById(R.id.destination_divider)
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
        val destinationName = service.end_loc?.name?.takeIf { it.isNotBlank() }
        if (destinationName != null) {
            holder.destinationRow.visibility = View.VISIBLE
            holder.destinationDivider.visibility = View.VISIBLE
            holder.textDestination.text = destinationName
        } else {
            holder.destinationRow.visibility = View.GONE
            holder.destinationDivider.visibility = View.GONE
            holder.textDestination.text = ""
        }
        val startLoc = Location("last")
        startLoc.latitude = service.start_loc.lat
        startLoc.longitude = service.start_loc.lng
        holder.textDis.text = lastLocation?.let {
            Map.distanceToString(Map.calculateDistance(
                LatLng(service.start_loc.lat, service.start_loc.lng),
                LatLng(it.latitude, it.longitude
                )))
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