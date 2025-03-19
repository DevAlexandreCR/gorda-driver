package gorda.driver.serializers

import com.google.firebase.database.DataSnapshot
import com.google.gson.GsonBuilder
import com.google.gson.JsonDeserializationContext
import com.google.gson.JsonDeserializer
import com.google.gson.JsonElement
import gorda.driver.interfaces.RideFees
import java.lang.reflect.Type

class RideFeesDeserializer : JsonDeserializer<RideFees> {

    companion object {

        fun getRideFees(snapshot: DataSnapshot): RideFees {
            val gsonBuilder = GsonBuilder()
            gsonBuilder.registerTypeAdapter(RideFees::class.java, RideFeesDeserializer())
            val gson = gsonBuilder.create()
            val json = snapshot.value ?: RideFees()
            return  gson.fromJson(gson.toJson(json), RideFees::class.java)
        }
    }
    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): RideFees {
        val jsonObject = json?.asJsonObject
        val rideFees = RideFees()

        rideFees.priceKm = jsonObject?.get("price_kilometer")?.asDouble ?: 0.0
        rideFees.priceMin = jsonObject?.get("price_minute")?.asDouble ?: 0.0
        rideFees.feesBase = jsonObject?.get("fees_base")?.asDouble ?: 0.0
        rideFees.priceAddFee = jsonObject?.get("fees_additional")?.asDouble ?: 0.0
        rideFees.priceMinFee = jsonObject?.get("fees_minimum")?.asDouble ?: 0.0
        rideFees.priceNightFee = jsonObject?.get("fees_night")?.asDouble ?: 0.0
        rideFees.priceFestive = jsonObject?.get("fees_DxF")?.asDouble ?: 0.0
        rideFees.priceFestiveNight = jsonObject?.get("fees_night_DxF")?.asDouble ?: 0.0
        rideFees.timeoutToComplete = jsonObject?.get("timeout_to_complete")?.asInt ?: 240
        rideFees.timeoutToConnection = jsonObject?.get("timeout_to_connection")?.asInt ?: 120

        return rideFees
    }
}
