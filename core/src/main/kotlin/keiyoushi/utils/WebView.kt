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
import java.io.IOException
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

/** The frame that sent a web message: where it came from, and how to reply to it. */
interface WebMessageReplyProxy {
    /**
     * Origin of the sending frame (e.g. `https://example.com`), or null on the pre-Chromium 82
     * fallback, which can't tell senders apart.
     */
    val sourceOrigin: String?

    /** Whether the message came from the main frame, or null on the pre-Chromium 82 fallback. */
    val isMainFrame: Boolean?

    /** Posts [message] back to the frame that originated the message. */
    fun postMessage(message: String)

    /** Executes [script] specifically inside the frame that sent the message. */
    fun evaluateJs(script: String, callback: ((String?) -> Unit)? = null)
}

/**
 * DSL for configuring and driving a single [runWebView] run.
 *
 * Threading: page/navigation hooks, [poll] and [webMessageBridge] handlers run on the main
 * thread; [interceptRequest] and [jsBridge] handlers (and [webMessageBridge] handlers on the
 * pre-Chromium 82 fallback) run on WebView background threads. An exception thrown from any
 * callback fails the run via [reject].
 */
@SuppressLint("SetJavaScriptEnabled")
class WebViewScope<T> internal constructor(
    internal val webView: WebView,
    private val deferred: CompletableDeferred<T>,
) {
    internal val pageStartedHooks = CopyOnWriteArrayList<(String) -> Unit>()
    internal val pageFinishedHooks = CopyOnWriteArrayList<(String) -> Unit>()
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
     * Includes the `Cookie` header in requests passed to [interceptRequest], and makes WebView
     * accept `Set-Cookie` headers from responses returned by it.
     *
     * Requires **Chromium 138+** (`COOKIE_INTERCEPT`). Silently ignored if unsupported; the
     * getter returns the requested value either way.
     */
    var includeCookiesOnIntercept: Boolean = false
        set(value) {
            field = value
            WebViewGlueBridge.setIncludeCookiesOnIntercept(webView.settings, value)
        }

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
     * Exposes `window.$name.post(message)` to the page, invoking [handler] with the message.
     * Must be called before [loadUrl]/[loadData]; interfaces added afterward are not visible
     * to the already-loaded page.
     */
    @Deprecated(
        message = "Use webMessageBridge instead for asynchronous, origin-scoped messaging. " +
            "Note that JavaScript calls window.<name>.postMessage() instead of .post(), " +
            "and the handler runs on the main thread instead of a background thread.",
    )
    fun jsBridge(name: String, handler: (String) -> Unit) {
        check(bridgeNames.add(name)) { "Bridge '$name' has already been registered on this WebView" }
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun post(message: String) {
                    if (destroyed) return
                    try {
                        handler(message)
                    } catch (t: Throwable) {
                        reject(t)
                    }
                }
            },
            name,
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
     * Requires **Chromium 82+** (`WEB_MESSAGE_LISTENER`) for native origin-scoped boundary messaging.
     * On older engines (< Chromium 82), transparently falls back to an `addJavascriptInterface`
     * bridge exposing `window.$name.postMessage(message)` with debug logging under tag `"KeiyoushiWebView"`.
     * Replying via [WebMessageReplyProxy] is unsupported on the fallback path and will [reject] the execution.
     * The fallback ignores [allowedOriginRules] (every frame can post) and reports a null
     * sender origin.
     *
     * [handler] runs on the main thread (on a background thread on the fallback path).
     * [WebMessageReplyProxy.evaluateJs] additionally requires **Chromium 146+**
     * (`JS_INJECTION_IN_FRAME_AND_WORLD`) and [reject]s otherwise.
     */
    fun webMessageBridge(
        name: String,
        allowedOriginRules: Set<String> = setOf("*"),
        handler: (message: String, replyProxy: WebMessageReplyProxy) -> Unit,
    ) {
        check(bridgeNames.add(name)) { "Bridge '$name' has already been registered on this WebView" }
        val added = WebViewGlueBridge.addWebMessageListener(
            webView = webView,
            jsObjectName = name,
            allowedOriginRules = allowedOriginRules,
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

        Log.d("KeiyoushiWebView", "WEB_MESSAGE_LISTENER not supported; falling back to addJavascriptInterface for '$name'")

        val fallbackReplyProxy = object : WebMessageReplyProxy {
            override val sourceOrigin: String? = null
            override val isMainFrame: Boolean? = null

            override fun postMessage(message: String) {
                reject(UnsupportedOperationException("WebMessageReplyProxy.postMessage is not supported on this WebView (requires Chromium 82+)"))
            }

            override fun evaluateJs(script: String, callback: ((String?) -> Unit)?) {
                reject(UnsupportedOperationException("WebMessageReplyProxy.evaluateJs is not supported on this WebView (requires Chromium 82+)"))
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
        handler: (message: String) -> Unit,
    ) = webMessageBridge(name, allowedOriginRules) { message, _ -> handler(message) }

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
        if (scope.destroyed) return
        scope.pageStartedHooks.forEach { it(url.orEmpty()) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        if (scope.destroyed) return
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
