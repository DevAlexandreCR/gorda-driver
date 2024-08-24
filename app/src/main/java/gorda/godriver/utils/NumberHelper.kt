package gorda.godriver.utils

import java.text.NumberFormat
import kotlin.math.round

object NumberHelper {
    fun roundDouble(double: Double): Double {
        return round(double / 100) * 100
    }

    fun toCurrency(double: Double, round: Boolean = false): String {
        val currencyFormat: NumberFormat = NumberFormat.getCurrencyInstance()
        return if (round) currencyFormat.format(roundDouble(double))
        else currencyFormat.format(double)
    }
}