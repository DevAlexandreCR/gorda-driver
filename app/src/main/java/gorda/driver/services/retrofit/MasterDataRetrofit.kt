package gorda.driver.services.retrofit

import gorda.driver.BuildConfig
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object MasterDataRetrofit {
    fun getRetrofit(): Retrofit {
        val baseUrl = if (BuildConfig.BASE_URL.endsWith("/")) {
            BuildConfig.BASE_URL
        } else {
            "${BuildConfig.BASE_URL}/"
        }

        val httpClient = OkHttpClient.Builder()
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
