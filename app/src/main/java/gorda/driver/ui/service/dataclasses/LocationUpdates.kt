package gorda.driver.ui.service.dataclasses

import android.location.Location

sealed class LocationUpdates {
    enum class Source {
        LIVE,
        CACHED
    }

    data class LastLocation(
        var location: Location,
        val source: Source,
        val capturedAtEpochMs: Long
    ) : LocationUpdates() {
        val isFreshObserved: Boolean
            get() = source == Source.LIVE
    }

    companion object {
        fun lastLocation(location: Location): LocationUpdates =
            liveLocation(location)

        fun liveLocation(
            location: Location,
            capturedAtEpochMs: Long = System.currentTimeMillis()
        ): LocationUpdates = LastLocation(
            location = location,
            source = Source.LIVE,
            capturedAtEpochMs = capturedAtEpochMs
        )

        fun cachedLocation(
            location: Location,
            capturedAtEpochMs: Long
        ): LocationUpdates = LastLocation(
            location = location,
            source = Source.CACHED,
            capturedAtEpochMs = capturedAtEpochMs
        )
    }
}
