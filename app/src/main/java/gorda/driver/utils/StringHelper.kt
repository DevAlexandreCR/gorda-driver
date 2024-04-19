package gorda.driver.utils

import android.content.Context
import android.os.Build
import android.text.Html
import android.text.Spanned
import androidx.core.text.HtmlCompat
import gorda.driver.R
import gorda.driver.models.Service

object StringHelper {
    fun getString(text: String): Spanned? {
        return if (Utils.isNewerVersion(Build.VERSION_CODES.N)) {
            Html.fromHtml(text, Html.FROM_HTML_MODE_COMPACT)
        } else {
            HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
        }
    }

    fun formatStatus(status: String, context: Context): String {
        return if (status == Service.STATUS_TERMINATED) context.getString(R.string.completed)
        else context.getString(R.string.canceled)
    }
}