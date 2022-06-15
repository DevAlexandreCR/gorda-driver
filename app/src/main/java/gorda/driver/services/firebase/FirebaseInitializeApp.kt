package gorda.driver.services.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage

object FirebaseInitializeApp {
    val auth: FirebaseAuth = FirebaseAuth.getInstance().run {
        this.useEmulator("10.0.2.2", 9099)

        this
    }
    val database: FirebaseDatabase = FirebaseDatabase.getInstance().run {
        this.useEmulator("10.0.2.2", 9000)

        this
    }

    val storage: FirebaseStorage = FirebaseStorage.getInstance().run {
        this.useEmulator("10.0.2.2", 9199)

        this
    }
}