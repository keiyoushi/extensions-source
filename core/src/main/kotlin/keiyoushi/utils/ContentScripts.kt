package keiyoushi.utils

import keiyoushi.webview.internal.WebViewGlueBridge
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/**
 * Scripts that run on a [runWebView] run's pages the way a browser extension's content scripts do: they share the
 * page's DOM, but have JavaScript objects of their own. The page's scripts can't see them, call them or wrap the
 * functions they use (`querySelector`, `fetch`, `performance`, ...). Create them with [WebViewScope.contentScripts].
 *
 * In the main frame, scripts have a `keiyoushi` object: `keiyoushi.send(channel, data)` reaches the [onMessage]
 * handlers for that channel, and `keiyoushi.on(channel, (data) => ...)` receives [send]. Data is anything
 * `JSON.stringify` takes; Kotlin gets and sends it as JSON text.
 * ```
 * runWebView<String> {
 *     val scripts = contentScripts(setOf("https://example.com"))
 *     onPageFinished { scripts.evaluate("document.title") { resolve(it.parseAs<String>()) } }
 *     loadUrl(url)
 * }
 * ```
 *
 * [add] and [onMessage] belong in the configure block; [evaluate] and [send] can be called from any thread. Their
 * callbacks and the [onMessage] handlers run on the main thread, and what they throw fails the run.
 *
 * Requires **Chromium 146+** (`JS_INJECTION_IN_FRAME_AND_WORLD`), with no fallback: on older engines creating them
 * throws [UnsupportedOperationException], failing the run. Check [isSupported] to choose another way first.
 */
class ContentScripts internal constructor(
    private val scope: WebViewScope<*>,
    private val allowedOriginRules: Set<String>,
) {
    // Main thread only, like everything else below
    private val handlers = HashMap<String, MutableList<(String) -> Unit>>()

    // The main frame's scripts, once they've said they're ready
    private var page: WebViewGlueBridge.ReplyProxy? = null

    // Evaluate calls not answered yet; each runs again on the next page if its page goes away first
    private val pending = mutableListOf<Evaluation>()

    private class Evaluation(val script: String, val callback: (String) -> Unit) {
        var page: WebViewGlueBridge.ReplyProxy? = null
    }

    init {
        val added = WebViewGlueBridge.addWebMessageListener(
            webView = scope.webView,
            jsObjectName = BRIDGE,
            allowedOriginRules = allowedOriginRules,
            worldName = WORLD,
            onError = scope::reject,
        ) { message, proxy ->
            if (scope.destroyed || proxy.isMainFrame != true) return@addWebMessageListener
            val (channel, data) = decodeContentScriptMessage(message) ?: return@addWebMessageListener
            try {
                if (channel == READY) pageReady(proxy) else handlers[channel]?.forEach { it(data) }
            } catch (t: Throwable) {
                scope.reject(t)
            }
        }
        if (!added) throw UnsupportedOperationException(UNSUPPORTED)
        add(PRELUDE)
    }

    /**
     * Runs [script] on every later page at [event], after the scripts added before it. It runs in every frame matching
     * the origin rules; `keiyoushi` exists only in the main frame.
     */
    fun add(script: String, event: InjectionEvent = InjectionEvent.DOCUMENT_START): ScriptHandle = WebViewGlueBridge.addJavaScriptOnEvent(scope.webView, script, event.value, WORLD, allowedOriginRules)
        ?: throw UnsupportedOperationException(UNSUPPORTED)

    /** Runs [handler] with the data of every `keiyoushi.send(channel, data)`. */
    fun onMessage(channel: String, handler: (data: String) -> Unit) {
        require(channel != READY) { "channel must not be empty" }
        handlers.getOrPut(channel) { mutableListOf() } += handler
    }

    /**
     * Sends [data], JSON text, to the current page's `keiyoushi.on(channel)` handlers, without waiting for an answer.
     * Dropped when no page is ready.
     */
    fun send(channel: String, data: String = "null") {
        require(channel != READY) { "channel must not be empty" }
        val message = encodeContentScriptMessage(channel, data)
        scope.runOnMain { page?.postMessage(message) }
    }

    /**
     * Runs the expression [script] in the main frame's content scripts and passes its value to [callback] as JSON text
     * (`null` for no value). Waits until a page's scripts are ready, and runs [script] again on the next page if this
     * one goes away first, so a script that navigates belongs in [send]. A promise isn't awaited; have the script
     * `keiyoushi.send` its result instead. If [script] throws, the run fails with a [ContentScriptException].
     */
    fun evaluate(script: String, callback: (String) -> Unit) {
        val evaluation = Evaluation(WRAPPER.replace(SCRIPT, script), callback)
        scope.runOnMain {
            pending += evaluation
            page?.let { run(evaluation, it) }
        }
    }

    private fun run(evaluation: Evaluation, proxy: WebViewGlueBridge.ReplyProxy) {
        evaluation.page = proxy
        proxy.execute(
            evaluation.script,
            onSuccess = { result -> finish(evaluation, proxy, decodeEvaluation(result)) },
            onFailure = { finish(evaluation, proxy, null) },
        )
    }

    // A null outcome means the page went away before the script finished
    private fun finish(evaluation: Evaluation, proxy: WebViewGlueBridge.ReplyProxy, outcome: EvaluationOutcome?) {
        if (evaluation.page !== proxy || evaluation !in pending) return
        if (outcome == null) {
            if (page === proxy) page = null
            return
        }
        pending -= evaluation
        try {
            outcome.error?.let { throw ContentScriptException(it) }
            evaluation.callback(outcome.value)
        } catch (t: Throwable) {
            scope.reject(t)
        }
    }

    private fun pageReady(proxy: WebViewGlueBridge.ReplyProxy) {
        page = proxy
        // A call still waiting on the old page never hears back from it
        pending.toList().forEach { run(it, proxy) }
    }

    companion object {
        /** Whether this WebView can run content scripts. */
        val isSupported: Boolean get() = WebViewGlueBridge.isFeatureSupported("JS_INJECTION_IN_FRAME_AND_WORLD")

        private const val WORLD = "keiyoushi-content-scripts"
        private const val BRIDGE = "keiyoushiContentScripts"
        private const val UNSUPPORTED = "Content scripts need Android System WebView 146 or newer"

        // Channel of the prelude's message that the page's scripts are ready
        private const val READY = ""

        private const val SCRIPT = "{{script}}"

        // An answer only a finished script gives, telling a thrown error apart from a page that went away
        private val WRAPPER = """
            (() => {
                try {
                    return { ok: true, value: ($SCRIPT) };
                } catch (e) {
                    return { error: String((e && e.stack) || e) };
                }
            })()
        """.trimIndent()

        // In the main frame: keeps the bridge to itself, gives scripts `keiyoushi`, says the page's scripts are ready
        private val PRELUDE = """
            if (window === window.top) {
                const bridge = globalThis.$BRIDGE;
                delete globalThis.$BRIDGE;
                const handlers = new Map();
                bridge.onmessage = (event) => {
                    const { c, d } = JSON.parse(event.data);
                    (handlers.get(c) || []).forEach((handler) => handler(d));
                };
                globalThis.keiyoushi = Object.freeze({
                    send: (channel, data) => bridge.postMessage(JSON.stringify({ c: String(channel), d: data === undefined ? null : data })),
                    on: (channel, handler) => handlers.set(String(channel), [...(handlers.get(String(channel)) || []), handler]),
                });
                bridge.postMessage(JSON.stringify({ c: '$READY' }));
            }
        """.trimIndent()
    }
}

/** A [ContentScripts.evaluate] script threw; [message] is its error's stack, or the error itself. */
class ContentScriptException internal constructor(message: String) : Exception(message)

internal class EvaluationOutcome(val value: String, val error: String?)

// The wrapper's answer, or null when there is none: the page went away before the script finished
internal fun decodeEvaluation(result: String?): EvaluationOutcome? {
    val answer = result?.let { runCatching { Json.parseToJsonElement(it) as? JsonObject }.getOrNull() } ?: return null
    (answer["error"] as? JsonPrimitive)?.contentOrNull?.let { return EvaluationOutcome("null", it) }
    if ((answer["ok"] as? JsonPrimitive)?.contentOrNull != "true") return null
    return EvaluationOutcome((answer["value"] ?: JsonNull).toString(), null)
}

internal fun encodeContentScriptMessage(channel: String, data: String): String = JsonObject(
    mapOf("c" to JsonPrimitive(channel), "d" to Json.parseToJsonElement(data)),
).toString()

internal fun decodeContentScriptMessage(message: String): Pair<String, String>? {
    val envelope = runCatching { Json.parseToJsonElement(message) as? JsonObject }.getOrNull() ?: return null
    val channel = (envelope["c"] as? JsonPrimitive)?.contentOrNull ?: return null
    val data: JsonElement = envelope["d"] ?: JsonNull
    return channel to data.toString()
}
