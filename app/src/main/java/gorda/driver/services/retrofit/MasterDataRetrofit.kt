package gorda.driver.services.retrofit

import gorda.driver.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object MasterDataRetrofit {
    fun getRetrofit(): Retrofit {
        val baseUrl = if (BuildConfig.BASE_URL.endsWith("/")) {
            BuildConfig.BASE_URL
        } else {
            "${BuildConfig.BASE_URL}/"
        }

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
