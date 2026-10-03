package keiyoushi.utils

import android.app.AlertDialog
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import keiyoushi.utils.ui.ActivityTrackingHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class InputHelper : ActivityTrackingHelper() {

    suspend fun askInput(
        title: String,
        message: String? = null,
        hint: String? = null,
        initialValue: String = "",
        password: Boolean = false,
    ): String = withContext(Dispatchers.Main) {
        val activity = topActivity()
        if (!activity.usable()) throw Exception("Activity unavailable for input prompt")

        suspendCancellableCoroutine { cont ->
            val pad = (16 * activity.resources.displayMetrics.density).toInt()

            val input = EditText(activity).apply {
                this.hint = hint
                setText(initialValue)
                setSelection(text.length)
                inputType = if (password) {
                    InputType.TYPE_CLASS_TEXT or
                        InputType.TYPE_TEXT_VARIATION_PASSWORD or
                        InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                } else {
                    InputType.TYPE_CLASS_TEXT
                }
                if (password) transformationMethod = PasswordTransformationMethod.getInstance()
            }
            val container = FrameLayout(activity).apply {
                setPadding(pad, pad / 2, pad, 0)
                addView(input, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }

            try {
                val dialog = AlertDialog.Builder(activity)
                    .setTitle(title)
                    .apply { if (message != null) setMessage(message) }
                    .setView(container)
                    .setPositiveButton(android.R.string.ok) { _, _ ->
                        if (cont.isActive) cont.resume(input.text.toString())
                    }
                    .setNegativeButton(android.R.string.cancel, null)
                    .setOnDismissListener {
                        if (cont.isActive) cont.resumeWithException(Exception("Input cancelled"))
                    }
                    .show()

                cont.invokeOnCancellation {
                    activity.runOnUiThread { if (dialog.isShowing) dialog.dismiss() }
                }
            } catch (t: Throwable) {
                if (cont.isActive) cont.resumeWithException(t)
            }
        }
    }
}
