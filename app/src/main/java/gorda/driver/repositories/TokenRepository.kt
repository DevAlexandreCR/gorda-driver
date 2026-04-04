package gorda.driver.repositories

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import gorda.driver.services.masterData.DriverTokenPayload
import gorda.driver.services.masterData.MasterDataApiService
import gorda.driver.services.retrofit.MasterDataRetrofit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object TokenRepository {
    fun setCurrentToken(id: String, token: String): Task<Void> {
        val taskSource = TaskCompletionSource<Void>()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = MasterDataRetrofit.getRetrofit()
                    .create(MasterDataApiService::class.java)
                    .upsertDriverToken(DriverTokenPayload(token))

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        taskSource.setResult(null)
                    } else {
                        taskSource.setException(IllegalStateException(response.message()))
                    }
                }
            } catch (exception: Exception) {
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
                val response = MasterDataRetrofit.getRetrofit()
                    .create(MasterDataApiService::class.java)
                    .deleteDriverToken()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        taskSource.setResult(null)
                    } else {
                        taskSource.setException(IllegalStateException(response.message()))
                    }
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    taskSource.setException(exception)
                }
            }
        }

        return taskSource.task
    }
}
