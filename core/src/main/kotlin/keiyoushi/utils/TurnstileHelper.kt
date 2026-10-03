package keiyoushi.utils

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.webkit.WebView
import android.widget.FrameLayout
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.ui.ActivityTrackingHelper
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.minutes

class TurnstileHelper : ActivityTrackingHelper() {

    suspend fun getTurnstileToken(url: String, siteKey: String, userAgent: String): String {
        val captchaRef = AtomicReference<CaptchaDialog?>(null)
        val cssHeight = AtomicInteger(65)

        return try {
            runWebView(3.minutes) {
                this.userAgent = userAgent

                onDispose {
                    captchaRef.get()?.dismiss()
                    getAndroidWebView().also {
                        (it.parent as? ViewGroup)?.removeView(it)
                    }
                }

                jsBridge("turnstileToken") { resolve(it) }

                jsBridge("turnstileError") { reject(Exception("Captcha Failed! Error: $it")) }

                jsBridge("turnstileResize") {
                    it.toIntOrNull()?.let { h ->
                        cssHeight.set(h)
                        captchaRef.get()?.resize(h)
                    }
                }

                jsBridge("turnstileInteractive") {
                    captchaRef.set(
                        CaptchaDialog(
                            activity = topActivity(),
                            webView = getAndroidWebView(),
                            initialCssHeight = cssHeight.get(),
                            onCancel = { reject(Exception("Captcha cancelled")) },
                            onError = { reject(Exception("Captcha dialog failed", it)) },
                        ).also { it.show() },
                    )
                }

                jsBridge("turnstileInteractiveDone") {
                    captchaRef.get()?.dismiss()
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
                            "after-interactive-callback": () => window.turnstileInteractiveDone.post("done"),
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
            captchaRef.get()?.dismiss()
        }
    }

    context(source: HttpSource)
    suspend fun getTurnstileToken(url: String, siteKey: String) = getTurnstileToken(url, siteKey, source.headers["User-Agent"]!!)
}

private class CaptchaDialog(
    private val activity: Activity,
    private val webView: WebView,
    initialCssHeight: Int,
    private val onCancel: () -> Unit,
    private val onError: (Throwable) -> Unit,
) {
    private var dialog: AlertDialog? = null
    private var holder: FrameLayout? = null

    @Volatile
    private var closing = false
    private val initialHeight = initialCssHeight.coerceIn(65, 400)

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
                addView(webView, FrameLayout.LayoutParams(MATCH_PARENT, dp(initialHeight)))
            }
            holder = frame

            dialog = AlertDialog.Builder(activity)
                .setTitle("Captcha Required!")
                .setView(frame)
                .setOnDismissListener {
                    holder?.removeView(webView)
                    holder = null
                    dialog = null
                    if (!closing) onCancel()
                }
                .show()
        } catch (t: Throwable) {
            closing = true
            holder?.removeView(webView)
            holder = null
            dialog = null
            onError(t)
        }
    }

    fun resize(cssPx: Int) = activity.runOnUiThread {
        webView.layoutParams = webView.layoutParams?.apply {
            height = dp(cssPx.coerceIn(65, 400))
        }
    }

    fun dismiss() = activity.runOnUiThread {
        closing = true
        dialog?.takeIf { it.isShowing }?.dismiss()
    }

    private fun dp(value: Int) = (value * activity.resources.displayMetrics.density).toInt()
}
