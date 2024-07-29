package gorda.driver.utils

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateHelper {
    fun formatTimeFromSeconds(currentTimeInSeconds: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTimeInSeconds * 1000
        return "%02d:%02d:%02d".format(
            calendar.get(Calendar.HOUR_OF_DAY),
            calendar.get(Calendar.MINUTE),
            calendar.get(Calendar.SECOND)
        )
    }

    fun formatDateFromSeconds(currentTimeInSeconds: Long): String {
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = currentTimeInSeconds * 1000
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return sdf.format(calendar.time)
    }
}