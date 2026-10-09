package keiyoushi.utils

import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.ui.BaseWebViewDialogHelper
import kotlin.time.Duration.Companion.minutes

/**
 * Solves Cloudflare Turnstile challenges and returns the token.
 *
 * Runs headless in the background first with normal mobile viewport metrics.
 * If user interaction is required, displays an HTML dialog for the challenge.
 */
class TurnstileHelper : BaseWebViewDialogHelper() {

    suspend fun getTurnstileToken(
        url: String,
        siteKey: String,
        userAgent: String,
    ): String {
        val html = """
            <style>#challenge { display: flex; justify-content: center; }</style>
            <h3 style="margin-top: 0; margin-bottom: 12px; text-align: center;">Captcha Required</h3>
            <div id="challenge"></div>
            <script>
                function onTurnstileLoad() {
                    turnstile.render('#challenge', {
                        sitekey: ${siteKey.toJsonString()},
                        theme: Dialog.theme,
                        appearance: 'interaction-only',
                        callback: function (t) { Dialog.submit(t); },
                        'before-interactive-callback': function () { Dialog.show(); },
                        'after-interactive-callback': function () { Dialog.hide(); },
                        'error-callback': function (e) { Dialog.error(String(e || 'unknown')); },
                        'expired-callback': function () { Dialog.error('expired'); },
                        'timeout-callback': function () { Dialog.error('timeout'); },
                    });
                }
            </script>
            <script src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit&amp;onload=onTurnstileLoad"
                    onerror="Dialog.error('script_load_failed')"></script>
        """.trimIndent()

        val token = try {
            runDialog(
                html = html,
                baseUrl = url,
                userAgent = userAgent,
                initiallyVisible = false,
                timeout = 2.minutes,
            )
        } catch (e: Exception) {
            val code = e.message ?: "unknown"
            if (code.startsWith("Activity") || e is WebViewTimeoutException || e is RenderProcessGoneException) throw e
            throw Exception("Captcha Failed! ${turnstileErrorMessage(code)} (code: $code)", e)
        }

        return token ?: throw Exception("Captcha cancelled")
    }

    context(source: HttpSource)
    suspend fun getTurnstileToken(url: String, siteKey: String): String = getTurnstileToken(
        url = url,
        siteKey = siteKey,
        userAgent = source.headers["User-Agent"]!!,
    )
}

private fun turnstileErrorMessage(code: String): String = when {
    code == "expired" -> "The captcha expired before it was used"
    code == "timeout" -> "The captcha timed out"
    code == "unsupported" -> "This WebView does not support the captcha"
    code == "script_load_failed" -> "The captcha script could not be loaded (network error or blocked)"
    code == "110100" || code == "110110" || code == "400020" -> "The captcha site key was rejected (invalid or not found)"
    code == "110200" -> "The captcha is not authorized for this domain"
    code.startsWith("1106") -> "The captcha challenge timed out"
    code.startsWith("100") -> "The captcha failed to initialize"
    code.startsWith("102") -> "The captcha received invalid parameters"
    code == "200100" -> "The device clock is wrong, or the challenge was cached"
    code == "200500" -> "The captcha frame could not load (network error or blocked)"
    code.startsWith("120") -> "The captcha hit an internal Cloudflare error"
    code.startsWith("300") -> "The captcha failed in the browser environment"
    code.startsWith("600") -> "The captcha challenge was not solved"
    else -> "Captcha error"
}
