package gorda.driver.ui.service

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.models.Service

class ServiceEventListener(private val listener: (service: Service?) -> Unit): ValueEventListener {

    var reference: DatabaseReference? = null
    override fun onDataChange(snapshot: DataSnapshot) {
        if (snapshot.exists()) {
            snapshot.getValue<Service>()?.let { service ->
                if (service.status == Service.STATUS_TERMINATED || service.status == Service.STATUS_CANCELED) {
                    this.listener(null)
                    reference?.removeEventListener(this)
                } else {
                    this.listener(service)
                }
            }
        } else {
            this.listener(null)
            reference?.removeEventListener(this)
        }
    }

    fun setRef(ref: DatabaseReference) {
        this.reference = ref
    }

    override fun onCancelled(error: DatabaseError) {
        Log.e(this.javaClass.toString(), error.message)
        reference?.removeEventListener(this)
    }

    fun setNull() {
        this.listener(null)
        reference?.removeEventListener(this)
    }
}