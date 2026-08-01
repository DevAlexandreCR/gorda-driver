package gorda.driver.services.masterData

import gorda.driver.interfaces.Device
import gorda.driver.interfaces.RideFees
import gorda.driver.models.Driver
import gorda.driver.models.Service
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT

data class ApiEnvelope<T>(
    val success: Boolean,
    val data: T?
)

data class DriverPayload(
    val driver: Driver
)

data class RideFeesPayload(
    val rideFees: RideFees
)

data class DevicePayload(
    val device: Device?
)

data class ServicesPayload(
    val services: List<Service>
)

data class DriverTokenPayload(
    val token: String
)

data class DriverVersionPolicy(
    val minVersionCode: Int
)

data class VersionPolicyPayload(
    val versionPolicy: VersionPolicyBody
)

data class VersionPolicyBody(
    val driver: DriverVersionPolicy
)

data class ConnectLocation(
    val lat: Double,
    val lng: Double
)

data class ConnectRequest(
    val vehicle_id: String,
    val session_id: String,
    val location: ConnectLocation
)

data class ConnectResponse(
    val connected: Boolean? = null
)

data class DisconnectResponse(
    val disconnected: Boolean? = null
)

data class LocationRequest(
    val session_id: String,
    val location: ConnectLocation
)

data class LocationResponse(
    val updated: Boolean? = null
)

data class VehicleColor(
    val hex: String?,
    val name: String?
)

data class RosterVehicle(
    val id: String,
    val plate: String,
    val brand: String?,
    val model: String?,
    val color: VehicleColor?,
    val is_selectable: Boolean,
    val is_selected: Boolean
)

data class VehiclesPayload(
    val vehicles: List<RosterVehicle>
)

data class SetSelectedVehicleRequest(
    val vehicle_id: String
)

data class SetSelectedVehicleResponse(
    val selected: Boolean? = null
)

/**
 * Body for POST /driver-app/me/services.
 *
 * Online mode only sets [location] and [trip_multiplier]. Deferred mode (a trip that started
 * and/or finished offline) additionally sets [deferred] plus the app-reported [created_at]/
 * [start_trip_at], the terminal [status] ("terminated" or "canceled"), and — when terminated —
 * [end_trip_at]/[trip_fee]/[trip_distance]/[route]. Fields left null are omitted from the
 * serialized JSON (default Gson does not serialize nulls), matching the online-mode payload
 * shape expected by the API.
 */
data class CreateServiceRequest(
    val location: ConnectLocation,
    val trip_multiplier: Double,
    val deferred: Boolean? = null,
    val created_at: Long? = null,
    val start_trip_at: Long? = null,
    val end_trip_at: Long? = null,
    val status: String? = null,
    val trip_fee: Int? = null,
    val trip_distance: Int? = null,
    val route: String? = null
)

data class ServicePayload(
    val service: Service
)

/** Typed rejection reasons for POST /driver-app/me/services (403/409/400 error bodies). */
object SelfServiceRejectionReason {
    const val DRIVER_NOT_CONNECTED = "driver_not_connected"
    const val DRIVER_DISABLED = "driver_disabled"
    const val NEGATIVE_BALANCE_PERCENTAGE = "negative_balance_percentage"
    const val DRIVER_ALREADY_IN_SERVICE = "driver_already_in_service"
    const val MALFORMED_DEFERRED_PAYLOAD = "malformed_deferred_payload"
}

/** Typed rejection reasons for POST /driver-app/me/services/{id}/cancel (403/404/409 error bodies). */
object SelfServiceCancelRejectionReason {
    const val SERVICE_NOT_FOUND = "service_not_found"
    const val NOT_SELF_SERVICE = "not_self_service"
    const val NOT_OWNER = "not_owner"
    const val INVALID_STATUS = "invalid_status"
    const val CANCEL_WINDOW_ELAPSED = "cancel_window_elapsed"
}

/**
 * Shape of the terse (non-envelope) error bodies returned by the self-service create/cancel
 * endpoints, e.g. {"error":"driver_already_in_service"} or
 * {"error":"malformed_deferred_payload","reason":"<code>"}.
 */
data class SelfServiceErrorBody(
    val error: String? = null,
    val reason: String? = null
)

interface MasterDataApiService {
    @GET("public/master-data/ride-fees/snapshot")
    suspend fun getRideFeesSnapshot(): Response<ApiEnvelope<RideFeesPayload>>

    @GET("public/master-data/version-policy")
    suspend fun getVersionPolicy(): Response<ApiEnvelope<VersionPolicyPayload>>

    @GET("public/drivers/{id}")
    suspend fun getDriver(@Path("id") driverId: String): Response<ApiEnvelope<DriverPayload>>

    @PATCH("public/drivers/{id}/device")
    suspend fun updateDevice(
        @Path("id") driverId: String,
        @Body payload: DevicePayload
    ): Response<ApiEnvelope<DriverPayload>>

    @GET("driver-app/me/history")
    suspend fun getDriverHistory(
        @Header("Authorization") authorization: String
    ): Response<ApiEnvelope<ServicesPayload>>

    @PUT("driver-app/me/token")
    suspend fun upsertDriverToken(
        @Header("Authorization") authorization: String,
        @Body payload: DriverTokenPayload
    ): Response<ApiEnvelope<Map<String, Any?>>>

    @DELETE("driver-app/me/token")
    suspend fun deleteDriverToken(
        @Header("Authorization") authorization: String
    ): Response<ApiEnvelope<Map<String, Any?>>>

    @POST("driver-app/me/connect")
    suspend fun connect(
        @Header("Authorization") authorization: String,
        @Body payload: ConnectRequest
    ): Response<ApiEnvelope<ConnectResponse>>

    @POST("driver-app/me/disconnect")
    suspend fun disconnect(
        @Header("Authorization") authorization: String
    ): Response<ApiEnvelope<DisconnectResponse>>

    @PUT("driver-app/me/location")
    suspend fun updateLocation(
        @Header("Authorization") authorization: String,
        @Body payload: LocationRequest
    ): Response<ApiEnvelope<LocationResponse>>

    @GET("driver-app/me/vehicles")
    suspend fun getVehicles(
        @Header("Authorization") authorization: String
    ): Response<ApiEnvelope<VehiclesPayload>>

    @PUT("driver-app/me/selected-vehicle")
    suspend fun setSelectedVehicle(
        @Header("Authorization") authorization: String,
        @Body payload: SetSelectedVehicleRequest
    ): Response<ApiEnvelope<SetSelectedVehicleResponse>>

    @POST("driver-app/me/services")
    suspend fun createSelfService(
        @Header("Authorization") authorization: String,
        @Body payload: CreateServiceRequest
    ): Response<ApiEnvelope<ServicePayload>>

    @POST("driver-app/me/services/{id}/cancel")
    suspend fun cancelSelfService(
        @Header("Authorization") authorization: String,
        @Path("id") serviceId: String
    ): Response<ApiEnvelope<Map<String, Any?>>>
}
