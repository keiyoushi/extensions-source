package keiyoushi.utils.ui

import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Shows HTML in an auto-sizing dialog and returns what the page submits.
 *
 * The page interacts through `window.Dialog`:
 * - `Dialog.submit(value)`: resolves the dialog with [value] (objects are JSON-serialized).
 * - `Dialog.cancel()`: cancels and closes the dialog, returning `null`.
 * - `Dialog.theme`: `"light"` or `"dark"`.
 */
class HtmlDialogHelper : BaseWebViewDialogHelper() {

    suspend fun showHtmlDialog(
        html: String,
        timeout: Duration = 5.minutes,
    ): String? = runDialog(
        html = html,
        initiallyVisible = true,
        timeout = timeout,
    )

    suspend fun htmlDialog(
        html: String,
        timeout: Duration = 5.minutes,
    ): String? = showHtmlDialog(html, timeout)
}

suspend fun showHtmlDialog(
    html: String,
    timeout: Duration = 5.minutes,
): String? = HtmlDialogHelper().showHtmlDialog(html, timeout)

suspend fun htmlDialog(
    html: String,
    timeout: Duration = 5.minutes,
): String? = HtmlDialogHelper().showHtmlDialog(html, timeout)

/** Shows an informative message with an OK button. */
suspend fun HtmlDialogHelper.showInfo(
    title: String,
    message: String,
    buttonText: String = "OK",
) {
    val html = """
        <h3 style="margin-top: 0; margin-bottom: 8px;">${title.escapeHtml()}</h3>
        <p style="color: var(--muted); margin-top: 0; margin-bottom: 16px;">${message.escapeHtml()}</p>
        <div style="display: flex; justify-content: flex-end;">
            <button type="button" class="primary" onclick="Dialog.submit('')">${buttonText.escapeHtml()}</button>
        </div>
    """.trimIndent()
    showHtmlDialog(html)
}

/** Shows a confirmation question with Yes/No buttons. */
suspend fun HtmlDialogHelper.askConfirm(
    title: String,
    message: String,
    yesText: String = "Yes",
    noText: String = "No",
): Boolean {
    val html = """
        <h3 style="margin-top: 0; margin-bottom: 8px;">${title.escapeHtml()}</h3>
        <p style="color: var(--muted); margin-top: 0; margin-bottom: 16px;">${message.escapeHtml()}</p>
        <div style="display: flex; justify-content: flex-end; gap: 8px;">
            <button type="button" onclick="Dialog.cancel()">${noText.escapeHtml()}</button>
            <button type="button" class="primary" onclick="Dialog.submit('true')">${yesText.escapeHtml()}</button>
        </div>
    """.trimIndent()
    return showHtmlDialog(html) == "true"
}

/** Shows a text input dialog with an OK and Cancel button. */
suspend fun HtmlDialogHelper.askInput(
    title: String,
    message: String? = null,
    hint: String? = null,
    initialValue: String = "",
    required: Boolean = true,
): String? {
    val messageHtml = message?.let {
        """<p style="color: var(--muted); margin-top: 0; margin-bottom: 8px;">${it.escapeHtml()}</p>"""
    } ?: ""

    val html = """
        <form id="f" onsubmit="event.preventDefault(); var val = document.getElementById('input').value; if ($required && !val.trim()) return; Dialog.submit(val);">
            <h3 style="margin-top: 0; margin-bottom: 8px;">${title.escapeHtml()}</h3>
            $messageHtml
            <input id="input" type="text" value="${initialValue.escapeHtml()}" placeholder="${hint?.escapeHtml() ?: ""}" autofocus>
            <div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px;">
                <button type="button" onclick="Dialog.cancel()">Cancel</button>
                <button type="submit" id="okBtn" class="primary">OK</button>
            </div>
        </form>
        <script>
            var input = document.getElementById('input');
            var okBtn = document.getElementById('okBtn');
            input.focus();
            if ($required) {
                function validate() { okBtn.disabled = !input.value.trim(); }
                input.addEventListener('input', validate);
                validate();
            }
        </script>
    """.trimIndent()

    return showHtmlDialog(html)
}

/** Shows a password input dialog with masked text and a visibility toggle. */
suspend fun HtmlDialogHelper.askPassword(
    title: String = "Password Required",
    message: String? = null,
    hint: String? = "Password",
    required: Boolean = true,
): String? {
    val messageHtml = message?.let {
        """<p style="color: var(--muted); margin-top: 0; margin-bottom: 8px;">${it.escapeHtml()}</p>"""
    } ?: ""

    val html = """
        <form id="f" onsubmit="event.preventDefault(); var val = document.getElementById('input').value; if ($required && !val.trim()) return; Dialog.submit(val);">
            <h3 style="margin-top: 0; margin-bottom: 8px;">${title.escapeHtml()}</h3>
            $messageHtml
            <div style="position: relative; display: flex; align-items: center;">
                <input id="input" type="password" placeholder="${hint?.escapeHtml() ?: ""}" autocomplete="off" autofocus style="padding-right: 36px;">
                <button type="button" id="toggleBtn" class="icon-btn" onclick="var i = document.getElementById('input'), s = document.getElementById('slash'), show = i.type === 'password'; i.type = show ? 'text' : 'password'; s.style.display = show ? 'block' : 'none';" style="position: absolute; right: 8px; cursor: pointer; color: var(--muted); display: flex; align-items: center;">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
                        <path d="M1 12s4-8 11-8 11 8 11 8-4 8-11 8-11-8-11-8z"></path>
                        <circle cx="12" cy="12" r="3"></circle>
                        <line id="slash" x1="1" y1="1" x2="23" y2="23" style="display: none;"></line>
                    </svg>
                </button>
            </div>
            <div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px;">
                <button type="button" onclick="Dialog.cancel()">Cancel</button>
                <button type="submit" id="okBtn" class="primary">OK</button>
            </div>
        </form>
        <script>
            var input = document.getElementById('input');
            var okBtn = document.getElementById('okBtn');
            input.focus();
            if ($required) {
                function validate() { okBtn.disabled = !input.value.trim(); }
                input.addEventListener('input', validate);
                validate();
            }
        </script>
    """.trimIndent()

    return showHtmlDialog(html)
}

/** Shows a radio list selection dialog and returns the selected index, or null if cancelled. */
suspend fun HtmlDialogHelper.askSelect(
    title: String,
    options: List<String>,
    selectedIndex: Int = 0,
    message: String? = null,
): Int? {
    if (options.isEmpty()) return null

    val messageHtml = message?.let {
        """<p style="color: var(--muted); margin-top: 0; margin-bottom: 8px;">${it.escapeHtml()}</p>"""
    } ?: ""

    val optionsHtml = options.mapIndexed { idx, opt ->
        val checked = if (idx == selectedIndex) "checked" else ""
        """
        <label style="display: flex; align-items: center; gap: 10px; padding: 8px; border-radius: 4px; cursor: pointer;">
            <input type="radio" name="opt" value="$idx" $checked style="margin: 0; width: auto;">
            <span>${opt.escapeHtml()}</span>
        </label>
        """.trimIndent()
    }.joinToString("\n")

    val html = """
        <form id="f" onsubmit="event.preventDefault(); var sel = document.querySelector('input[name=opt]:checked'); if (sel) Dialog.submit(sel.value);">
            <h3 style="margin-top: 0; margin-bottom: 8px;">${title.escapeHtml()}</h3>
            $messageHtml
            <div style="display: flex; flex-direction: column; max-height: 240px; overflow-y: auto; margin: 8px 0;">
                $optionsHtml
            </div>
            <div style="display: flex; justify-content: flex-end; gap: 8px; margin-top: 16px;">
                <button type="button" onclick="Dialog.cancel()">Cancel</button>
                <button type="submit" class="primary">OK</button>
            </div>
        </form>
    """.trimIndent()

    return showHtmlDialog(html)?.toIntOrNull()
}

/** Shorthand for [askSelect] that returns the selected option string directly. */
suspend fun HtmlDialogHelper.askSelectOption(
    title: String,
    options: List<String>,
    selectedIndex: Int = 0,
    message: String? = null,
): String? = askSelect(title, options, selectedIndex, message)?.let { options.getOrNull(it) }

// Top-level convenience overloads
suspend fun showInfo(title: String, message: String, buttonText: String = "OK") = HtmlDialogHelper().showInfo(title, message, buttonText)

suspend fun askConfirm(title: String, message: String, yesText: String = "Yes", noText: String = "No"): Boolean = HtmlDialogHelper().askConfirm(title, message, yesText, noText)

suspend fun askInput(title: String, message: String? = null, hint: String? = null, initialValue: String = "", required: Boolean = true): String? = HtmlDialogHelper().askInput(title, message, hint, initialValue, required)

suspend fun askPassword(title: String = "Password Required", message: String? = null, hint: String? = "Password", required: Boolean = true): String? = HtmlDialogHelper().askPassword(title, message, hint, required)

suspend fun askSelect(title: String, options: List<String>, selectedIndex: Int = 0, message: String? = null): Int? = HtmlDialogHelper().askSelect(title, options, selectedIndex, message)

suspend fun askSelectOption(title: String, options: List<String>, selectedIndex: Int = 0, message: String? = null): String? = HtmlDialogHelper().askSelectOption(title, options, selectedIndex, message)

private fun String.escapeHtml(): String = buildString(length) {
    for (ch in this@escapeHtml) {
        when (ch) {
            '&' -> append("&amp;")
            '<' -> append("&lt;")
            '>' -> append("&gt;")
            '"' -> append("&quot;")
            '\'' -> append("&#39;")
            else -> append(ch)
        }
    }
}
