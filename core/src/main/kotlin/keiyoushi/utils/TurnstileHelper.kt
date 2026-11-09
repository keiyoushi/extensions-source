package keiyoushi.utils

import android.app.Activity
import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.WebView
import android.widget.FrameLayout
import keiyoushi.utils.ui.onDestroyed
import keiyoushi.utils.ui.topActivity
import keiyoushi.utils.ui.usable
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** A CSS selector for a Turnstile widget, for [WebViewScope.showToUser]. */
const val TURNSTILE_AREA = "div:has(> input[name=\"cf-turnstile-response\"])"

/**
 * Solves Cloudflare Turnstile challenges and returns the token.
 *
 * Runs headless with normal mobile viewport metrics, so it works while the app is in the
 * background. Only if user interaction is required does it display a full-screen dimmed overlay
 * on the foreground Activity; if there is none at that point, it fails instead.
 *
 * By default the page sees the reduced Chrome user agent of the installed WebView's own version,
 * so the claimed browser matches the engine. Turnstile tokens aren't tied to a user agent, so
 * the token can still be sent with a source's own User-Agent. Pass [userAgent] to override.
 */
suspend fun getTurnstileToken(
    url: String,
    siteKey: String,
    userAgent: String? = null,
    action: String? = null,
    cData: String? = null,
    timeout: Duration = 2.minutes,
): String {
    // The app's own night mode lives in the Activity configuration; fall back to the system's.
    val uiContext = foregroundActivity() ?: applicationContext
    val night = (uiContext.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val theme = if (night) "dark" else "light"

    // Turnstile's callbacks run in our page; keep iframes (including Cloudflare's) off the bridge.
    val pageOrigin = url.toHttpUrl().let {
        if (it.port == HttpUrl.defaultPort(it.scheme)) "${it.scheme}://${it.host}" else "${it.scheme}://${it.host}:${it.port}"
    }

    var dialog: CaptchaOverlayDialog? = null

    return try {
        runWebView(timeout = timeout) {
            (userAgent ?: reducedChromeUserAgent(this.userAgent))?.let { this.userAgent = it }

            val d = CaptchaOverlayDialog(
                webView = webView,
                transparent = true,
                onCancel = { reject(Exception("Captcha cancelled")) },
                onNoActivity = {
                    reject(IllegalStateException("Captcha needs interaction, but the app isn't in the foreground"))
                },
            )
            dialog = d

            webMessageBridge("turnstileBridge", setOf(pageOrigin)) { message ->
                when {
                    message.startsWith("token:") -> {
                        d.dismiss()
                        resolve(message.removePrefix("token:"))
                    }
                    message.startsWith("error:") -> {
                        val code = message.removePrefix("error:")
                        d.dismiss()
                        reject(Exception("Captcha Failed! ${turnstileErrorMessage(code)} (code: $code)"))
                    }
                    message == "cancel" -> {
                        d.dismiss()
                        reject(Exception("Captcha cancelled"))
                    }
                    message == "show" -> d.show()
                    message == "hide" -> d.dismiss()
                }
            }

            val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <link rel="icon" href="data:,">
                <style>
                    html, body {
                        margin: 0;
                        padding: 0;
                        width: 100%;
                        height: 100%;
                        background: transparent;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                    }
                    #challenge {
                        display: flex;
                        justify-content: center;
                        align-items: center;
                    }
                </style>
            </head>
            <body>
                <div id="challenge"></div>
                <script>
                    function onTurnstileLoad() {
                        const options = {
                            sitekey: ${siteKey.toJsonString()},
                            theme: '$theme',
                            appearance: 'interaction-only',
                            callback: function (token) {
                                window.turnstileBridge.postMessage('token:' + token);
                            },
                            'before-interactive-callback': function () {
                                window.turnstileBridge.postMessage('show');
                            },
                            'after-interactive-callback': function () {
                                window.turnstileBridge.postMessage('hide');
                            },
                            'error-callback': function (e) {
                                window.turnstileBridge.postMessage('error:' + String(e || 'unknown'));
                            },
                            'expired-callback': function () {
                                window.turnstileBridge.postMessage('error:expired');
                            },
                            'timeout-callback': function () {
                                window.turnstileBridge.postMessage('error:timeout');
                            },
                        };

                        const action = ${action.toJsonString()};
                        if (action !== null) options.action = action;

                        const cData = ${cData.toJsonString()};
                        if (cData !== null) options.cData = cData;

                        turnstile.render('#challenge', options);
                    }

                    document.body.addEventListener('click', function (e) {
                        if (e.target === document.body || e.target === document.documentElement) {
                            window.turnstileBridge.postMessage('cancel');
                        }
                    });
                </script>
                <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&amp;onload=onTurnstileLoad"
                        onerror="window.turnstileBridge.postMessage('error:script_load_failed')"></script>
            </body>
            </html>
            """.trimIndent()

            loadData(url, html)
        }
    } finally {
        dialog?.dismiss()
    }
}

// The WebView's default UA carries "; wv" and "Version/4.0"; real Chrome sends this reduced form.
private fun reducedChromeUserAgent(webViewUserAgent: String): String? {
    val major = Regex("""Chrome/(\d+)""").find(webViewUserAgent)?.groupValues?.get(1) ?: return null
    return "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$major.0.0.0 Mobile Safari/537.36"
}

internal fun foregroundActivity(): Activity? = runCatching { topActivity() }.getOrNull()?.takeIf { it.usable() }

/**
 * Shows [webView] over the foreground Activity, dimmed around it, for a check that wants the user's tap: all of it, or
 * only an area of it. A [transparent] WebView shows the app through it, for a page that is only the widget.
 * [onScreenGone] runs when the Activity it is shown on goes away, after it is hidden.
 */
internal class CaptchaOverlayDialog(
    private val webView: WebView,
    private val transparent: Boolean,
    private val onCancel: () -> Unit,
    private val onNoActivity: () -> Unit,
    private val onScreenGone: () -> Unit = onCancel,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private var dialog: Dialog? = null
    private var unhook: (() -> Unit)? = null
    private var placed: Rect? = null

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else mainHandler.post(block)
    }

    /**
     * Shows the WebView, or only its [area] (in the WebView's pixels at its off-screen size) when given; moves the
     * area if it is already shown.
     */
    fun show(area: Rect? = null) = runOnMain {
        if (dialog != null) {
            // The same area again leaves the view where it is, so a tap isn't lost
            if (area != placed) runCatching { place(area) }.onFailure { onCancel() }
            return@runOnMain
        }
        val activity = foregroundActivity() ?: return@runOnMain onNoActivity()

        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            if (transparent) webView.setBackgroundColor(Color.TRANSPARENT)

            val d = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar).apply {
                window?.apply {
                    setLayout(MATCH_PARENT, MATCH_PARENT)
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    setDimAmount(0.6f)
                }
                setOnCancelListener { onCancel() }
                setOnDismissListener { onCancel() }
            }
            dialog = d
            place(area)
            unhook = activity.onDestroyed {
                dismiss()
                onScreenGone()
            }
            d.show()
        } catch (_: Throwable) {
            onCancel()
        }
    }

    // The whole WebView, or a box of the area's size in the middle of the screen with the WebView moved under it
    private fun place(area: Rect?) {
        val d = dialog ?: return
        placed = area
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.translationX = 0f
        webView.translationY = 0f
        if (area == null || area.isEmpty) return d.setContentView(webView)
        val size = offscreenSize()
        val box = FrameLayout(d.context).apply {
            addView(webView, FrameLayout.LayoutParams(size.x, size.y))
            webView.translationX = -area.left.toFloat()
            webView.translationY = -area.top.toFloat()
        }
        val root = FrameLayout(d.context).apply {
            addView(box, FrameLayout.LayoutParams(area.width(), area.height(), Gravity.CENTER))
        }
        d.setContentView(root)
    }

    fun dismiss() = runOnMain {
        val d = dialog ?: return@runOnMain
        dialog = null
        placed = null
        unhook?.invoke()
        unhook = null
        try {
            d.setOnDismissListener(null)
            d.setOnCancelListener(null)
            runCatching { if (d.isShowing) d.dismiss() }
        } finally {
            runCatching { (webView.parent as? ViewGroup)?.removeView(webView) }
            webView.translationX = 0f
            webView.translationY = 0f
            layOutOffscreen(webView)
            // Leaving the window marked the page hidden, which stops its timers and lazy loads
            webView.dispatchWindowVisibilityChanged(View.VISIBLE)
        }
    }
}

private fun turnstileErrorMessage(code: String): String = when {
    code == "expired" -> "Expired"
    code == "timeout" -> "Timed out"
    code == "unsupported" -> "WebView doesn't support captcha"
    code == "script_load_failed" -> "Script not loaded"
    code == "110100" || code == "110110" || code == "400020" -> "Sitekey rejected"
    code == "110200" -> "Not authorized for this domain"
    code.startsWith("1106") -> "Timed out"
    code.startsWith("100") -> "Failed to initialize"
    code.startsWith("102") -> "Invalid parameters"
    code == "200100" -> "Wrong Device clock"
    code == "200500" -> "Captcha didn't load"
    code.startsWith("120") -> "Internal Cloudflare error"
    code.startsWith("300") -> "Challenge failed in this browser"
    code.startsWith("600") -> "Challenge not solved"
    else -> "Unknown error"
}
