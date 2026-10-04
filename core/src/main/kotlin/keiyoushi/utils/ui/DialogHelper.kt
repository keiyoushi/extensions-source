package keiyoushi.utils.ui

import android.app.Activity
import android.app.AlertDialog
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Shows simple dialogs (info, yes/no, text input) on the foreground Activity and suspends until
 * the user answers.
 *
 * All methods:
 * - throw if no usable Activity is available or the dialog cannot be shown;
 * - dismiss the dialog if the calling coroutine is canceled.
 *
 * Activity lookup comes from [ActivityTrackingHelper].
 */
class DialogHelper : ActivityTrackingHelper() {

    /**
     * Shows a message with a single button and suspends until it is closed.
     * Back press or tapping outside also counts as closing, and does not throw.
     */
    suspend fun showInfo(
        title: String,
        message: String,
        buttonText: String = "OK",
    ): Unit = showDialog(onDismissed = { }) { _, resolve ->
        setTitle(title)
        setMessage(message)
        setPositiveButton(buttonText) { _, _ -> resolve(Unit) }
    }

    /**
     * Shows a yes/no question.
     *
     * @return `true` for the positive button, `false` for the negative button.
     * Back press or tapping outside also returns `false`.
     */
    suspend fun askConfirm(
        title: String,
        message: String,
        yesText: String = "Yes",
        noText: String = "No",
    ): Boolean = showDialog(onDismissed = { false }) { _, resolve ->
        setTitle(title)
        setMessage(message)
        setPositiveButton(yesText) { _, _ -> resolve(true) }
        setNegativeButton(noText) { _, _ -> resolve(false) }
    }

    /**
     * Shows a dialog with a single text field.
     *
     * @param title Dialog title.
     * @param message Optional text shown above the field.
     * @param hint Placeholder shown while the field is empty.
     * @param initialValue Text the field starts with.
     * @param password If true, the input is masked and keyboard suggestions/learning are disabled.
     * @param required If true, the OK button stays disabled while the field is empty, so the
     * result is never an empty string. Whitespace counts as input.
     * @return The text the user entered, or `null` if they cancelled or dismissed the dialog.
     * An empty string is only returned when [required] is false and the user pressed OK on an
     * empty field.
     */
    suspend fun askInput(
        title: String,
        message: String? = null,
        hint: String? = null,
        initialValue: String = "",
        password: Boolean = false,
        required: Boolean = true,
    ): String? {
        var inputField: EditText? = null

        return showDialog(
            onDismissed = { null },
            onShown = { dialog ->
                val field = inputField
                if (required && field != null) {
                    val ok = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    ok.isEnabled = field.text.isNotEmpty()
                    field.addTextChangedListener(object : TextWatcher {
                        override fun afterTextChanged(s: Editable?) {
                            ok.isEnabled = !s.isNullOrEmpty()
                        }

                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
                    })
                }
            },
        ) { activity, resolve ->
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
            inputField = input

            val container = FrameLayout(activity).apply {
                setPadding(pad, pad / 2, pad, 0)
                addView(input, FrameLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            }

            setTitle(title)
            if (message != null) setMessage(message)
            setView(container)
            setPositiveButton(android.R.string.ok) { _, _ -> resolve(input.text.toString()) }
            setNegativeButton(android.R.string.cancel, null)
        }
    }

    /** Shorthand for [askInput] with a masked field. See [askInput] for the parameters. */
    suspend fun askPassword(
        title: String = "Password Required",
        message: String? = null,
        hint: String? = "Password",
        required: Boolean = true,
    ): String? = askInput(
        title = title,
        message = message,
        hint = hint,
        password = true,
        required = required,
    )

    /**
     * Core of every dialog above.
     *
     * [configure] fills in the builder (title, message, view, buttons) and calls `resolve` from
     * a button to finish with a value. If the dialog closes without `resolve` being called
     * (back press, tapping outside, a button with no listener), [onDismissed] supplies the
     * result, or throws to fail the call.
     *
     * [onShown] runs on the main thread right after the dialog is shown, for work that needs the
     * dialog's buttons (for example enabling or disabling OK).
     */
    private suspend fun <T> showDialog(
        onDismissed: () -> T,
        onShown: (AlertDialog) -> Unit = {},
        configure: AlertDialog.Builder.(activity: Activity, resolve: (T) -> Unit) -> Unit,
    ): T = withContext(Dispatchers.Main) {
        val activity = topActivity()
        if (!activity.usable()) throw Exception("Activity unavailable for dialog")

        suspendCancellableCoroutine { cont ->
            try {
                val builder = AlertDialog.Builder(activity)
                builder.configure(activity) { value ->
                    if (cont.isActive) cont.resume(value)
                }
                builder.setOnDismissListener {
                    if (cont.isActive) {
                        runCatching(onDismissed).fold(cont::resume, cont::resumeWithException)
                    }
                }

                val dialog = builder.show()
                cont.invokeOnCancellation {
                    activity.runOnUiThread { if (dialog.isShowing) dialog.dismiss() }
                }
                onShown(dialog)
            } catch (t: Throwable) {
                if (cont.isActive) cont.resumeWithException(t)
            }
        }
    }
}
