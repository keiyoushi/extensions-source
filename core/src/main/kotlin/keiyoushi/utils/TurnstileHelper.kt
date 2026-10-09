package keiyoushi.utils

import android.app.Activity
import android.app.Dialog
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.WindowManager
import android.webkit.WebView
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.ui.onDestroyed
import keiyoushi.utils.ui.topActivity
import keiyoushi.utils.ui.usable
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Solves Cloudflare Turnstile challenges and returns the token.
 *
 * Runs headless in the background first with normal mobile viewport metrics.
 * If user interaction is required, displays a full-screen dimmed overlay on the foreground
 * Activity while keeping the WebView at the exact device dimensions.
 */
suspend fun getTurnstileToken(
    url: String,
    siteKey: String,
    userAgent: String,
    action: String? = null,
    cData: String? = null,
    timeout: Duration = 2.minutes,
): String {
    val activity = topActivity().takeIf { it.usable() }
        ?: throw IllegalStateException("Activity unavailable for captcha")

    val night = (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES
    val theme = if (night) "dark" else "light"

    var dialog: CaptchaOverlayDialog? = null

    return try {
        runWebView(activity = activity, timeout = timeout) {
            this.userAgent = userAgent

            val d = CaptchaOverlayDialog(
                activity = activity,
                webView = webView,
                onCancel = { reject(Exception("Captcha cancelled")) },
            )
            dialog = d

            jsBridge("turnstileToken") { token ->
                d.dismiss()
                resolve(token)
            }

            jsBridge("turnstileError") { code ->
                d.dismiss()
                reject(Exception("Captcha Failed! ${turnstileErrorMessage(code)} (code: $code)"))
            }

            jsBridge("turnstileCancel") {
                d.dismiss()
                reject(Exception("Captcha cancelled"))
            }

            jsBridge("turnstileShow") {
                d.show()
            }

            jsBridge("turnstileHide") {
                d.dismiss()
            }

            val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1">
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
                                window.turnstileToken.post(token);
                            },
                            'before-interactive-callback': function () {
                                window.turnstileShow.post('show');
                            },
                            'after-interactive-callback': function () {
                                window.turnstileHide.post('hide');
                            },
                            'error-callback': function (e) {
                                window.turnstileError.post(String(e || 'unknown'));
                            },
                            'expired-callback': function () {
                                window.turnstileError.post('expired');
                            },
                            'timeout-callback': function () {
                                window.turnstileError.post('timeout');
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
                            window.turnstileCancel.post('cancelled');
                        }
                    });
                </script>
                <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&amp;onload=onTurnstileLoad"
                        onerror="window.turnstileError.post('script_load_failed')"></script>
            </body>
            </html>
            """.trimIndent()

            loadData(url, html)
        }
    } finally {
        dialog?.dismiss()
    }
}

context(source: HttpSource)
suspend fun getTurnstileToken(
    url: String,
    siteKey: String,
    action: String? = null,
    cData: String? = null,
    timeout: Duration = 2.minutes,
): String = getTurnstileToken(
    url = url,
    siteKey = siteKey,
    userAgent = source.headers["User-Agent"]!!,
    action = action,
    cData = cData,
    timeout = timeout,
)

private class CaptchaOverlayDialog(
    private val activity: Activity,
    private val webView: WebView,
    private val onCancel: () -> Unit,
) {
    private var dialog: Dialog? = null
    private var unhook: (() -> Unit)? = null

    fun show() = activity.runOnUiThread {
        if (dialog != null || activity.isFinishing || activity.isDestroyed) return@runOnUiThread

        try {
            (webView.parent as? ViewGroup)?.removeView(webView)
            webView.setBackgroundColor(Color.TRANSPARENT)

            val d = Dialog(activity, android.R.style.Theme_Translucent_NoTitleBar).apply {
                window?.apply {
                    setLayout(MATCH_PARENT, MATCH_PARENT)
                    setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
                    addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                    setDimAmount(0.6f)
                }
                setContentView(webView)
                setOnCancelListener { onCancel() }
                setOnDismissListener { onCancel() }
            }
            dialog = d
            unhook = activity.onDestroyed {
                dismiss()
                onCancel()
            }
            d.show()
        } catch (_: Throwable) {
            onCancel()
        }
    }

    fun dismiss() = activity.runOnUiThread {
        val d = dialog ?: return@runOnUiThread
        dialog = null
        unhook?.invoke()
        unhook = null
        try {
            d.setOnDismissListener(null)
            d.setOnCancelListener(null)
            runCatching { if (d.isShowing) d.dismiss() }
        } finally {
            runCatching { (webView.parent as? ViewGroup)?.removeView(webView) }
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
