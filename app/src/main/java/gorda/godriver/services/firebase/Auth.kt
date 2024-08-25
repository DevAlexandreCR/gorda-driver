package gorda.godriver.services.firebase

import android.content.Context
import android.content.Intent
import com.firebase.ui.auth.AuthUI
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.FirebaseAuth
import gorda.godriver.R

object Auth {
    private val auth: FirebaseAuth = FirebaseInitializeApp.auth

    fun getCurrentUserUUID(): String? {
        return auth.currentUser?.uid
    }

    fun onAuthChanges(listener: (uuid: String?) -> Unit) {
        auth.addAuthStateListener { p0 ->
            listener(p0.currentUser?.uid)
        }
    }

    fun launchLogin(): Intent {
        val providers = arrayListOf(
            AuthUI.IdpConfig.EmailBuilder().setAllowNewAccounts(false).build(),
        )

        return AuthUI.getInstance()
            .createSignInIntentBuilder()
            .setAvailableProviders(providers)
            .setIsSmartLockEnabled(false)
            .setTosAndPrivacyPolicyUrls(
                "https://www.termsfeed.com/live/696f216f-d044-4a33-b925-9604bb26b823",
                "https://www.termsfeed.com/live/4f59772d-e5cb-49d0-abe2-a29ecf9c022c"
            )
            .setTheme(R.style.AuthUI)
            .setLogo(R.drawable.ic_launcher_foreground)
            .build()
    }

    fun logOut(context: Context): Task<Void> {
        return AuthUI.getInstance().signOut(context)
    }
}