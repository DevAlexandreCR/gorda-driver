package gorda.driver.location

import android.content.SharedPreferences
import android.location.Location
import androidx.core.content.edit
import gorda.driver.utils.Constants

object CachedLocationStore {

    data class Snapshot(
        val latitude: Double,
        val longitude: Double,
        val accuracy: Float,
        val capturedAtEpochMs: Long
    ) {
        fun toLocation(provider: String = "cached"): Location {
            return Location(provider).apply {
                latitude = this@Snapshot.latitude
                longitude = this@Snapshot.longitude
                if (accuracy >= 0f) {
                    this.accuracy = accuracy
                }
                time = capturedAtEpochMs
            }
        }
    }

    fun save(
        preferences: SharedPreferences,
        location: Location,
        capturedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        preferences.edit {
            putLong(
                Constants.CACHED_LOCATION_LAT_BITS,
                java.lang.Double.doubleToRawLongBits(location.latitude)
            )
            putLong(
                Constants.CACHED_LOCATION_LNG_BITS,
                java.lang.Double.doubleToRawLongBits(location.longitude)
            )
            putFloat(
                Constants.CACHED_LOCATION_ACCURACY,
                if (location.hasAccuracy()) location.accuracy else -1f
            )
            putLong(Constants.CACHED_LOCATION_CAPTURED_AT_MS, capturedAtEpochMs)
        }
    }

    fun read(preferences: SharedPreferences): Snapshot? {
        if (
            !preferences.contains(Constants.CACHED_LOCATION_LAT_BITS) ||
            !preferences.contains(Constants.CACHED_LOCATION_LNG_BITS) ||
            !preferences.contains(Constants.CACHED_LOCATION_CAPTURED_AT_MS)
        ) {
            return null
        }

        val latitude = java.lang.Double.longBitsToDouble(
            preferences.getLong(Constants.CACHED_LOCATION_LAT_BITS, 0L)
        )
        val longitude = java.lang.Double.longBitsToDouble(
            preferences.getLong(Constants.CACHED_LOCATION_LNG_BITS, 0L)
        )
        val capturedAtEpochMs = preferences.getLong(Constants.CACHED_LOCATION_CAPTURED_AT_MS, 0L)

        if (!latitude.isFinite() || !longitude.isFinite() || capturedAtEpochMs <= 0L) {
            clear(preferences)
            return null
        }

        return Snapshot(
            latitude = latitude,
            longitude = longitude,
            accuracy = preferences.getFloat(Constants.CACHED_LOCATION_ACCURACY, -1f),
            capturedAtEpochMs = capturedAtEpochMs
        )
    }

    fun clear(preferences: SharedPreferences) {
        preferences.edit {
            remove(Constants.CACHED_LOCATION_LAT_BITS)
            remove(Constants.CACHED_LOCATION_LNG_BITS)
            remove(Constants.CACHED_LOCATION_ACCURACY)
            remove(Constants.CACHED_LOCATION_CAPTURED_AT_MS)
        }
    }
}
