package gorda.godriver.maps

import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Url

interface MapApiService {
    @GET
    suspend fun getDirections(@Url url: String): Response<MapData>
}