package gorda.driver.services.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase

object FirebaseInitializeApp {
    fun initializeApp(): Unit {
        FirebaseAuth.getInstance().useEmulator("10.0.2.2", 9099)
        FirebaseDatabase.getInstance().useEmulator("10.0.2.2", 9000)
    }
}