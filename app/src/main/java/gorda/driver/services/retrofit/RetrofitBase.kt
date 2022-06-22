package gorda.driver.services.retrofit

import gorda.driver.BuildConfig
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

object RetrofitBase {

    fun getRetrofit(): Retrofit {
        return Retrofit.Builder()
            .baseUrl(BuildConfig.MAPS_BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}