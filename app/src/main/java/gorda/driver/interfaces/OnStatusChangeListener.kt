package gorda.driver.interfaces

import com.google.firebase.database.DatabaseError

interface OnStatusChangeListener {
    fun onChange(status: String): Unit

    fun onFailure(error: DatabaseError): Unit
}