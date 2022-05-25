package gorda.driver.services.firebase

import android.content.Intent
import com.firebase.ui.auth.AuthUI
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import gorda.driver.R

object Auth {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    fun getCurrentUser(): FirebaseUser? {
        return auth.currentUser
    }

    fun launchLogin(): Intent {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().setAllowNewAccounts(false).build()
        )

        return AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setLogo(R.drawable.ic_launcher_foreground)
            .setAvailableProviders(providers)
            .setIsSmartLockEnabled(false)
            .build()
    }

    fun logOut() {
        auth.signOut()
    }
}