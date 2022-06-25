package gorda.driver.models

import gorda.driver.interfaces.LocType
import gorda.driver.interfaces.ServiceInterface
import gorda.driver.interfaces.ServiceMetadata
import java.io.Serializable

class Service : ServiceInterface, Serializable {
    companion object {
        const val STATUS_COMPLETED = "completed"
        const val TAG = "gorda.driver.models.Service"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_PENDING = "pending"
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
}