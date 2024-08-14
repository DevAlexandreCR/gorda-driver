package gorda.go.repositories

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import gorda.go.services.firebase.Database

object SettingsRepository {
    fun getRideFees(): Task<DataSnapshot> {
        return Database.dbRideFees().get()
    }
}
