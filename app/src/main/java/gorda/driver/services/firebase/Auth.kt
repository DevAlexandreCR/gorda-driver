package gorda.driver.services.firebase

import android.content.Context
import android.content.Intent
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import gorda.driver.R

object Auth {
    private val auth: FirebaseAuth = FirebaseInitializeApp.auth

    fun getCurrentUserUUID(): String? {
        return auth.uid
    }

    fun onAuthChanges(listener: (uuid: String?) -> Unit) {
        auth.addAuthStateListener { p0 -> listener(p0.uid) }
    }

    fun launchLogin(): Intent {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().setAllowNewAccounts(false).build(),
        )

        return AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setIsSmartLockEnabled(false)
            .setTheme(R.style.AuthUI)
            .setLogo(R.drawable.ic_launcher_foreground)
            .build()
    }

    fun logOut(context: Context): Task<Void> {
        return AuthUI.getInstance().signOut(context)
    }
}