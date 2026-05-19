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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Owns Firebase RTDB presence for a single driver.
 *
 * The visible Connected/Connecting state is driven by `.info/connected`,
 * NOT by the setValue completion callback. Firebase's setValue callback can
 * be silently delayed for minutes when the SDK is in a degraded state
 * (validated socket but stalled write pipeline), and gating UI on it caused
 * the "stuck disconnected" symptom even after a real reconnect.
 *
 * Writes are fire-and-forget: we still listen for PERMISSION_DENIED (the
 * version-unsupported / blocked-driver signal) but never block the UI on a
 * write ack. Firebase persistence queues writes offline and the SDK retries
 * its own writes when the socket recovers.
 *
 * On every `.info/connected` false → true transition, presence is rewritten
 * and `onDisconnect().removeValue()` is re-armed. This is the fix for the
 * "needs app restart" bug.
 */
class PresenceManager(
    private val onlineDriversRef: DatabaseReference = Database.dbOnlineDrivers(),
    private val infoConnectedRef: DatabaseReference = Database.dbInfoConnected(),
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main,
    private val nowSeconds: () -> Long = { System.currentTimeMillis() / 1000L },
    private val transportCycler: TransportCycler = TransportCycler.Default
) {

    sealed class State {
        object Idle : State()
        object Connecting : State()
        object Connected : State()
        object Offline : State()
        data class Fatal(val reason: String) : State()
    }

    /** Lets tests stub out goOffline/goOnline. */
    interface TransportCycler {
        fun cycle()
        object Default : TransportCycler {
            override fun cycle() {
                val db = com.google.firebase.database.FirebaseDatabase.getInstance()
                db.goOffline()
                db.goOnline()
            }
        }
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
        // Don't pre-judge: let the .info/connected listener tell us where we are.
        // If we're already connected, the listener will fire true immediately and we
        // transition into Connected. If not, it'll fire false and we'll show Connecting.
        transitionTo(if (!hasAndroidNetwork) State.Offline else State.Connecting)
        attachInfoConnectedListener()
        // Best-effort write. If Firebase is offline, persistence queues it; the SDK
        // will flush when the socket recovers. We don't gate state on the ack.
        writePresence()
    }

    /**
     * User-initiated kick when the SDK appears stuck (e.g. apply screen retry).
     * Cycles transport: goOffline → goOnline. Safe because we re-arm onDisconnect
     * on every `.info/connected` reconnect.
     */
    fun forceReconnect() {
        if (driverId == null) return
        Log.i(TAG, "force reconnect: cycling transport")
        try {
            transportCycler.cycle()
        } catch (e: Exception) {
            Log.w(TAG, "transport cycle failed: ${e.message}")
        }
        // The .info/connected listener will fire false then true and drive the rest.
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
            // We rely on .info/connected to flip to Connected. Defensive re-attach.
            transitionTo(State.Connecting)
            attachInfoConnectedListener()
        }
    }

    /** Hint from ConnectivityManager that the device has no transport network. */
    fun onAndroidNetworkLost() {
        if (!hasAndroidNetwork) return
        hasAndroidNetwork = false
        Log.d(TAG, "android network lost")
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
            // .info/connected → true is our truth signal for Connected, regardless of
            // whether any previous setValue has yet acked. UI unblocks here.
            if (_state.value !is State.Connected && _state.value !is State.Fatal) {
                transitionTo(State.Connected)
            }
            // (Re)write presence + (re)arm onDisconnect on every reconnect. This is
            // the load-bearing fix for the "needs app restart" bug.
            writePresence()
        } else {
            // Lost socket. Don't tear anything down; the SDK reconnects on its own.
            if (_state.value is State.Connected) {
                transitionTo(if (hasAndroidNetwork) State.Connecting else State.Offline)
            }
        }
    }

    /**
     * Fire-and-forget write of `/online_drivers/{id}`. State is NOT gated on
     * the ack — only PERMISSION_DENIED is acted on (version unsupported / driver
     * blocked). All other outcomes are logged for diagnostics.
     */
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
                    Log.d(TAG, "presence write acked")
                    ref.onDisconnect().removeValue { disconnectError, _ ->
                        if (disconnectError != null) {
                            Log.w(TAG, "onDisconnect rearm failed: ${disconnectError.message}")
                        }
                    }
                }
                error.code == DatabaseError.PERMISSION_DENIED -> {
                    Log.e(TAG, "presence write permission denied (version unsupported?)")
                    transitionTo(State.Fatal(reason = REASON_VERSION_UNSUPPORTED))
                }
                else -> {
                    Log.w(TAG, "presence write failed: ${error.message}")
                }
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
        detachInfoConnectedListener()
        driverId = null
        sessionId = null
        latestLocation = null
        firebaseConnected = false
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
    }
}

/** Convenience accessor for code that wants to catch the fatal version error. */
val PresenceManager.State.fatalVersionUnsupported: Boolean
    get() = this is PresenceManager.State.Fatal && this.reason == PresenceManager.REASON_VERSION_UNSUPPORTED
