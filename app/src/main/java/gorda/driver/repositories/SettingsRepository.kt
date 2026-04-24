package gorda.driver.repositories

import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.TaskCompletionSource
import gorda.driver.interfaces.RideFees
import gorda.driver.services.masterData.MasterDataApiService
import gorda.driver.services.retrofit.MasterDataRetrofit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SettingsRepository {
    fun getRideFeesTask(): Task<RideFees> {
        val taskSource = TaskCompletionSource<RideFees>()

        getRideFees(
            onSuccess = { fees ->
                taskSource.setResult(fees)
            },
            onError = { message ->
                taskSource.setException(IllegalStateException(message))
            }
        )

        return taskSource.task
    }

    fun getRideFees(
        onSuccess: (fees: RideFees) -> Unit,
        onError: (message: String) -> Unit = {}
    ) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = MasterDataRetrofit.getRetrofit()
                    .create(MasterDataApiService::class.java)
                    .getRideFeesSnapshot()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        val fees = response.body()?.data?.rideFees
                        if (fees != null) {
                            onSuccess(fees)
                        } else {
                            onError("Empty ride fees response")
                        }
                    } else {
                        onError(response.message())
                    }
                }
            } catch (exception: Exception) {
                withContext(Dispatchers.Main) {
                    onError(exception.message ?: "Unknown error")
                }
            }
        }
    }
}
