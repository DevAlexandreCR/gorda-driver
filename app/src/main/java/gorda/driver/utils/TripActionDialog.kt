package gorda.driver.utils

import android.app.AlertDialog
import android.content.Context
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import gorda.driver.R

fun showTripActionDialog(
    context: Context,
    titleRes: Int,
    message: CharSequence,
    primaryTextRes: Int,
    secondaryTextRes: Int,
    iconRes: Int,
    primaryIconRes: Int,
    secondaryIconRes: Int,
    customBody: View? = null,
    onActionSelected: (Boolean) -> Unit
): AlertDialog {
    val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_trip_action, null)
    val dialog = AlertDialog.Builder(context)
        .setView(dialogView)
        .create()

    dialog.setCancelable(false)
    dialog.setCanceledOnTouchOutside(false)

    val iconView = dialogView.findViewById<ImageView>(R.id.dialogIcon)
    val titleView = dialogView.findViewById<TextView>(R.id.dialogTitle)
    val messageView = dialogView.findViewById<TextView>(R.id.dialogMessage)
    val bodyContainer = dialogView.findViewById<FrameLayout>(R.id.dialogBodyContainer)
    val primaryButton = dialogView.findViewById<MaterialButton>(R.id.btnPrimary)
    val secondaryButton = dialogView.findViewById<MaterialButton>(R.id.btnSecondary)

    iconView.setImageResource(iconRes)
    titleView.setText(titleRes)
    messageView.text = message
    primaryButton.setText(primaryTextRes)
    primaryButton.setIconResource(primaryIconRes)
    secondaryButton.setText(secondaryTextRes)
    secondaryButton.setIconResource(secondaryIconRes)

    if (customBody != null) {
        (customBody.parent as? ViewGroup)?.removeView(customBody)
        bodyContainer.visibility = View.VISIBLE
        bodyContainer.addView(customBody)
    } else {
        bodyContainer.visibility = View.GONE
    }

    val dialogEditTexts = mutableListOf<EditText>()
    collectEditTexts(customBody, dialogEditTexts)
    dialogEditTexts.forEach { editText ->
        editText.setOnEditorActionListener { _, actionId, event ->
            val isDoneAction = actionId == EditorInfo.IME_ACTION_DONE
            val isEnterKey = event?.keyCode == KeyEvent.KEYCODE_ENTER &&
                event.action == KeyEvent.ACTION_UP
            if (isDoneAction || isEnterKey) {
                primaryButton.performClick()
                true
            } else {
                false
            }
        }
    }

    primaryButton.setOnClickListener {
        dialog.dismiss()
        onActionSelected(true)
    }

    secondaryButton.setOnClickListener {
        dialog.dismiss()
        onActionSelected(false)
    }

    dialog.show()
    dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
    dialogEditTexts.forEach(EditText::clearFocus)
    primaryButton.requestFocus()
    return dialog
}

private fun collectEditTexts(view: View?, sink: MutableList<EditText>) {
    when (view) {
        null -> return
        is EditText -> sink.add(view)
        is ViewGroup -> {
            for (index in 0 until view.childCount) {
                collectEditTexts(view.getChildAt(index), sink)
            }
        }
    }
}
