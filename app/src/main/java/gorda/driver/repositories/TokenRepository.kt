package gorda.driver.repositories

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import gorda.driver.interfaces.ServiceMetadata
import gorda.driver.models.Service
import gorda.driver.services.firebase.Database
import gorda.driver.ui.service.ServicesEventListener
import java.io.Serializable

object TokenRepository {
    fun setCurrentToken(id: String, token: String): Task<Void> {
        return Database.dbTokens().child(id).setValue(token)
    }
}
