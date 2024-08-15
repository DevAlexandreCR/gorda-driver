package gorda.go.utils

import android.content.Context
import android.text.Spanned
import androidx.core.text.HtmlCompat
import gorda.go.R
import gorda.go.models.Service

object StringHelper {
    fun getString(text: String): Spanned {
        return HtmlCompat.fromHtml(text, HtmlCompat.FROM_HTML_MODE_LEGACY)
    }

    fun formatStatus(status: String, context: Context): String {
        return if (status == Service.STATUS_TERMINATED) context.getString(R.string.completed)
        else context.getString(R.string.canceled)
    }
}