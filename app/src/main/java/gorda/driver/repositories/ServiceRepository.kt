package gorda.driver.repositories

import gorda.driver.ui.service.ServicesEventListener
import gorda.driver.models.Service
import gorda.driver.services.firebase.Database

object ServiceRepository {

    private var serviceEventListener: ServicesEventListener? = null

    fun getPending(listener: (serviceList: MutableList<Service>) -> Unit): Unit {
        serviceEventListener = ServicesEventListener(listener)
        Database.dbServices().orderByChild("status").equalTo(Service.STATUS_PENDING).limitToLast(100)
            .addValueEventListener(serviceEventListener!!)
    }

    fun stopListenServices() {
        serviceEventListener?.let { listener ->
            Database.dbServices().removeEventListener(listener)
        }
    }
}
