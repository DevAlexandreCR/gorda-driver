package gorda.driver.models

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import gorda.driver.interfaces.LocType
import gorda.driver.interfaces.OnStatusChangeListener
import gorda.driver.interfaces.ServiceInterface
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.repositories.ServiceRepository
import java.io.Serializable

class Service : ServiceInterface, Serializable {
    companion object {
        const val STATUS_CANCELED = "canceled"
        const val CANCEL_APPLY = "cancel"
        const val STATUS_COMPLETED = "completed"
        const val TAG = "gorda.driver.models.Service"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_PENDING = "pending"
        const val STATUS = "status"
        const val APPLICANTS = "applicants"
    }

    override var id: String? = null
    override lateinit var status: String
    override lateinit var start_loc: LocType
    override var end_loc: LocType? = null
    override lateinit var phone: String
    override lateinit var name: String
    override var comment: String? = null
    override var amount: Int? = null
    override var driver_id: String? = null
    override var client_id: String? = null
    override var created_at: Long = 0
    override var metadata = ServiceMetadata()

    fun update(): Task<Void> {
        return ServiceRepository.update(this)
    }

    fun addApplicant(driver: Driver, distance: Int, time: Int): Task<Void> {
        return ServiceRepository.addApplicant(this.id!!, driver.id!!, distance, time)
    }

    fun cancelApplicant(driver: Driver): Task<Void> {
        return ServiceRepository.cancelApply(this.id!!, driver.id!!)
    }

    fun onStatusChange(listener: ValueEventListener) {
        ServiceRepository.onStatusChange(this.id!!, listener)
    }

    fun getStatusReference(): DatabaseReference {
        return ServiceRepository.getStatusReference(this.id!!)
    }
}