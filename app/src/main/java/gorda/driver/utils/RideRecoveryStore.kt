package gorda.driver.utils

import android.content.SharedPreferences
import androidx.core.content.edit
import com.google.gson.Gson
import gorda.driver.background.FeesService
import gorda.driver.interfaces.RideFees
import gorda.driver.ui.service.current.BottomSheetPresentationSnapshot
import gorda.driver.ui.service.current.CurrentServiceUiSnapshot
import gorda.driver.ui.service.current.PendingServiceActionSnapshot

object RideRecoveryStore {

    data class Snapshot(
        val serviceId: String,
        val startTime: Long,
        val multiplier: Double?,
        val pointsJson: String?,
        val totalDistance: Double?,
        val rideFees: RideFees?
    )

    private val gson = Gson()

    fun getTrackedServiceId(preferences: SharedPreferences): String? {
        return preferences.getString(Constants.RIDE_RECOVERY_SERVICE_ID, null)?.takeIf { it.isNotBlank() }
    }

    fun attachToService(preferences: SharedPreferences, serviceId: String) {
        preferences.edit(commit = true) {
            putString(Constants.RIDE_RECOVERY_SERVICE_ID, serviceId)
        }
    }

    fun clearIfStale(preferences: SharedPreferences, currentServiceId: String): Boolean {
        val storedServiceId = getTrackedServiceId(preferences)
        if (!RideRecoveryPolicy.shouldClearStaleRecovery(storedServiceId, currentServiceId)) {
            return false
        }

        clear(preferences)
        return true
    }

    fun hasRecoverableSession(preferences: SharedPreferences, serviceId: String): Boolean {
        val snapshot = getSnapshot(preferences) ?: return false
        return snapshot.serviceId == serviceId && snapshot.startTime > 0L
    }

    fun getSnapshot(preferences: SharedPreferences): Snapshot? {
        val serviceId = getTrackedServiceId(preferences) ?: return null
        val startTime = preferences.getLong(Constants.START_TIME, 0L)
        val multiplier = preferences.getString(Constants.MULTIPLIER, null)?.toDoubleOrNull()
        val pointsJson = preferences.getString(Constants.POINTS, null)
        val totalDistance = preferences.getString(FeesService.TOTAL_DISTANCE, null)?.toDoubleOrNull()
        val rideFees = getRideFeesSnapshot(preferences)

        return Snapshot(
            serviceId = serviceId,
            startTime = startTime,
            multiplier = multiplier,
            pointsJson = pointsJson,
            totalDistance = totalDistance,
            rideFees = rideFees
        )
    }

    fun persistStart(
        preferences: SharedPreferences,
        serviceId: String,
        startTime: Long,
        multiplier: Double
    ) {
        preferences.edit(commit = true) {
            putString(Constants.RIDE_RECOVERY_SERVICE_ID, serviceId)
            putLong(Constants.START_TIME, startTime)
            putString(Constants.MULTIPLIER, multiplier.toString())
        }
    }

    fun persistMultiplier(preferences: SharedPreferences, multiplier: Double) {
        preferences.edit(commit = true) {
            putString(Constants.MULTIPLIER, multiplier.toString())
        }
    }

    fun persistPoints(preferences: SharedPreferences, pointsJson: String) {
        preferences.edit(commit = true) {
            putString(Constants.POINTS, pointsJson)
        }
    }

    fun persistTotalDistance(preferences: SharedPreferences, totalDistance: Double) {
        preferences.edit(commit = true) {
            putString(FeesService.TOTAL_DISTANCE, totalDistance.toString())
        }
    }

    fun persistRideFeesSnapshot(preferences: SharedPreferences, rideFees: RideFees) {
        if (!RideRecoveryPolicy.isValidRideFeesSnapshot(rideFees)) {
            return
        }

        preferences.edit(commit = true) {
            putString(FeesService.CURRENT_FEES, gson.toJson(rideFees))
        }
    }

    fun getRideFeesSnapshot(preferences: SharedPreferences): RideFees? {
        val feesJson = preferences.getString(FeesService.CURRENT_FEES, null) ?: return null

        return try {
            gson.fromJson(feesJson, RideFees::class.java)
        } catch (_: Exception) {
            null
        }
    }

    fun getCurrentServiceUiSnapshot(preferences: SharedPreferences): CurrentServiceUiSnapshot? {
        return getSnapshot(
            preferences = preferences,
            key = Constants.CURRENT_SERVICE_UI_SNAPSHOT,
            clazz = CurrentServiceUiSnapshot::class.java
        )
    }

    fun persistCurrentServiceUiSnapshot(
        preferences: SharedPreferences,
        snapshot: CurrentServiceUiSnapshot
    ) {
        persistSnapshot(preferences, Constants.CURRENT_SERVICE_UI_SNAPSHOT, snapshot)
    }

    fun clearCurrentServiceUiSnapshot(preferences: SharedPreferences) {
        preferences.edit(commit = true) {
            remove(Constants.CURRENT_SERVICE_UI_SNAPSHOT)
        }
    }

    fun getPendingServiceActionSnapshot(preferences: SharedPreferences): PendingServiceActionSnapshot? {
        return getSnapshot(
            preferences = preferences,
            key = Constants.PENDING_SERVICE_ACTION_SNAPSHOT,
            clazz = PendingServiceActionSnapshot::class.java
        )
    }

    fun persistPendingServiceActionSnapshot(
        preferences: SharedPreferences,
        snapshot: PendingServiceActionSnapshot
    ) {
        persistSnapshot(preferences, Constants.PENDING_SERVICE_ACTION_SNAPSHOT, snapshot)
    }

    fun clearPendingServiceActionSnapshot(preferences: SharedPreferences) {
        preferences.edit(commit = true) {
            remove(Constants.PENDING_SERVICE_ACTION_SNAPSHOT)
        }
    }

    fun getBottomSheetPresentationSnapshot(preferences: SharedPreferences): BottomSheetPresentationSnapshot? {
        return getSnapshot(
            preferences = preferences,
            key = Constants.CURRENT_SERVICE_BOTTOM_SHEET_SNAPSHOT,
            clazz = BottomSheetPresentationSnapshot::class.java
        )
    }

    fun persistBottomSheetPresentationSnapshot(
        preferences: SharedPreferences,
        snapshot: BottomSheetPresentationSnapshot
    ) {
        persistSnapshot(preferences, Constants.CURRENT_SERVICE_BOTTOM_SHEET_SNAPSHOT, snapshot)
    }

    fun clearBottomSheetPresentationSnapshot(preferences: SharedPreferences) {
        preferences.edit(commit = true) {
            remove(Constants.CURRENT_SERVICE_BOTTOM_SHEET_SNAPSHOT)
        }
    }

    fun clear(preferences: SharedPreferences) {
        preferences.edit(commit = true) {
            clearRideSessionEntries(this)
            remove(Constants.CURRENT_SERVICE_UI_SNAPSHOT)
            remove(Constants.PENDING_SERVICE_ACTION_SNAPSHOT)
            remove(Constants.CURRENT_SERVICE_BOTTOM_SHEET_SNAPSHOT)
        }
    }

    fun clearRideSession(preferences: SharedPreferences) {
        preferences.edit(commit = true) {
            clearRideSessionEntries(this)
        }
    }

    private fun <T> getSnapshot(
        preferences: SharedPreferences,
        key: String,
        clazz: Class<T>
    ): T? {
        val snapshotJson = preferences.getString(key, null) ?: return null
        return try {
            gson.fromJson(snapshotJson, clazz)
        } catch (_: Exception) {
            null
        }
    }

    private fun persistSnapshot(
        preferences: SharedPreferences,
        key: String,
        snapshot: Any
    ) {
        preferences.edit(commit = true) {
            putString(key, gson.toJson(snapshot))
        }
    }

    private fun clearRideSessionEntries(editor: SharedPreferences.Editor) {
        editor.remove(Constants.RIDE_RECOVERY_SERVICE_ID)
        editor.remove(Constants.CURRENT_SERVICE_ID)
        editor.remove(Constants.START_TIME)
        editor.remove(Constants.MULTIPLIER)
        editor.remove(Constants.POINTS)
        editor.remove(FeesService.TOTAL_DISTANCE)
    }
}
