package services.firebase

import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class Auth {
    fun getCurrentUser(): FirebaseUser? {
        return Firebase.auth.currentUser
    }
}