package keiyoushi.utils

import keiyoushi.webview.internal.WebViewGlueBridge
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Scripts that run on a [runWebView] run's pages the way a browser extension's content scripts do: they share the
 * page's DOM, but have JavaScript objects of their own. The page's scripts can't see them, call them or wrap the
 * functions they use (`querySelector`, `fetch`, `performance`, ...). Create them with [WebViewScope.contentScripts].
 *
 * Main frame only. Inside, scripts have a `keiyoushi` object: `keiyoushi.send(channel, data)` reaches the
 * [onMessage] handlers for that channel, and `keiyoushi.on(channel, (data) => ...)` receives [send]. Data is anything
 * `JSON.stringify` takes; Kotlin gets and sends it as JSON text.
 *
 * [evaluate] suspends, so call it from a coroutine of the source's while the run lives, with the object handed out of
 * the configure block:
 * ```
 * val scripts = CompletableDeferred<ContentScripts>()
 * launch { runWebView<Unit> { scripts.complete(contentScripts(setOf("https://example.com"))); loadUrl(url) } }
 * val title = scripts.await().evaluate("document.title")
 * ```
 *
 * Requires **Chromium 146+** (`JS_INJECTION_IN_FRAME_AND_WORLD`), with no fallback: on older engines creating them
 * throws [UnsupportedOperationException], failing the run. Check [isSupported] to choose another way first.
 */
class ContentScripts internal constructor(
    private val scope: WebViewScope<*>,
    private val allowedOriginRules: Set<String>,
) {
    private val handlers = ConcurrentHashMap<String, CopyOnWriteArrayList<(String) -> Unit>>()

    // Main thread only: the current page's scripts once they're ready, and the evaluate calls waiting on a page or a script
    private var page: WebViewGlueBridge.ReplyProxy? = null
    private val pageWaiters = mutableListOf<CancellableContinuation<WebViewGlueBridge.ReplyProxy>>()
    private val running = mutableSetOf<CancellableContinuation<String?>>()
    private var ended = false

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
        if (!added || add(PRELUDE) == null) throw UnsupportedOperationException("Content scripts need Android System WebView 146 or newer")
        scope.endHooks += ::end
    }

    /** Runs [script] on every later page at [event], after the scripts added before it. Null if unsupported. */
    fun add(script: String, event: InjectionEvent = InjectionEvent.DOCUMENT_START): ScriptHandle? = WebViewGlueBridge.addJavaScriptOnEvent(scope.webView, script, event.value, WORLD, allowedOriginRules)

    /** Runs [handler] on the main thread with the data of every `keiyoushi.send(channel, data)`. */
    fun onMessage(channel: String, handler: (data: String) -> Unit) {
        require(channel != READY) { "channel must not be empty" }
        handlers.getOrPut(channel) { CopyOnWriteArrayList() } += handler
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
     * Runs [script] in the current page's content scripts and returns its result as JSON text (`null` for no value).
     * Waits until a page's scripts are ready, and runs [script] again on the next page if this one goes away first, so
     * a script that navigates belongs in [send]. Throws a [ContentScriptException] when [script] throws, and an
     * [IllegalStateException] once the run has ended.
     */
    suspend fun evaluate(script: String): String = withContext(Dispatchers.Main) {
        while (true) {
            check(!ended) { RUN_ENDED }
            val proxy = page ?: suspendCancellableCoroutine { continuation ->
                pageWaiters += continuation
                continuation.invokeOnCancellation { scope.runOnMain { pageWaiters -= continuation } }
            }
            val result = suspendCancellableCoroutine { continuation ->
                running += continuation
                continuation.invokeOnCancellation { scope.runOnMain { running -= continuation } }
                proxy.execute(
                    script,
                    onSuccess = {
                        running -= continuation
                        continuation.resume(it ?: "null")
                    },
                    onFailure = { pageGone, message ->
                        running -= continuation
                        // Null: the page went away before answering
                        if (pageGone) continuation.resume(null) else continuation.resumeWithException(ContentScriptException(message))
                    },
                )
            }
            if (result != null) return@withContext result
            if (page === proxy) page = null
        }
        @Suppress("UNREACHABLE_CODE")
        error("unreachable")
    }

    private fun pageReady(proxy: WebViewGlueBridge.ReplyProxy) {
        page = proxy
        pageWaiters.toList().forEach { it.resume(proxy) }
        pageWaiters.clear()
    }

    // Cancelled calls ignore their script's late callback
    private fun end() {
        ended = true
        page = null
        val error = IllegalStateException(RUN_ENDED)
        (pageWaiters + running).forEach { it.cancel(error) }
        pageWaiters.clear()
        running.clear()
    }

    companion object {
        /** Whether this WebView can run content scripts. */
        val isSupported: Boolean get() = WebViewGlueBridge.isFeatureSupported("JS_INJECTION_IN_FRAME_AND_WORLD")

        private const val WORLD = "keiyoushi-content-scripts"
        private const val BRIDGE = "keiyoushiContentScripts"
        private const val RUN_ENDED = "The WebView run has ended"

        // Channel of the prelude's message that the page's scripts are ready
        private const val READY = ""

        // Removes the bridge's global, so only the prelude can post through it
        private val PRELUDE = """
            (() => {
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
            })();
        """.trimIndent()
    }
}

/** A content script's [ContentScripts.evaluate] script threw; [message] is the WebView's. */
class ContentScriptException internal constructor(message: String?) : Exception(message)

internal fun encodeContentScriptMessage(channel: String, data: String): String = JsonObject(
    mapOf("c" to JsonPrimitive(channel), "d" to Json.parseToJsonElement(data)),
).toString()

internal fun decodeContentScriptMessage(message: String): Pair<String, String>? {
    val envelope = runCatching { Json.parseToJsonElement(message).jsonObject }.getOrNull() ?: return null
    val channel = (envelope["c"] as? JsonPrimitive)?.contentOrNull ?: return null
    val data: JsonElement = envelope["d"] ?: JsonNull
    return channel to data.toString()
}
