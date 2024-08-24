package gorda.godriver.services.firebase

import com.google.firebase.storage.FirebaseStorage

object Storage {
    private val storage: FirebaseStorage = FirebaseInitializeApp.storage
}