package gorda.go.repositories

import com.google.android.gms.tasks.Task
import gorda.go.services.firebase.Database

object TokenRepository {
    fun setCurrentToken(id: String, token: String): Task<Void> {
        return Database.dbTokens().child(id).setValue(token)
    }
}
