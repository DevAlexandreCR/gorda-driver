package gorda.driver.services.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import gorda.driver.BuildConfig

object FirebaseInitializeApp {
    val auth: FirebaseAuth = FirebaseAuth.getInstance().run {
        if (BuildConfig.FIREBASE_USE_EMULATORS == "true")
            this.useEmulator(BuildConfig.FIREBASE_AUTH_HOST, BuildConfig.FIREBASE_AUTH_PORT.toInt())
        this
    }
    val database: FirebaseDatabase = FirebaseDatabase.getInstance().run {
        if (BuildConfig.FIREBASE_USE_EMULATORS == "true")
            this.useEmulator(BuildConfig.FIREBASE_DATABASE_HOST, BuildConfig.FIREBASE_DATABASE_PORT.toInt())
        this
    }

    val storage: FirebaseStorage = FirebaseStorage.getInstance().run {
        if (BuildConfig.FIREBASE_USE_EMULATORS == "true")
            this.useEmulator(BuildConfig.FIREBASE_STORAGE_HOST, BuildConfig.FIREBASE_STORAGE_PORT.toInt())
        this
    }
}