package gorda.driver.ui.service

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.models.Service

class NextServiceListener(private val listener: (service: Service?) -> Unit): ValueEventListener {

    override fun onDataChange(snapshot: DataSnapshot) {
        if (snapshot.exists()) {
            snapshot.getValue<Service>()?.let { service ->
                if (service.status === Service.STATUS_TERMINATED || service.status === Service.STATUS_CANCELED) {
                    this.listener(null)
                }
                this.listener(service)
            }
        }
    }

    override fun onCancelled(error: DatabaseError) {
        Log.e(this.javaClass.toString(), error.message)
    }
}