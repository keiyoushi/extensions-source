package eu.kanade.tachiyomi.extension.es.codearc

import keiyoushi.utils.WebViewScope
import keiyoushi.utils.runWebView
import keiyoushi.utils.runWebViewBlocking
import keiyoushi.utils.toJsonString
import okhttp3.Call
import kotlin.time.Duration.Companion.seconds

internal class TurnstileException(message: String) : IllegalStateException(message)
internal class TurnstileInteractiveException(message: String) : IllegalStateException(message)

internal suspend fun fetchTurnstileToken(chapterUrl: String, sitekey: String): String = runWebView(timeout = 30.seconds) {
    loadReaderChallenge(chapterUrl, sitekey)
}

internal fun fetchTurnstileToken(chapterUrl: String, sitekey: String, call: Call): String = runWebViewBlocking(call, timeout = 30.seconds) {
    loadReaderChallenge(chapterUrl, sitekey)
}

private fun WebViewScope<String>.loadReaderChallenge(chapterUrl: String, sitekey: String) {
    jsBridge("turnstileToken") { resolve(it) }
    jsBridge("turnstileError") { reject(TurnstileException(it)) }
    jsBridge("turnstileInteractive") { reject(TurnstileInteractiveException(it)) }
    jsBridge("turnstileReady") { evaluateJs(turnstileRender(sitekey)) }
    // loadData(chapterUrl, """<script>window.turnstileInteractive.post("interactive")</script>""")
    loadData(chapterUrl, turnstilePage())
}

private fun turnstilePage(): String = """
    <html><body>
        <div id="challenge"></div>
        <script
            src="https://challenges.cloudflare.com/turnstile/v0/api.js?render=explicit"
            onload="window.turnstileReady.post('ready')"
            onerror="window.turnstileError.post(event.type)"></script>
    </body></html>
""".trimIndent()

private fun turnstileRender(sitekey: String): String = """
    turnstile.render("#challenge", {
        sitekey: ${sitekey.toJsonString()},
        action: "reader",
        callback: token => window.turnstileToken.post(token),
        "error-callback": error => window.turnstileError.post(error || "error"),
        "expired-callback": () => window.turnstileError.post("expired"),
        "before-interactive-callback": () => window.turnstileInteractive.post("interactive"),
        "unsupported-callback": () => window.turnstileError.post("unsupported"),
        "timeout-callback": () => window.turnstileError.post("timeout"),
    });
""".trimIndent()
