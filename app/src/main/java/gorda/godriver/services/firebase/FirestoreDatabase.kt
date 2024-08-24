package gorda.godriver.services.firebase

import com.google.firebase.firestore.CollectionReference

object FirestoreDatabase {

    fun fsServices(): CollectionReference {
        return FirebaseInitializeApp.firestoreDB.collection("services")
    }
}