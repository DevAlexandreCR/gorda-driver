package gorda.driver.services.masterData

import gorda.driver.interfaces.Device
import gorda.driver.interfaces.RideFees
import gorda.driver.models.Driver
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.Path

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

interface MasterDataApiService {
    @GET("public/master-data/ride-fees/snapshot")
    suspend fun getRideFeesSnapshot(): Response<ApiEnvelope<RideFeesPayload>>

    @GET("public/drivers/{id}")
    suspend fun getDriver(@Path("id") driverId: String): Response<ApiEnvelope<DriverPayload>>

    @PATCH("public/drivers/{id}/device")
    suspend fun updateDevice(
        @Path("id") driverId: String,
        @Body payload: DevicePayload
    ): Response<ApiEnvelope<DriverPayload>>
}
