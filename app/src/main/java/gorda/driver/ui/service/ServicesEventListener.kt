package gorda.driver.ui.service

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.models.Service

class ServicesEventListener(driverID: String,
                            listener: (serviceList: MutableList<Service>) -> Unit) :
    ValueEventListener {

    private val listener: (serviceList: MutableList<Service>) -> Unit
    private val driverID: String

    init {
        this.listener = listener
        this.driverID = driverID
    }
    override fun onDataChange(snapshot: DataSnapshot) {
        val list: MutableList<Service> = mutableListOf()

        if (snapshot.hasChildren()) {
            Log.d("***** services", snapshot.children.toString())
            snapshot.children.forEach { dataSnapshot ->
                dataSnapshot.getValue<Service>()?.let { service ->
                    list.add(service)
                }
            }
        }

        this.listener(list)
    }

    override fun onCancelled(error: DatabaseError) {
        Log.e(this.javaClass.toString(), error.message)
    }
}