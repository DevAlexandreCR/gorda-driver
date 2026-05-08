package gorda.driver.ui.service

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import gorda.driver.repositories.ServiceObservationResult

class ServiceEventListener(
    private val listener: (result: ServiceObservationResult) -> Unit
) : ValueEventListener {

    var reference: DatabaseReference? = null
    private var onClosed: (() -> Unit)? = null
    private var closed = false

    override fun onDataChange(snapshot: DataSnapshot) {
        val result = ServiceObservationResult.fromSnapshot(snapshot)
        listener(result)

        if (result !is ServiceObservationResult.Active) {
            closeListener()
        }
    }

    fun setRef(ref: DatabaseReference) {
        closed = false
        this.reference = ref
    }

    fun setOnClosed(callback: (() -> Unit)?) {
        onClosed = callback
    }

    override fun onCancelled(error: DatabaseError) {
        Log.e(this.javaClass.toString(), error.message)
        closeListener()
    }

    fun setMissing() {
        listener(ServiceObservationResult.Missing)
        closeListener()
    }

    private fun closeListener() {
        if (closed) {
            return
        }

        closed = true
        reference?.removeEventListener(this)
        onClosed?.invoke()
    }
}
