package gorda.driver.repositories

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.getValue
import gorda.driver.models.Service
import gorda.driver.services.firebase.Database

object ServiceRepository {
    fun getPending(listener: (serviceList: MutableList<Service>) -> Unit): Unit {
        Database.dbServices().orderByChild("status").equalTo(Service.STATUS_PENDING).limitToLast(100)
            .addValueEventListener(object: ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val list: MutableList<Service> = mutableListOf()

                    if (snapshot.hasChildren()) {
                        snapshot.children.forEach { dataSnapshot ->
                            dataSnapshot.getValue<Service>()?.let { service ->
                                list.add(service)
                            }
                        }
                    }

                    listener(list)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e(this.javaClass.toString(), error.message)
                }
            })
    }
}
