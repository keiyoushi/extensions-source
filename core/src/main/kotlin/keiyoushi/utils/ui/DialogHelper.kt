package keiyoushi.utils.ui

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
 * @return The text the user entered, or `null` if they canceled or dismissed the dialog.
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

        if (password) {
            val density = activity.resources.displayMetrics.density
            val eye = EyeDrawable(
                density = density,
                color = input.currentTextColor and 0x00FFFFFF or 0x99000000.toInt(),
            )
            val toggle = ImageView(activity).apply {
                setImageDrawable(eye)
                contentDescription = "Toggle visibility"
                val p = (8 * density).toInt()
                setPadding(p, p, p, p)
                setOnClickListener {
                    val wasVisible = eye.isPasswordVisible
                    eye.isPasswordVisible = !wasVisible
                    val start = input.selectionStart
                    val end = input.selectionEnd
                    input.transformationMethod = if (!wasVisible) null else PasswordTransformationMethod.getInstance()
                    input.setSelection(start, end)
                }
            }
            val iconSize = (40 * density).toInt()
            val lp = FrameLayout.LayoutParams(iconSize, iconSize).apply {
                gravity = Gravity.END or Gravity.CENTER_VERTICAL
                marginEnd = (4 * density).toInt()
            }
            input.setPaddingRelative(input.paddingStart, input.paddingTop, iconSize + (8 * density).toInt(), input.paddingBottom)
            container.addView(toggle, lp)
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
 * Shows a single-choice list of [options].
 *
 * @return The 0-based index of the chosen option, or `null` if canceled.
 */
suspend fun askSelect(
    title: String,
    options: List<String>,
    selectedIndex: Int = 0,
    message: String? = null,
): Int? {
    if (options.isEmpty()) return null

    return showDialog(onDismissed = { null }) { activity, resolve ->
        setTitle(title)
        var selected = selectedIndex.coerceIn(0, options.lastIndex)
        if (message != null) {
            val pad = (16 * activity.resources.displayMetrics.density).toInt()
            val textView = TextView(activity).apply {
                text = message
                setPadding(pad, pad / 2, pad, 0)
            }
            setCustomTitle(
                LinearLayout(activity).apply {
                    orientation = LinearLayout.VERTICAL
                    val titleView = TextView(activity).apply {
                        text = title
                        textSize = 20f
                        setPadding(pad, pad, pad, 0)
                    }
                    addView(titleView)
                    addView(textView)
                },
            )
        }
        setSingleChoiceItems(options.toTypedArray(), selected) { _, which ->
            selected = which
        }
        setPositiveButton(android.R.string.ok) { _, _ -> resolve(selected) }
        setNegativeButton(android.R.string.cancel, null)
    }
}

/** Shorthand for [askSelect] that returns the selected option string directly. */
suspend fun askSelectOption(
    title: String,
    options: List<String>,
    selectedIndex: Int = 0,
    message: String? = null,
): String? = askSelect(title, options, selectedIndex, message)?.let { options.getOrNull(it) }

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
    if (!activity.usable()) throw IllegalStateException("Activity unavailable for dialog")

    suspendCancellableCoroutine { cont ->
        try {
            var resolved = false
            var unhook: (() -> Unit)? = null
            val builder = AlertDialog.Builder(activity)
            builder.configure(activity) { value ->
                resolved = true
                if (cont.isActive) cont.resume(value)
            }
            builder.setOnDismissListener {
                unhook?.invoke()
                unhook = null
                if (cont.isActive && !resolved) {
                    runCatching(onDismissed).fold(cont::resume, cont::resumeWithException)
                }
            }

            val dialog = builder.show()
            unhook = activity.onDestroyed {
                runCatching { if (dialog.isShowing) dialog.dismiss() }
            }
            cont.invokeOnCancellation {
                activity.runOnUiThread { runCatching { if (dialog.isShowing) dialog.dismiss() } }
            }
            onShown(dialog)
        } catch (t: Throwable) {
            if (cont.isActive) cont.resumeWithException(t)
        }
    }
}

private class EyeDrawable(
    private val density: Float,
    private val color: Int,
) : Drawable() {
    var isPasswordVisible: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                invalidateSelf()
            }
        }

    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@EyeDrawable.color
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    private val pupilPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@EyeDrawable.color
        style = Paint.Style.FILL
    }

    override fun getIntrinsicWidth(): Int = (24 * density).toInt()
    override fun getIntrinsicHeight(): Int = (24 * density).toInt()

    override fun draw(canvas: Canvas) {
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val w = 18f * density
        val h = 10f * density

        val path = Path().apply {
            moveTo(cx - w / 2, cy)
            quadTo(cx, cy - h, cx + w / 2, cy)
            quadTo(cx, cy + h, cx - w / 2, cy)
            close()
        }
        canvas.drawPath(path, strokePaint)
        canvas.drawCircle(cx, cy, 2.5f * density, pupilPaint)

        if (!isPasswordVisible) {
            canvas.drawLine(cx - w / 2, cy - h / 1.5f, cx + w / 2, cy + h / 1.5f, strokePaint)
        }
    }

    override fun setAlpha(alpha: Int) {
        strokePaint.alpha = alpha
        pupilPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        strokePaint.colorFilter = colorFilter
        pupilPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}
