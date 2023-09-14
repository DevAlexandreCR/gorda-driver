package gorda.driver.interfaces

import com.google.gson.annotations.SerializedName

data class RideFees (
    @SerializedName("price_kilometer") var priceKm: Double = 0.0,
    @SerializedName("price_minute") var priceMin: Double = 0.0,
    @SerializedName("fees_base") var feesBase: Double = 0.0,
    @SerializedName("fees_additional") var priceAddFee: Double = 0.0,
    @SerializedName("fees_minimum") var priceMinFee: Double = 0.0,
    @SerializedName("fees_night") var priceNightFee: Double = 0.0,
    @SerializedName("fees_DxF") var priceFestive: Double = 0.0,
    @SerializedName("fees_night_DxF") var priceFestiveNight: Double = 0.0,
)