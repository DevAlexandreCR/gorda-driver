package gorda.driver.services.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import gorda.driver.BuildConfig
import gorda.driver.interfaces.DriverConnected
import gorda.driver.interfaces.LocInterface
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Owns Firebase RTDB presence for a single driver.
 *
 * Source of truth for Firebase connectivity is `.info/connected`. Android's
 * ConnectivityManager is treated as a hint only (it drives the `Offline` UI
 * state but never tears down listeners or the SDK transport).
 *
 * On every transition of `.info/connected` from false → true, presence is
 * (re)written and `onDisconnect().removeValue()` is (re)armed. This is the
 * fix for the "needs app restart" bug: the previous coordinator armed the
 * onDisconnect handler only once and lost it after socket churn.
 */
class PresenceManager(
    private val onlineDriversRef: DatabaseReference = Database.dbOnlineDrivers(),
    private val infoConnectedRef: DatabaseReference = Database.dbInfoConnected(),
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) {

    sealed class State {
        object Idle : State()
        object Connecting : State()
        object Connected : State()
        object Offline : State()
        data class Fatal(val reason: String) : State()
    }

    private val scope = CoroutineScope(dispatcher + SupervisorJob())
    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    private var driverId: String? = null
    private var sessionId: String? = null
    private var latestLocation: LocInterface? = null
    private var hasAndroidNetwork: Boolean = true
    private var firebaseConnected: Boolean = false

    private var infoConnectedListener: ValueEventListener? = null
    private var retryJob: Job? = null
    private var backoffIndex: Int = 0

    /** Begin maintaining presence for `driverId` with the given last-known location. */
    fun start(driverId: String, location: LocInterface) {
        if (this.driverId == driverId && _state.value !is State.Idle) {
            latestLocation = location
            return
        }
        stopInternal(clearStateToIdle = false)
        this.driverId = driverId
        this.sessionId = UUID.randomUUID().toString()
        this.latestLocation = location
        this.backoffIndex = 0
        transitionTo(if (!hasAndroidNetwork) State.Offline else State.Connecting)
        attachInfoConnectedListener()
    }

    /** Stop maintaining presence. Best-effort cleanup of server-side state. */
    fun stop() {
        val id = driverId
        if (id != null) {
            try {
                onlineDriversRef.child(id).onDisconnect().cancel()
                onlineDriversRef.child(id).removeValue()
            } catch (e: Exception) {
                Log.w(TAG, "stop(): cleanup error: ${e.message}")
            }
        }
        stopInternal(clearStateToIdle = true)
    }

    /** Update last-known location. Heartbeat write only fires when Connected. */
    fun updateLocation(location: LocInterface) {
        latestLocation = location
        val id = driverId ?: return
        if (_state.value !is State.Connected) return
        onlineDriversRef.child(id).updateChildren(
            mapOf(
                "location" to location,
                "last_seen_at" to nowSeconds()
            )
        )
    }

    /** Hint from ConnectivityManager that the device has a transport network. */
    fun onAndroidNetworkAvailable() {
        if (hasAndroidNetwork) return
        hasAndroidNetwork = true
        Log.d(TAG, "android network available")
        if (driverId == null) return
        if (_state.value is State.Offline) {
            transitionTo(State.Connecting)
            // Defensive: re-attach in case the listener died.
            attachInfoConnectedListener()
        }
    }

    /** Hint from ConnectivityManager that the device has no transport network. */
    fun onAndroidNetworkLost() {
        if (!hasAndroidNetwork) return
        hasAndroidNetwork = false
        Log.d(TAG, "android network lost")
        retryJob?.cancel()
        retryJob = null
        if (driverId == null) return
        if (_state.value is State.Connected || _state.value is State.Connecting) {
            transitionTo(State.Offline)
        }
    }

    private fun attachInfoConnectedListener() {
        if (infoConnectedListener != null) return
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) == true
                onFirebaseConnectedChanged(connected)
            }

            override fun onCancelled(error: DatabaseError) {
                Log.w(TAG, ".info/connected cancelled: ${error.message}")
                infoConnectedListener = null
            }
        }
        infoConnectedListener = listener
        infoConnectedRef.addValueEventListener(listener)
    }

    private fun detachInfoConnectedListener() {
        infoConnectedListener?.let { infoConnectedRef.removeEventListener(it) }
        infoConnectedListener = null
    }

    private fun onFirebaseConnectedChanged(connected: Boolean) {
        val previous = firebaseConnected
        firebaseConnected = connected
        Log.i(TAG, "firebase connected: $connected (was $previous)")
        if (driverId == null) return

        if (connected) {
            // false → true: rearm presence + onDisconnect.
            backoffIndex = 0
            retryJob?.cancel()
            retryJob = null
            writePresence()
        } else {
            // Lost socket. Don't tear anything down; the SDK reconnects on its own.
            if (_state.value is State.Connected) {
                transitionTo(if (hasAndroidNetwork) State.Connecting else State.Offline)
            }
        }
    }

    private fun writePresence() {
        val currentDriverId = driverId ?: return
        val currentSession = sessionId ?: return
        val currentLocation = latestLocation
        if (currentLocation == null) {
            Log.d(TAG, "writePresence skipped: no location yet")
            return
        }
        val ref = onlineDriversRef.child(currentDriverId)
        val payload = object : DriverConnected {
            override var id: String = currentDriverId
            override var location: LocInterface = currentLocation
            override var version: String = BuildConfig.VERSION_NAME
            override var versionCode: Int = BuildConfig.VERSION_CODE
            override var last_seen_at: Long = nowSeconds()
            override var session_id: String = currentSession
        }
        ref.setValue(payload) { error, _ ->
            when {
                error == null -> {
                    ref.onDisconnect().removeValue { disconnectError, _ ->
                        if (disconnectError != null) {
                            Log.w(TAG, "onDisconnect rearm failed: ${disconnectError.message}")
                        }
                    }
                    backoffIndex = 0
                    transitionTo(State.Connected)
                }
                error.code == DatabaseError.PERMISSION_DENIED -> {
                    Log.e(TAG, "presence write permission denied (version unsupported?)")
                    transitionTo(State.Fatal(reason = REASON_VERSION_UNSUPPORTED))
                }
                else -> {
                    Log.w(TAG, "presence write failed: ${error.message}")
                    scheduleRetry()
                }
            }
        }
    }

    private fun scheduleRetry() {
        retryJob?.cancel()
        if (!hasAndroidNetwork) {
            transitionTo(State.Offline)
            return
        }
        if (_state.value is State.Connected) return
        transitionTo(State.Connecting)
        val delayMs = BACKOFF_MS[backoffIndex.coerceAtMost(BACKOFF_MS.lastIndex)]
        backoffIndex = (backoffIndex + 1).coerceAtMost(BACKOFF_MS.lastIndex)
        retryJob = scope.launch {
            delay(delayMs)
            if (firebaseConnected && driverId != null && _state.value !is State.Connected) {
                writePresence()
            }
        }
    }

    private fun transitionTo(next: State) {
        val previous = _state.value
        if (previous == next) return
        Log.i(TAG, "state $previous → $next")
        _state.value = next
    }

    private fun stopInternal(clearStateToIdle: Boolean) {
        retryJob?.cancel()
        retryJob = null
        detachInfoConnectedListener()
        driverId = null
        sessionId = null
        latestLocation = null
        backoffIndex = 0
        if (clearStateToIdle) {
            transitionTo(State.Idle)
        }
    }

    /** Release internal scope. Call from ViewModel.onCleared(). */
    fun dispose() {
        stop()
        scope.cancel()
    }

    companion object {
        private const val TAG = "PresenceManager"
        const val REASON_VERSION_UNSUPPORTED = "version_unsupported"
        private val BACKOFF_MS = longArrayOf(1_000L, 2_000L, 5_000L, 10_000L, 30_000L)
    }
}

/** Convenience accessor for code that wants to catch the fatal version error. */
val PresenceManager.State.fatalVersionUnsupported: Boolean
    get() = this is PresenceManager.State.Fatal && this.reason == PresenceManager.REASON_VERSION_UNSUPPORTED
