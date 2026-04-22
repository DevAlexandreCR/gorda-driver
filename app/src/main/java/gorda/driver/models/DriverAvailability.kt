package gorda.driver.models

import com.google.gson.annotations.SerializedName
import java.io.Serializable

data class DriverAvailability(
    @SerializedName("canGoOnline") var canGoOnline: Boolean = true,
    @SerializedName("canApply") var canApply: Boolean = true,
    @SerializedName("reason") var reason: String? = null,
    @SerializedName("paymentMode") var paymentMode: String = "monthly",
    @SerializedName("balance") var balance: Double = 0.0,
    @SerializedName("enabledAt") var enabledAt: Int = 0,
) : Serializable
