package gorda.driver.repositories

import com.google.android.gms.tasks.Task
import com.google.firebase.database.DataSnapshot
import gorda.driver.services.firebase.Database

object SettingsRepository {
    fun getRideFees(): Task<DataSnapshot> {
        return Database.dbRideFees().get()
    }
}
