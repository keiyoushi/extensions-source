package keiyoushi.utils

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import android.widget.FrameLayout
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.ui.ActivityTrackingHelper
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes

private const val CAPTCHA_MIN_HEIGHT_DP = 65
private const val CAPTCHA_MAX_HEIGHT_DP = 400

/**
 * Solves Cloudflare Turnstile challenges inside a headless [WebView] and returns the token.
 *
 * Turnstile is rendered with `appearance: "interaction-only"`, so most challenges pass
 * silently. If Cloudflare needs user input, the WebView is moved into an [AlertDialog] on the
 * foreground Activity, and removed again when the challenge finishes.
 *
 * Activity lookup comes from [ActivityTrackingHelper].
 */
class TurnstileHelper : ActivityTrackingHelper() {

    private val mainHandler = Handler(Looper.getMainLooper())

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    /**
     * Loads a minimal page that renders a Turnstile widget and suspends until it yields a token.
     *
     * @param url Origin the challenge runs under. It is used as the page's base URL, so it should
     * be the site the [siteKey] is registered for.
     * @param siteKey The Turnstile site key for [url].
     * @param userAgent User agent to use for the WebView. It should match the one used for the
     * requests that will present the token, since Cloudflare may bind the two together.
     * @return The Turnstile token.
     * @throws Exception with message `"Captcha Failed! <description> (code: <code>)"` if Turnstile
     * reports an error. `<code>` is the Turnstile error code, or one of `expired`, `timeout`,
     * `unsupported`, `script_load_failed` or `unknown`.
     * @throws Exception with message `"Captcha cancelled"` if the user dismisses the dialog.
     * @throws Exception if no usable Activity is available or the dialog cannot be shown, for
     * interactive challenges only.
     * @throws WebViewTimeoutException if no token is produced within 2 minutes.
     * @throws RenderProcessGoneException if the WebView renderer crashes or is killed.
     */
    suspend fun getTurnstileToken(url: String, siteKey: String, userAgent: String): String {
        val dialogRef = AtomicReference<CaptchaDialog?>(null)
        val cssHeight = AtomicInteger(CAPTCHA_MIN_HEIGHT_DP)
        val disposed = AtomicBoolean(false)

        return try {
            runWebView(3.minutes) {
                this.userAgent = userAgent

                onDispose {
                    disposed.set(true)
                    dialogRef.get()?.dismiss()
                }

                jsBridge("turnstileToken") { resolve(it) }

                jsBridge("turnstileError") { reject(Exception("Captcha Failed! ${turnstileErrorMessage(it)} (code: $it)")) }

                jsBridge("turnstileResize") {
                    it.toIntOrNull()?.let { h ->
                        cssHeight.set(h)
                        dialogRef.get()?.resize(h)
                    }
                }

                jsBridge("turnstileInteractive") {
                    runOnMain {
                        if (disposed.get()) return@runOnMain
                        try {
                            val dialog = CaptchaDialog(
                                activity = topActivity(),
                                webView = getAndroidWebView(),
                                initialCssHeight = { cssHeight.get() },
                                onCancel = { reject(Exception("Captcha cancelled")) },
                                onError = { reject(Exception("Captcha dialog failed", it)) },
                            )
                            dialogRef.set(dialog)
                            dialog.show()
                        } catch (t: Throwable) {
                            reject(t)
                        }
                    }
                }

                jsBridge("turnstileInteractiveEnd") {
                    runOnMain { dialogRef.get()?.dismiss() }
                }

                jsBridge("turnstileReady") {
                    evaluateJs(
                        """
                        turnstile.render("#challenge", {
                            sitekey: ${siteKey.toJsonString()},
                            appearance: "interaction-only",
                            callback: token => window.turnstileToken.post(token),
                            "error-callback": error => window.turnstileError.post(error || "unknown"),
                            "expired-callback": () => window.turnstileError.post("expired"),
                            "before-interactive-callback": () => window.turnstileInteractive.post("interactive"),
                            "after-interactive-callback": () => window.turnstileInteractiveEnd.post("end"),
                            "unsupported-callback": () => window.turnstileError.post("unsupported"),
                            "timeout-callback": () => window.turnstileError.post("timeout"),
                        });
                        """.trimIndent(),
                    )
                }

                loadData(
                    url,
                    """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1">
                        <style>
                            html, body { margin: 0; background: transparent; }
                            body { display: flex; justify-content: center; align-items: flex-start; }
                            #challenge { align-self: flex-start; }
                        </style>
                    </head>
                    <body>
                        <div id="challenge"></div>
                        <script
                            src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"
                            onload="window.turnstileReady.post('ready')"
                            onerror="window.turnstileError.post('script_load_failed')"></script>
                        <script>
                            const challenge = document.getElementById('challenge');
                            new ResizeObserver(() => {
                                const h = Math.ceil(challenge.getBoundingClientRect().height);
                                if (h > 0) window.turnstileResize.post(String(h));
                            }).observe(challenge);
                        </script>
                    </body>
                    </html>
                    """.trimIndent(),
                )
            }
        } finally {
            dialogRef.get()?.dismiss()
        }
    }

    context(source: HttpSource)
    suspend fun getTurnstileToken(url: String, siteKey: String) = getTurnstileToken(url, siteKey, source.headers["User-Agent"]!!)

    private fun turnstileErrorMessage(code: String): String = when {
        // Our own codes
        code == "expired" -> "The captcha expired before it was used"
        code == "timeout" -> "The captcha timed out"
        code == "unsupported" -> "This WebView does not support the captcha"
        code == "script_load_failed" -> "The captcha script could not be loaded (network error or blocked)"
        code == "unknown" -> "Unknown captcha error"

        // Configuration errors: the site key or domain is wrong, retrying won't help
        code == "110100" || code == "110110" || code == "400020" -> "The captcha site key was rejected (invalid or not found)"
        code == "110200" -> "The captcha is not authorized for this domain"

        // Timeouts (challenge / interaction), retryable
        code.startsWith("1106") -> "The captcha challenge timed out"

        code.startsWith("100") -> "The captcha failed to initialize"
        code.startsWith("102") -> "The captcha received invalid parameters"
        code == "200100" -> "The device clock is wrong, or the challenge was cached"
        code == "200500" -> "The captcha frame could not load (network error or blocked)"
        code.startsWith("120") -> "The captcha hit an internal Cloudflare error"
        code.startsWith("300") -> "The captcha failed in the browser environment"
        code.startsWith("600") -> "The captcha challenge was not solved"

        else -> "unknown error"
    }
}

/**
 * Hosts [webView] in an [AlertDialog]. Owns the WebView's placement while the dialog exists: it
 * attaches the WebView when shown and detaches it again when the dialog closes or [dismiss] is
 * called, so the owner can safely destroy the WebView afterward.
 *
 * @param initialCssHeight Read when the dialog is shown, so a resize that arrived earlier is not
 * lost.
 * @param onCancel Called when the user dismisses the dialog (back press, tapping outside).
 * Not called after [dismiss].
 * @param onError Called if the dialog cannot be shown.
 */
private class CaptchaDialog(
    private val activity: Activity,
    private val webView: WebView,
    private val initialCssHeight: () -> Int,
    private val onCancel: () -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    private var dialog: AlertDialog? = null
    private var holder: FrameLayout? = null

    @Volatile
    private var closing = false

    fun show() = activity.runOnUiThread {
        if (dialog != null || closing) return@runOnUiThread

        if (activity.isFinishing || activity.isDestroyed) {
            onError(IllegalStateException("Activity unavailable for captcha"))
            return@runOnUiThread
        }

        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.setBackgroundColor(Color.TRANSPARENT)

            val frame = FrameLayout(activity).apply {
                setPadding(dp(8), dp(8), dp(8), dp(16))
                addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, dp(clampHeight(initialCssHeight()))))
            }
            holder = frame

            dialog = AlertDialog.Builder(activity)
                .setTitle("Captcha Required!")
                .setView(frame)
                .setOnDismissListener {
                    releaseWebView()
                    dialog = null
                    if (!closing) onCancel()
                }
                .show()
        } catch (t: Throwable) {
            closing = true
            releaseWebView()
            dialog = null
            onError(t)
        }
    }

    fun resize(cssPx: Int) = activity.runOnUiThread {
        if (holder == null) return@runOnUiThread
        webView.layoutParams = webView.layoutParams?.apply {
            height = dp(clampHeight(cssPx))
        }
    }

    fun dismiss() = activity.runOnUiThread {
        closing = true
        dialog?.takeIf { it.isShowing }?.dismiss()
        releaseWebView()
    }

    private fun releaseWebView() {
        holder?.removeView(webView)
        holder = null
    }

    private fun clampHeight(cssPx: Int) = cssPx.coerceIn(CAPTCHA_MIN_HEIGHT_DP, CAPTCHA_MAX_HEIGHT_DP)

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
