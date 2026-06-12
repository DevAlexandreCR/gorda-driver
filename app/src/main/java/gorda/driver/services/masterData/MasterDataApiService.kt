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

    @GET("driver-app/me/vehicles")
    suspend fun getVehicles(
        @Header("Authorization") authorization: String
    ): Response<ApiEnvelope<VehiclesPayload>>

    @PUT("driver-app/me/selected-vehicle")
    suspend fun setSelectedVehicle(
        @Header("Authorization") authorization: String,
        @Body payload: SetSelectedVehicleRequest
    ): Response<ApiEnvelope<SetSelectedVehicleResponse>>
}
