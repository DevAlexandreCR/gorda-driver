package gorda.driver.repositories

import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import gorda.driver.services.retrofit.DriverAppRequestRunner
import gorda.driver.services.masterData.DriverTokenPayload
import gorda.driver.services.masterData.MasterDataApiService
import gorda.driver.services.retrofit.MasterDataRetrofit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TokenRepository {
    private const val TAG = "TokenRepository"

    fun setCurrentToken(id: String, token: String): Task<Void> {
        val taskSource = TaskCompletionSource<Void>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val service = MasterDataRetrofit.getRetrofit()
                    .create(MasterDataApiService::class.java)
                DriverAppRequestRunner.execute("/driver-app/me/token") { authorization ->
                    service.upsertDriverToken(authorization, DriverTokenPayload(token))
                }

                withContext(Dispatchers.Main) {
                    taskSource.setResult(null)
                }
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Token update failed driverId=$id baseUrl=${gorda.driver.BuildConfig.BASE_URL} hasCurrentUser=${gorda.driver.services.firebase.Auth.isUserSignedIn()}",
                    exception
                )
                withContext(Dispatchers.Main) {
                    taskSource.setException(exception)
                }
            }
        }

        return taskSource.task
    }

    fun deleteCurrentToken(id: String): Task<Void> {
        val taskSource = TaskCompletionSource<Void>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val service = MasterDataRetrofit.getRetrofit()
                    .create(MasterDataApiService::class.java)
                DriverAppRequestRunner.execute("/driver-app/me/token") { authorization ->
                    service.deleteDriverToken(authorization)
                }

                withContext(Dispatchers.Main) {
                    taskSource.setResult(null)
                }
            } catch (exception: Exception) {
                Log.e(
                    TAG,
                    "Token delete failed driverId=$id baseUrl=${gorda.driver.BuildConfig.BASE_URL} hasCurrentUser=${gorda.driver.services.firebase.Auth.isUserSignedIn()}",
                    exception
                )
                withContext(Dispatchers.Main) {
                    taskSource.setException(exception)
                }
            }
        }

        return taskSource.task
    }
}
