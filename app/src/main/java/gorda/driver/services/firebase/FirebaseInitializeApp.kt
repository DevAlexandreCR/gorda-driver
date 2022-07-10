package gorda.driver.services.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import gorda.driver.BuildConfig

object FirebaseInitializeApp {
    val auth: FirebaseAuth = FirebaseAuth.getInstance().run {
        if (BuildConfig.FIREBASE_USE_EMULATORS == "true") this.useEmulator("10.0.2.2", 9099)

        this
    }
    val database: FirebaseDatabase = FirebaseDatabase.getInstance().run {
        if (BuildConfig.FIREBASE_USE_EMULATORS == "true") this.useEmulator("10.0.2.2", 9000)

        this
    }

    val storage: FirebaseStorage = FirebaseStorage.getInstance().run {
        if (BuildConfig.FIREBASE_USE_EMULATORS == "true") this.useEmulator("10.0.2.2", 9199)

        this
    }
}