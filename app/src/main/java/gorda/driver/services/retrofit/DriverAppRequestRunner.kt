package gorda.driver.services.retrofit

import android.util.Log
import gorda.driver.BuildConfig
import gorda.driver.services.firebase.Auth
import retrofit2.Response

class DriverAppRequestException(
    val endpoint: String,
    val baseUrl: String,
    val code: Int? = null,
    val responseMessage: String? = null,
    val errorBody: String? = null,
    val hasCurrentUser: Boolean,
    cause: Throwable? = null
) : IllegalStateException(
    buildString {
        append("Driver app request failed")
        append(" endpoint=").append(endpoint)
        append(" baseUrl=").append(baseUrl)
        append(" hasCurrentUser=").append(hasCurrentUser)
        code?.let { append(" code=").append(it) }
        responseMessage?.takeIf { it.isNotBlank() }?.let { append(" message=").append(it) }
        errorBody?.takeIf { it.isNotBlank() }?.let { append(" body=").append(it) }
    },
    cause
)

object DriverAppRequestRunner {
    private const val TAG = "DriverAppRequest"

    suspend fun <T> execute(
        endpoint: String,
        request: suspend (authorizationHeader: String) -> Response<T>
    ): Response<T> {
        return execute(endpoint, forceRefresh = false, request = request)
    }

    private suspend fun <T> execute(
        endpoint: String,
        forceRefresh: Boolean,
        request: suspend (authorizationHeader: String) -> Response<T>
    ): Response<T> {
        val hasCurrentUser = Auth.getCurrentUserUUID() != null
        if (!hasCurrentUser) {
            throw DriverAppRequestException(
                endpoint = endpoint,
                baseUrl = BuildConfig.BASE_URL,
                hasCurrentUser = false,
                responseMessage = "Firebase auth user is not available"
            )
        }

        val idToken = try {
            Auth.requireIdToken(forceRefresh)
        } catch (exception: Exception) {
            throw DriverAppRequestException(
                endpoint = endpoint,
                baseUrl = BuildConfig.BASE_URL,
                hasCurrentUser = true,
                responseMessage = "Unable to obtain Firebase ID token",
                cause = exception
            )
        }

        val response = request("Bearer $idToken")
        if (response.isSuccessful) {
            return response
        }

        val errorBody = try {
            response.errorBody()?.string()
        } catch (_: Exception) {
            null
        }

        Log.e(
            TAG,
            "endpoint=$endpoint baseUrl=${BuildConfig.BASE_URL} code=${response.code()} message=${response.message()} body=${errorBody ?: "n/a"} forceRefresh=$forceRefresh hasCurrentUser=$hasCurrentUser"
        )

        if (response.code() == 401 && !forceRefresh) {
            Log.w(
                TAG,
                "endpoint=$endpoint received 401, retrying with forced Firebase ID token refresh"
            )
            return execute(endpoint, forceRefresh = true, request = request)
        }

        throw DriverAppRequestException(
            endpoint = endpoint,
            baseUrl = BuildConfig.BASE_URL,
            code = response.code(),
            responseMessage = response.message(),
            errorBody = errorBody,
            hasCurrentUser = hasCurrentUser
        )
    }
}
