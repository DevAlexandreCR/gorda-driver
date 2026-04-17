package gorda.driver.services.firebase

import android.content.Context
import android.content.Intent
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuth.AuthStateListener
import gorda.driver.R
import gorda.driver.repositories.TokenRepository

object Auth {
    private val auth: FirebaseAuth = FirebaseInitializeApp.auth

    fun getCurrentUserUUID(): String? {
        return auth.currentUser?.uid
    }

    fun reloadUser(): Task<Void> {
        return if (auth.currentUser != null) {
            auth.currentUser!!.reload()
        } else {
            throw IllegalStateException("User is not logged in")
        }
    }

    fun onAuthChanges(listener: (uuid: String?) -> Unit): AuthStateListener {
        val authStateListener = AuthStateListener { firebaseAuth ->
            listener(firebaseAuth.currentUser?.uid)
        }
        auth.addAuthStateListener(authStateListener)
        return authStateListener
    }

    fun removeAuthChanges(listener: AuthStateListener) {
        auth.removeAuthStateListener(listener)
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
        val currentUserId = getCurrentUserUUID()
        if (currentUserId == null) {
            return AuthUI.getInstance().signOut(context)
        }

        return TokenRepository.deleteCurrentToken(currentUserId)
            .continueWithTask {
                AuthUI.getInstance().signOut(context)
            }
    }
}
