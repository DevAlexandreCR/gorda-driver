package gorda.driver.repositories

import gorda.driver.interfaces.RideFees
import gorda.driver.services.masterData.MasterDataApiService
import gorda.driver.services.retrofit.MasterDataRetrofit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

object SettingsRepository {
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
