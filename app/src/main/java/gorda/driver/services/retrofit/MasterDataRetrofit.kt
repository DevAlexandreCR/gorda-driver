package gorda.driver.services.retrofit

import com.google.android.gms.tasks.Tasks
import gorda.driver.BuildConfig
import gorda.driver.services.firebase.FirebaseInitializeApp
import okhttp3.Interceptor
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
            .addInterceptor(Interceptor { chain ->
                val requestBuilder = chain.request().newBuilder()
                val currentUser = FirebaseInitializeApp.auth.currentUser

                if (currentUser != null) {
                    try {
                        val tokenResult = Tasks.await(currentUser.getIdToken(false))
                        val idToken = tokenResult.token
                        if (!idToken.isNullOrBlank()) {
                            requestBuilder.header("Authorization", "Bearer $idToken")
                        }
                    } catch (_: Exception) {
                    }
                }

                chain.proceed(requestBuilder.build())
            })
            .build()

        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
    }
}
