package gorda.driver.services.firebase

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.storage.FirebaseStorage
import gorda.driver.BuildConfig

object FirebaseInitializeApp {
    private fun buildConfigValue(name: String): String? {
        return runCatching {
            BuildConfig::class.java.getField(name).get(null) as? String
        }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun emulatorHost(): String {
        return buildConfigValue("FIREBASE_EMULATOR_HOST")
            ?: buildConfigValue("FIREBASE_AUTH_HOST")
            ?: buildConfigValue("FIREBASE_DATABASE_HOST")
            ?: buildConfigValue("FIREBASE_STORAGE_HOST")
            ?: "10.0.2.2"
    }

    val auth: FirebaseAuth = FirebaseAuth.getInstance().run {
        if (BuildConfig.FIREBASE_USE_EMULATORS == "true")
            this.useEmulator(emulatorHost(), BuildConfig.FIREBASE_AUTH_PORT.toInt())
        this
    }
    val database: FirebaseDatabase = FirebaseDatabase.getInstance().run {
        this.setPersistenceEnabled(true)
        if (BuildConfig.FIREBASE_USE_EMULATORS == "true")
            this.useEmulator(emulatorHost(), BuildConfig.FIREBASE_DATABASE_PORT.toInt())
        this
    }

    val storage: FirebaseStorage = FirebaseStorage.getInstance().run {
        if (BuildConfig.FIREBASE_USE_EMULATORS == "true")
            this.useEmulator(emulatorHost(), BuildConfig.FIREBASE_STORAGE_PORT.toInt())
        this
    }

    val messaging: FirebaseMessaging = FirebaseMessaging.getInstance()

    fun usesEmulators(): Boolean {
        return BuildConfig.FIREBASE_USE_EMULATORS == "true"
    }

    fun databaseHostPort(): String {
        return "${emulatorHost()}:${BuildConfig.FIREBASE_DATABASE_PORT}"
    }
}
