package gorda.driver.services.firebase

import android.util.Log
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.ValueEventListener
import com.google.gson.Gson
import gorda.driver.interfaces.LocInterface
import gorda.driver.services.masterData.ConnectLocation
import gorda.driver.services.masterData.ConnectRequest
import gorda.driver.services.masterData.MasterDataApiService
import gorda.driver.services.retrofit.DriverAppRequestException
import gorda.driver.services.retrofit.DriverAppRequestRunner
import gorda.driver.services.retrofit.MasterDataRetrofit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    private val infoConnectedRef: DatabaseReference = Database.dbInfoConnected(),
    private val dispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Main,
    private val transportCycler: TransportCycler = TransportCycler.Default,
    private val apiService: MasterDataApiService = MasterDataRetrofit.getRetrofit().create(MasterDataApiService::class.java)
) {

    sealed class State {
        object Idle : State()
        object Connecting : State()
        object Connected : State()
        object Offline : State()
        data class Fatal(val reason: String) : State()
        data class ConnectRejected(val reason: String) : State()
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
    private var vehicleId: String? = null
    private var sessionId: String? = null
    private var latestLocation: LocInterface? = null
    private var hasAndroidNetwork: Boolean = true
    private var firebaseConnected: Boolean = false

    private var infoConnectedListener: ValueEventListener? = null

    /** Begin maintaining presence for `driverId` with the given last-known location. */
    fun start(driverId: String, vehicleId: String, location: LocInterface) {
        if (this.driverId == driverId && this.vehicleId == vehicleId && _state.value !is State.Idle) {
            latestLocation = location
            return
        }
        stopInternal(clearStateToIdle = false)
        this.driverId = driverId
        this.vehicleId = vehicleId
        this.sessionId = UUID.randomUUID().toString()
        this.latestLocation = location
        // Don't pre-judge: let the .info/connected listener tell us where we are.
        // When Firebase reports connected, writePresence() will call the API to register.
        transitionTo(if (!hasAndroidNetwork) State.Offline else State.Connecting)
        attachInfoConnectedListener()
        // Kick off an initial connect attempt in case Firebase is already connected.
        // If Firebase is offline the .info/connected listener will drive the retry.
        if (firebaseConnected) {
            writePresence()
        }
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

    /** Stop maintaining presence. Server-side cleanup is handled by the API disconnect endpoint. */
    fun stop() {
        stopInternal(clearStateToIdle = true)
    }

    /** Update last-known location. Location updates are handled by the server via periodic API calls. */
    fun updateLocation(location: LocInterface) {
        latestLocation = location
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
            // Firebase socket is up; attempt the API connect. State transitions to Connected
            // only after the API confirms the connect (200 OK). Re-calling writePresence on
            // every reconnect makes the connect resilient to network interruptions.
            if (_state.value !is State.Fatal && _state.value !is State.ConnectRejected) {
                writePresence()
            }
        } else {
            // Lost socket. Don't tear anything down; the SDK reconnects on its own.
            if (_state.value is State.Connected) {
                transitionTo(if (hasAndroidNetwork) State.Connecting else State.Offline)
            }
        }
    }

    /**
     * Call the API to register presence. On 200 OK the API writes /online_drivers/{id}
     * server-side — the app no longer writes RTDB directly. On 409 the connect is
     * rejected with a structured reason; other errors are logged and the state stays
     * in Connecting so the next Firebase reconnect will retry.
     */
    private fun writePresence() {
        val currentDriverId = driverId ?: return
        val currentVehicleId = vehicleId ?: return
        val currentSession = sessionId ?: return
        val currentLocation = latestLocation
        if (currentLocation == null) {
            Log.d(TAG, "writePresence skipped: no location yet")
            return
        }

        scope.launch(Dispatchers.IO) {
            try {
                val payload = ConnectRequest(
                    vehicle_id = currentVehicleId,
                    session_id = currentSession,
                    location = ConnectLocation(
                        lat = currentLocation.lat,
                        lng = currentLocation.lng
                    )
                )
                DriverAppRequestRunner.execute("/driver-app/me/connect") { authorization ->
                    apiService.connect(authorization, payload)
                }
                Log.i(TAG, "connect API success — driverId=$currentDriverId vehicleId=$currentVehicleId")
                transitionTo(State.Connected)
            } catch (e: DriverAppRequestException) {
                if (e.code == 409) {
                    val reason = parseConnectErrorReason(e.errorBody)
                    Log.w(TAG, "connect rejected: reason=$reason driverId=$currentDriverId vehicleId=$currentVehicleId")
                    transitionTo(State.ConnectRejected(reason = reason))
                } else {
                    Log.w(TAG, "connect API error: code=${e.code} message=${e.responseMessage}")
                    // Stay in Connecting; the next Firebase reconnect will retry.
                }
            } catch (e: Exception) {
                Log.w(TAG, "connect API exception: ${e.message}")
                // Stay in Connecting; the next Firebase reconnect will retry.
            }
        }
    }

    private data class ConnectErrorBody(val error: String?)

    private fun parseConnectErrorReason(errorBody: String?): String {
        if (errorBody == null) return REASON_UNKNOWN
        return try {
            Gson().fromJson(errorBody, ConnectErrorBody::class.java)?.error ?: REASON_UNKNOWN
        } catch (_: Exception) {
            REASON_UNKNOWN
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
        vehicleId = null
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
        const val REASON_VEHICLE_IN_USE = "vehicle_in_use"
        const val REASON_DRIVER_ALREADY_CONNECTED = "driver_already_connected"
        const val REASON_VEHICLE_DISABLED = "vehicle_disabled"
        const val REASON_VEHICLE_NOT_SELECTABLE = "vehicle_not_selectable"
        private const val REASON_UNKNOWN = "unknown"
    }
}

/** Convenience accessor for code that wants to catch the fatal version error. */
val PresenceManager.State.fatalVersionUnsupported: Boolean
    get() = this is PresenceManager.State.Fatal && this.reason == PresenceManager.REASON_VERSION_UNSUPPORTED
