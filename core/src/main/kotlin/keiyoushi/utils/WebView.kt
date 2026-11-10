package keiyoushi.utils

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.webview.internal.WebViewGlueBridge
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
import okhttp3.Headers.Companion.toHeaders
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CopyOnWriteArraySet
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/** Thrown when the WebView's render process crashes or is killed mid-run. */
class RenderProcessGoneException internal constructor(
    didCrash: Boolean,
) : Exception(
    if (didCrash) "WebView render process crashed" else "WebView render process was killed",
)

/**
 * Thrown by [runWebView] on timeout. Deliberately a plain [Exception] rather than a
 * [kotlinx.coroutines.CancellationException], so callers see a failure instead of a
 * silently swallowed cancellation.
 */
class WebViewTimeoutException internal constructor(
    timeout: Duration,
) : Exception("Timed out waiting for WebView after $timeout")

/** Lifecycle event timing for [WebViewScope.addJavaScriptOnEvent]. */
enum class InjectionEvent(internal val value: Int) {
    /** Execute before any script in the document executes. */
    DOCUMENT_START(0),

    /** Execute after the document has finished parsing. */
    DOCUMENT_END(1),
}

/** Handle for removing an injected document script. */
fun interface ScriptHandle {
    fun remove()
}

/**
 * The frame that sent a web message: where it came from, and how to reply to it. Its methods can be called from any
 * thread. It belongs to the page that sent the message: when the frame navigates, calls may be dropped without an
 * answer, so don't wait on a callback without a timeout. Have the new page send a message to get its proxy.
 */
interface WebMessageReplyProxy {
    /**
     * Origin of the sending frame (e.g. `https://example.com`), or null on the pre-Chromium 116
     * fallback, which can't tell senders apart.
     */
    val sourceOrigin: String?

    /** Whether the message came from the main frame, or null on the pre-Chromium 116 fallback. */
    val isMainFrame: Boolean?

    /** Posts [message] back to the frame that originated the message. */
    fun postMessage(message: String)

    /**
     * Executes [script] specifically inside the frame that sent the message, and passes its value to [callback] as
     * JSON text. A failed script or a page that went away gives null, or the text `null`. Promises aren't awaited.
     */
    fun evaluateJs(script: String, callback: ((String?) -> Unit)? = null)
}

/**
 * DSL for configuring and driving a single [runWebView] run.
 *
 * Threading: page/navigation hooks, [poll] and [webMessageBridge] handlers run on the main
 * thread; [interceptRequest] handlers (and [webMessageBridge] handlers on the pre-Chromium 116
 * fallback) run on WebView background threads. An exception thrown from any
 * callback fails the run via [reject].
 */
@SuppressLint("SetJavaScriptEnabled")
class WebViewScope<T> internal constructor(
    internal val webView: WebView,
    private val deferred: CompletableDeferred<T>,
) {
    internal val pageStartedHooks = CopyOnWriteArrayList<(String) -> Unit>()
    internal val pageFinishedHooks = CopyOnWriteArrayList<(String) -> Unit>()

    // Pages proxyRequest answered with a refresh to a redirect's target; the page hooks skip them
    internal val refreshPages: MutableSet<String> = ConcurrentHashMap.newKeySet()
    internal val receivedErrorHooks = CopyOnWriteArrayList<(WebResourceRequest, WebResourceError) -> Unit>()
    private val bridgeNames = CopyOnWriteArraySet<String>()

    @Volatile
    internal var interceptHook: ((WebResourceRequest) -> WebResourceResponse?)? = null

    @Volatile
    internal var destroyed = false

    @Volatile
    internal var renderDead = false

    private val loaded = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Completes [runWebView] with [value]. First completion wins; later calls are no-ops. */
    fun resolve(value: T) {
        deferred.complete(value)
    }

    /** Completes [runWebView] by throwing [error]. No-op if already completed. */
    fun reject(error: Throwable) {
        deferred.completeExceptionally(error)
    }

    var javaScriptEnabled: Boolean by setting({ javaScriptEnabled }, { javaScriptEnabled = it })
    var domStorageEnabled: Boolean by setting({ domStorageEnabled }, { domStorageEnabled = it })
    var blockImages: Boolean by setting({ blockNetworkImage }, { blockNetworkImage = it })
    var useWideViewPort: Boolean by setting({ useWideViewPort }, { useWideViewPort = it })
    var loadWithOverviewMode: Boolean by setting({ loadWithOverviewMode }, { loadWithOverviewMode = it })

    /**
     * Sets the user agent string and spoofs matching `Sec-CH-UA` client hints.
     *
     * Spoofing client hints requires **Chromium 119+** (`USER_AGENT_METADATA`). On older engines,
     * only the standard user agent string is modified.
     */
    var userAgent: String
        get() = webView.settings.userAgentString
        set(value) {
            webView.settings.userAgentString = value
            WebViewGlueBridge.setClientHintsFromUserAgent(webView.settings, value)
        }

    /**
     * Sends [name] with [value] on every request to an origin matching [allowedOriginRules], such as
     * `https://example.com`, in place of any value WebView sets itself. WebView can replace a header this way, not
     * remove it. Calling this again with the same [value] replaces its rules; with another value for a matching
     * origin, WebView sends both, comma-separated.
     *
     * The header is set on the WebView's profile, so it stays for every WebView of the app until the app is closed.
     * Requires **Chromium 142+** (`CUSTOM_REQUEST_HEADERS`, see [originMatchedHeaderSupported]). On older WebView
     * versions it immediately fails ([reject]s).
     */
    fun setOriginMatchedHeader(name: String, value: String, allowedOriginRules: Set<String>) {
        if (WebViewGlueBridge.setOriginMatchedHeader(webView, name, value, allowedOriginRules)) return
        reject(UnsupportedOperationException("CUSTOM_REQUEST_HEADERS is not supported on this WebView (requires Chromium 142+)"))
    }

    /** Whether [setOriginMatchedHeader] is supported on this WebView. */
    val originMatchedHeaderSupported: Boolean
        get() = WebViewGlueBridge.isOriginMatchedHeaderSupported()

    /** Runs [block] on every navigation start. */
    fun onPageStarted(block: (url: String) -> Unit) {
        pageStartedHooks += { url ->
            try {
                block(url)
            } catch (t: Throwable) {
                reject(t)
            }
        }
    }

    /**
     * Runs [block] on every navigation finish. Note that this can fire multiple times for a
     * single page (redirects, SPA navigations, iframes).
     */
    fun onPageFinished(block: (url: String) -> Unit) {
        pageFinishedHooks += { url ->
            try {
                block(url)
            } catch (t: Throwable) {
                reject(t)
            }
        }
    }

    /** Runs [block] on navigation failures (DNS/SSL/network errors). */
    fun onReceivedError(block: (request: WebResourceRequest, error: WebResourceError) -> Unit) {
        receivedErrorHooks += { request, error ->
            try {
                block(request, error)
            } catch (t: Throwable) {
                reject(t)
            }
        }
    }

    /**
     * Registers [block] to inspect/replace/block every resource request; return null to let a
     * request proceed normally. Runs on a WebView background thread. Can only be registered once.
     */
    fun interceptRequest(block: (WebResourceRequest) -> WebResourceResponse?) {
        check(interceptHook == null) { "interceptRequest already registered" }
        interceptHook = { request ->
            try {
                block(request)
            } catch (t: Throwable) {
                reject(t)
                null
            }
        }
    }

    /**
     * Sends one of the WebView's GETs through [client] instead and hands the response to the WebView; call it from
     * [interceptRequest]. The request has the headers WebView passes to [interceptRequest] (WebView adds its own
     * `X-Requested-With` later, so it isn't among them) and the [Fetch] of what it loads, both open to change in
     * [edit], e.g. `.fetch(Fetch.SCRIPT)` for a script without a `.js` name. [client], a KeiSource client, adds the
     * browser headers WebView would (`Sec-Fetch-*`, `Accept-Language`, `Priority`) and shares WebView's cookies.
     *
     * Returns null for what it leaves to the WebView: other methods, frames other than the page, stylesheets and
     * fonts. A redirected page is loaded at its new address. The page fails the run when it can't be loaded or comes
     * back with a status WebView refuses; error pages (4xx, 5xx) go to the WebView like any other.
     */
    fun proxyRequest(
        request: WebResourceRequest,
        client: OkHttpClient,
        edit: Request.Builder.() -> Unit = {},
    ): WebResourceResponse? {
        // The run is over: nothing more to send
        if (destroyed || deferred.isCompleted) return WebResourceResponse(null, null, null)
        val call = request.toOkHttpRequest()?.apply(edit)?.build() ?: return null

        // A page that fails fails the run; anything else the WebView sees as a failed load
        fun failed(error: IOException): WebResourceResponse {
            if (request.isForMainFrame) reject(error)
            return WebResourceResponse(null, null, null)
        }

        val response = try {
            // The WebView doesn't follow a redirect it is handed
            (if (request.isForMainFrame) client.newBuilder().followRedirects(false).build() else client).newCall(call).execute()
        } catch (e: IOException) {
            return failed(e)
        }
        if (request.isForMainFrame && response.isRedirect) {
            val target = response.header("Location")?.let { response.request.url.resolve(it) }
            response.close()
            target ?: return failed(IOException("HTTP ${response.code} without a location"))
            // A page that moves on to the new address, sending no Referer, as after a redirect
            val url = target.toString().replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")
            val html = "<meta name=\"referrer\" content=\"no-referrer\"><meta http-equiv=\"refresh\" content=\"0;url=$url\">"
            refreshPages += request.url.toString()
            return WebResourceResponse("text/html", "utf-8", ByteArrayInputStream(html.toByteArray()))
        }
        return response.toWebResourceResponse() ?: run {
            response.close()
            failed(IOException("HTTP ${response.code}"))
        }
    }

    /**
     * The OkHttp request for a WebView GET, to send through a source's own client: WebView's headers and the [Fetch]
     * of what it loads, so a KeiSource client adds the browser headers WebView would (`Sec-Fetch-*`, `Accept-Language`,
     * `Priority`). WebView's revalidation headers are left out; the client's own cache revalidates.
     *
     * Returns null for what core has no [Fetch] for: other methods, frames other than the page, stylesheets and fonts.
     */
    private fun WebResourceRequest.toOkHttpRequest(): Request.Builder? {
        val headers = requestHeaders.toHeaders()
        val fetch = proxyFetch(method, url.path.orEmpty(), headers["Accept"].orEmpty(), isForMainFrame) ?: return null
        return Request.Builder()
            .url(url.toString())
            .headers(
                headers.newBuilder()
                    .removeAll("If-None-Match")
                    .removeAll("If-Modified-Since")
                    // A page the WebView was sent to, not one a page linked to
                    .apply { if (isForMainFrame && get("Referer") == null) add("Sec-Fetch-Site", "none") }
                    .build(),
            )
            .fetch(fetch)
    }

    /**
     * The response for WebView to load, from a request the client sent for it. Drops the headers that no longer apply:
     * the body comes decoded, and a KeiSource client's cookie jar is WebView's own. Returns null for the codes WebView
     * refuses, redirects and 304 among them.
     */
    private fun Response.toWebResourceResponse(): WebResourceResponse? {
        if (code !in 100..299 && code !in 400..599) return null
        val type = body.contentType()
        val kept = headers.newBuilder()
            .removeAll("Content-Encoding")
            .removeAll("Content-Length")
            .removeAll("Set-Cookie")
            .build()
            .toMultimap()
            .mapValues { (_, values) -> values.joinToString() }
        return WebResourceResponse(
            type?.let { "${it.type}/${it.subtype}" },
            type?.charset()?.name(),
            code,
            // WebView refuses an empty reason phrase, which HTTP/2 responses have
            message.ifEmpty { if (isSuccessful) "OK" else "Error" },
            kept,
            body.byteStream(),
        )
    }

    /**
     * Injects `window.$name` exposing modern, asynchronous, origin-scoped 2-way messaging.
     * JavaScript sends: `window.$name.postMessage(message)`.
     * JavaScript receives: `window.$name.onmessage = (event) => ...`.
     * Must be called before [loadUrl]/[loadData]; the object is only injected on later navigations.
     * [allowedOriginRules] entries need a scheme (e.g. `https://example.com`); WebView rejects
     * invalid rules with [IllegalArgumentException], failing the run.
     *
     * With the default `*` rule, every frame on the page (including third-party iframes such as
     * ads) can post messages. Restrict [allowedOriginRules] to the expected origin, and check
     * [WebMessageReplyProxy.sourceOrigin]/[WebMessageReplyProxy.isMainFrame] if a message can
     * complete the run.
     *
     * Requires **Chromium 116+** (`WEB_MESSAGE_ARRAY_BUFFER`) for native origin-scoped messaging.
     * On older engines (< Chromium 116), transparently falls back to an `addJavascriptInterface`
     * bridge exposing `window.$name.postMessage(message)` with debug logging under tag `"KeiyoushiWebView"`.
     * Replying via [WebMessageReplyProxy] is unsupported on the fallback path and will [reject] the execution.
     * The fallback ignores [allowedOriginRules] (every frame can post) and reports a null
     * sender origin.
     *
     * [handler] runs on the main thread (on a background thread on the fallback path).
     * [WebMessageReplyProxy.evaluateJs] additionally requires **Chromium 146+**
     * (`JS_INJECTION_IN_FRAME_AND_WORLD`) and [reject]s otherwise.
     *
     * With a [worldName], the object exists only in that isolated world, not in the page's JavaScript, so the page can't
     * see it or post through it. Scripts reach it by being injected into the same world with [addJavaScriptOnEvent]; they
     * share the page's DOM but none of its JavaScript. Requires **Chromium 146+** (`JS_INJECTION_IN_FRAME_AND_WORLD`),
     * with **no fallback**: it [reject]s if unsupported, as the fallback would expose the object to the page.
     */
    fun webMessageBridge(
        name: String,
        allowedOriginRules: Set<String> = setOf("*"),
        worldName: String = "",
        handler: (message: String, replyProxy: WebMessageReplyProxy) -> Unit,
    ) {
        check(bridgeNames.add(name)) { "Bridge '$name' has already been registered on this WebView" }
        val added = WebViewGlueBridge.addWebMessageListener(
            webView = webView,
            jsObjectName = name,
            allowedOriginRules = allowedOriginRules,
            worldName = worldName,
            onError = { reject(it) },
        ) { message, replyProxy ->
            if (destroyed) return@addWebMessageListener
            try {
                handler(message, replyProxy)
            } catch (t: Throwable) {
                reject(t)
            }
        }
        if (added) return
        if (worldName.isNotEmpty()) {
            reject(UnsupportedOperationException("webMessageBridge in a world requires JS_INJECTION_IN_FRAME_AND_WORLD (Chromium 146+)"))
            return
        }

        Log.d("KeiyoushiWebView", "Native web messaging not supported; falling back to addJavascriptInterface for '$name'")

        val fallbackReplyProxy = object : WebMessageReplyProxy {
            override val sourceOrigin: String? = null
            override val isMainFrame: Boolean? = null

            override fun postMessage(message: String) {
                reject(UnsupportedOperationException("WebMessageReplyProxy.postMessage is not supported on this WebView (requires Chromium 116+)"))
            }

            override fun evaluateJs(script: String, callback: ((String?) -> Unit)?) {
                reject(UnsupportedOperationException("WebMessageReplyProxy.evaluateJs is not supported on this WebView (requires Chromium 116+)"))
            }
        }

        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun postMessage(message: String) {
                    if (destroyed) return
                    try {
                        handler(message, fallbackReplyProxy)
                    } catch (t: Throwable) {
                        reject(t)
                    }
                }
            },
            name,
        )
    }

    /**
     * Simplified [webMessageBridge] overload when reply capabilities are not needed.
     */
    fun webMessageBridge(
        name: String,
        allowedOriginRules: Set<String> = setOf("*"),
        worldName: String = "",
        handler: (message: String) -> Unit,
    ) = webMessageBridge(name, allowedOriginRules, worldName) { message, _ -> handler(message) }

    /**
     * Injects [script] into matching origins before any document or inline script executes.
     * Returns a [ScriptHandle] to cancel future injections. Must be called before
     * [loadUrl]/[loadData]; it only applies to later navigations. Invalid [allowedOriginRules]
     * entries (e.g. missing scheme) are rejected with [IllegalArgumentException], failing the run.
     *
     * Requires **Chromium 91+** (`DOCUMENT_START_SCRIPT`) for native pre-execution injection.
     * On older engines (< Chromium 91), falls back to [onPageStarted] evaluation with debug
     * logging under tag `"KeiyoushiWebView"`. The fallback only reaches the main frame and
     * runs asynchronously, so page scripts (including inline ones) may already have run.
     */
    fun injectAtDocumentStart(
        script: String,
        allowedOriginRules: Set<String> = setOf("*"),
    ): ScriptHandle {
        val handle = WebViewGlueBridge.addDocumentStartJavaScript(webView, script, allowedOriginRules)
        if (handle != null) {
            return handle
        }
        Log.d("KeiyoushiWebView", "DOCUMENT_START_SCRIPT not supported; falling back to onPageStarted injection")
        var cancelled = false
        onPageStarted {
            if (!cancelled) {
                evaluateJs(script)
            }
        }
        return ScriptHandle { cancelled = true }
    }

    /**
     * Injects [script] on lifecycle [event] ([InjectionEvent.DOCUMENT_START] or [InjectionEvent.DOCUMENT_END]),
     * optionally targeting an isolated [worldName]. Must be called before [loadUrl]/[loadData];
     * it only applies to later navigations. Invalid [allowedOriginRules] entries (e.g. missing
     * scheme) are rejected with [IllegalArgumentException], failing the run.
     *
     * With the default `*` rule, [script] also runs in every frame on the page, including third-party iframes such as
     * ads, and those can use a bridge in the same world. Restrict [allowedOriginRules] to the expected origin.
     *
     * Requires **Chromium 146+** (`JS_INJECTION_IN_FRAME_AND_WORLD`).
     * This method has **no fallback** on older WebView versions and immediately fails ([reject]s)
     * if the feature is unsupported.
     */
    fun addJavaScriptOnEvent(
        script: String,
        event: InjectionEvent = InjectionEvent.DOCUMENT_START,
        worldName: String = "",
        allowedOriginRules: Set<String> = setOf("*"),
    ): ScriptHandle {
        val handle = WebViewGlueBridge.addJavaScriptOnEvent(webView, script, event.value, worldName, allowedOriginRules)
        if (handle != null) {
            return handle
        }
        reject(UnsupportedOperationException("JS_INJECTION_IN_FRAME_AND_WORLD is not supported on this WebView (requires Chromium 146+)"))
        return ScriptHandle { }
    }

    /** Loads [url]. Can only be called once per scope. */
    fun loadUrl(url: String, headers: Map<String, String> = emptyMap()) {
        markLoaded()
        webView.loadUrl(url, headers)
    }

    /** Loads [html] as the page content, with [baseUrl] as its origin. Can only be called once per scope. */
    fun loadData(baseUrl: String, html: String, mimeType: String = "text/html") {
        markLoaded()
        webView.loadDataWithBaseURL(baseUrl, html, mimeType, "UTF-8", null)
    }

    /**
     * Runs [block] with the run's WebView on the main thread, for what the scope has no API for: right away when
     * called on the main thread, otherwise posted. Skipped once the run has ended; anything [block] throws fails the
     * run via [reject]. Don't keep the view past [block], load pages or replace its clients through it. The view is
     * never attached to a window, so `View.post` runnables don't run; schedule with a main-thread `Handler`.
     */
    @NativeWebViewApi
    fun withNativeWebView(block: (WebView) -> Unit) {
        runOnMain {
            if (destroyed || deferred.isCompleted) return@runOnMain
            try {
                block(webView)
            } catch (t: Throwable) {
                reject(t)
            }
        }
    }

    /** Runs [script], optionally passing its JSON-encoded result to [callback]. */
    fun evaluateJs(script: String, callback: ((String) -> Unit)? = null) {
        runOnMain {
            if (destroyed) return@runOnMain
            if (callback == null) {
                webView.evaluateJavascript(script, null)
            } else {
                webView.evaluateJavascript(script) { value ->
                    try {
                        callback(value)
                    } catch (t: Throwable) {
                        reject(t)
                    }
                }
            }
        }
    }

    fun stopLoading() {
        runOnMain {
            if (!destroyed) {
                webView.stopLoading()
            }
        }
    }

    /**
     * Repeats [block] on the main thread every [interval] until [resolve]/[reject] is called or
     * the scope is destroyed. The first run happens after one [interval], not immediately.
     */
    fun poll(interval: Duration = 500.milliseconds, block: () -> Unit) {
        val runnable = object : Runnable {
            override fun run() {
                if (destroyed || deferred.isCompleted) return
                try {
                    block()
                } catch (t: Throwable) {
                    reject(t)
                    return
                }
                if (!destroyed && !deferred.isCompleted) {
                    mainHandler.postDelayed(this, interval.inWholeMilliseconds)
                }
            }
        }
        runOnMain { mainHandler.postDelayed(runnable, interval.inWholeMilliseconds) }
    }

    private fun markLoaded() {
        check(loaded.compareAndSet(false, true)) { "load already called on this WebViewScope" }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun <V> setting(
        get: WebSettings.() -> V,
        set: WebSettings.(V) -> Unit,
    ): ReadWriteProperty<Any?, V> = object : ReadWriteProperty<Any?, V> {
        override fun getValue(thisRef: Any?, property: KProperty<*>): V = webView.settings.get()

        override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) {
            webView.settings.set(value)
        }
    }
}

private class ScopeWebViewClient(
    private val scope: WebViewScope<*>,
) : WebViewClient() {

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        if (scope.destroyed || url in scope.refreshPages) return
        scope.pageStartedHooks.forEach { it(url.orEmpty()) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (scope.destroyed || scope.refreshPages.remove(url)) return
        scope.pageFinishedHooks.forEach { it(url.orEmpty()) }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        if (scope.destroyed || request == null) return null
        return scope.interceptHook?.invoke(request)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
        if (scope.destroyed) return
        if (request != null && error != null) {
            scope.receivedErrorHooks.forEach { it(request, error) }
        }
    }

    override fun onRenderProcessGone(
        view: WebView?,
        detail: RenderProcessGoneDetail?,
    ): Boolean {
        scope.destroyed = true
        scope.renderDead = true
        scope.reject(RenderProcessGoneException(detail?.didCrash() == true))
        return true
    }
}

private class LoggingWebChromeClient : WebChromeClient() {
    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
        val priority = when (consoleMessage.messageLevel()) {
            ConsoleMessage.MessageLevel.ERROR -> Log.ERROR
            ConsoleMessage.MessageLevel.WARNING -> Log.WARN
            ConsoleMessage.MessageLevel.DEBUG -> Log.DEBUG
            else -> Log.INFO
        }
        Log.println(
            priority,
            "KeiyoushiWebView",
            "${consoleMessage.message()} (${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})",
        )
        return true
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun setupWebView(webView: WebView) {
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        blockNetworkImage = false
        useWideViewPort = false
        loadWithOverviewMode = false
    }

    WebViewGlueBridge.setAudioMuted(webView, true)

    runCatching {
        val metrics = Resources.getSystem().displayMetrics
        webView.layoutParams = ViewGroup.LayoutParams(metrics.widthPixels, metrics.heightPixels)
        webView.measure(
            View.MeasureSpec.makeMeasureSpec(metrics.widthPixels, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(metrics.heightPixels, View.MeasureSpec.EXACTLY),
        )
        webView.layout(0, 0, metrics.widthPixels, metrics.heightPixels)
    }
}

/** The [Fetch] a proxied WebView request goes out as, or null for one proxyRequest leaves to the WebView. */
internal fun proxyFetch(method: String, path: String, accept: String, isForMainFrame: Boolean): Fetch? {
    val name = path.lowercase()
    // Chromium's Accept for a navigation, not a script's text/html fetch
    val frame = !isForMainFrame && accept.startsWith("text/html,application/xhtml+xml")
    if (method != "GET" || frame || accept.startsWith("text/css") || FONT_EXTENSIONS.any(name::endsWith)) return null
    return when {
        isForMainFrame -> Fetch.DOCUMENT
        SCRIPT_EXTENSIONS.any(name::endsWith) -> Fetch.SCRIPT
        accept.startsWith("image/") -> Fetch.IMAGE
        else -> Fetch.XHR
    }
}

private val SCRIPT_EXTENSIONS = listOf(".js", ".mjs")

private val FONT_EXTENSIONS = listOf(".woff2", ".woff", ".ttf", ".otf")

/**
 * Runs a WebView with [configure], suspending until it calls `resolve`/`reject` or [timeout]
 * elapses. The WebView is torn down whether this returns, throws, or is canceled.
 */
suspend fun <T> runWebView(
    timeout: Duration = 30.seconds,
    configure: WebViewScope<T>.() -> Unit,
): T = withContext(Dispatchers.Main) {
    val deferred = CompletableDeferred<T>()
    val webView = WebView(applicationContext)
    setupWebView(webView)
    val scope = WebViewScope(webView, deferred)
    webView.webViewClient = ScopeWebViewClient(scope)
    webView.webChromeClient = LoggingWebChromeClient()
    try {
        try {
            scope.configure()
        } catch (t: Throwable) {
            deferred.completeExceptionally(t)
        }
        try {
            withTimeout(timeout) {
                deferred.await()
            }
        } catch (_: TimeoutCancellationException) {
            throw WebViewTimeoutException(timeout)
        }
    } finally {
        scope.destroyed = true
        runCatching { (webView.parent as? ViewGroup)?.removeView(webView) }
        webView.stopLoading()
        webView.destroy()
    }
}

/**
 * Blocking wrapper around [runWebView] for non-suspend call sites like OkHttp interceptors.
 * OkHttp exposes no per-call cancellation callback, so a side coroutine polls
 * [Call.isCanceled] and cancels the run when it flips. Cancellation surfaces as an
 * [IOException] (matching how OkHttp itself reports a canceled call to interceptors)
 * rather than leaking a raw [CancellationException].
 */
fun <T> runWebViewBlocking(
    call: Call,
    timeout: Duration = 30.seconds,
    configure: WebViewScope<T>.() -> Unit,
): T {
    check(Looper.myLooper() != Looper.getMainLooper()) {
        "runWebViewBlocking must not be called on the main thread"
    }
    return try {
        runBlocking {
            val watcher = launch {
                while (isActive) {
                    if (call.isCanceled()) {
                        this@runBlocking.cancel("OkHttp call was canceled")
                        break
                    }
                    delay(250.milliseconds)
                }
            }
            try {
                runWebView(timeout, configure)
            } finally {
                watcher.cancel()
            }
        }
    } catch (e: CancellationException) {
        throw IOException("Canceled", e)
    }
}

/**
 * Reads [key] from `localStorage` after loading [url], or null if unset. Loads an empty
 * page with [url] as its origin, so [url] itself is never fetched over the network.
 */
suspend fun getLocalStorage(url: String, key: String): String? = runWebView(timeout = 10.seconds) {
    onPageFinished {
        evaluateJs("localStorage.getItem(${key.toJsonString()})") { value ->
            resolve(value.parseAs<String?>())
        }
    }
    loadData(url, """<link rel="icon" href="data:,">""")
}

/** Marks [WebViewScope.withNativeWebView]. Prefer the scope's own API where it has one. */
@RequiresOptIn(message = "Avoid depending on native WebView")
@Retention(AnnotationRetention.BINARY)
@Target(AnnotationTarget.FUNCTION)
annotation class NativeWebViewApi
