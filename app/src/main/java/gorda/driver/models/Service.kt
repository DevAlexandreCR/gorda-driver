package gorda.driver.models

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.ValueEventListener
import com.google.gson.annotations.SerializedName
import gorda.driver.interfaces.LocType
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.repositories.ServiceRepository
import java.io.Serializable
import java.util.Date

@IgnoreExtraProperties
data class Service(
    @SerializedName("id") var id: String = "",
    @SerializedName("name") var name: String = "",
    @SerializedName("status") var status: String = STATUS_PENDING,
    @SerializedName("start_loc") var start_loc: LocType = LocType(),
    @SerializedName("end_loc") var end_loc: LocType? = null,
    @SerializedName("phone") var phone: String = "",
    @SerializedName("comment") var comment: String? = null,
    @SerializedName("driver_id") var driver_id: String? = null,
    @SerializedName("client_id") var client_id: String? = null,
    @SerializedName("wp_client_id") var wp_client_id: String? = null,
    @SerializedName("created_at") var created_at: Long = 0,
    @SerializedName("metadata") var metadata: ServiceMetadata = ServiceMetadata()
) : Serializable {
    companion object {
        const val CREATED_AT = "created_at"
        const val STATUS_CANCELED = "canceled"
        const val STATUS_TERMINATED = "terminated"
        const val TAG = "gorda.driver.models.Service"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_PENDING = "pending"
        const val STATUS = "status"
        const val APPLICANTS = "applicants"
        const val DRIVER_ID = "driver_id"
        const val ID = "id"
    }

    fun updateMetadata(): Task<Void> {
        return ServiceRepository.updateMetadata(this.id, this.metadata, this.status)
    }

    fun terminate(route: String, distance: Int, fee: Int, multiplier: Double): Task<Void> {
        val meta = this.metadata
        meta.end_trip_at = Date().time / 1000
        meta.route = route
        meta.trip_distance = distance
        meta.trip_fee = fee
        meta.trip_multiplier = multiplier
        return ServiceRepository.updateMetadata(this.id, meta, STATUS_TERMINATED)
    }

    fun addApplicant(driver: Driver, distance: Int, time: Int, connection: String? = null): Task<Void> {
        return ServiceRepository.addApplicant(this.id, driver.id, distance, time, connection)
    }

    fun cancelApplicant(driver: Driver): Task<Void> {
        return ServiceRepository.cancelApply(this.id, driver.id)
    }

    fun onStatusChange(listener: ValueEventListener) {
        ServiceRepository.onStatusChange(this.id, listener)
    }

    fun getStatusReference(): DatabaseReference {
        return ServiceRepository.getStatusReference(this.id)
    }

    fun isInProgress(): Boolean {
        return this.status == STATUS_IN_PROGRESS
    }
}