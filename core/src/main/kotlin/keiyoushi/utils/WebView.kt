package keiyoushi.utils

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Bitmap
import android.os.Build
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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.Call
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CopyOnWriteArrayList
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

/**
 * DSL for configuring and driving a single [runWebView] run.
 *
 * Threading: page/navigation hooks and [poll] run on the main thread; [interceptRequest]
 * and [jsBridge] handlers run on WebView background threads. An exception thrown from any
 * callback fails the run via [fail].
 */
@SuppressLint("SetJavaScriptEnabled")
class WebViewScope<T> internal constructor(
    private val webView: WebView,
    private val deferred: CompletableDeferred<T>,
) {
    internal val pageStartedHooks = CopyOnWriteArrayList<(String) -> Unit>()
    internal val pageFinishedHooks = CopyOnWriteArrayList<(String) -> Unit>()
    internal val receivedErrorHooks = CopyOnWriteArrayList<(WebResourceRequest, WebResourceError) -> Unit>()
    internal val bridgeNames = CopyOnWriteArrayList<String>()

    @Volatile
    internal var interceptHook: ((WebResourceRequest) -> WebResourceResponse?)? = null

    /** Set once the run is tearing down; guards against late calls into a dead WebView. */
    @Volatile
    internal var destroyed = false

    /** Set when the render process died; tells [WebViewSession] not to reuse this WebView. */
    @Volatile
    internal var renderDead = false

    private val loaded = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Completes [runWebView] with [value]. First completion wins; later calls are no-ops. */
    fun resume(value: T) {
        deferred.complete(value)
    }

    /** Completes [runWebView] by throwing [error]. No-op if already completed. */
    fun fail(error: Throwable) {
        deferred.completeExceptionally(error)
    }

    // WebSettings passthroughs.
    var javaScriptEnabled: Boolean by setting({ javaScriptEnabled }, { javaScriptEnabled = it })
    var domStorageEnabled: Boolean by setting({ domStorageEnabled }, { domStorageEnabled = it })
    var databaseEnabled: Boolean by setting({ databaseEnabled }, { databaseEnabled = it })
    var blockImages: Boolean by setting({ blockNetworkImage }, { blockNetworkImage = it })
    var useWideViewPort: Boolean by setting({ useWideViewPort }, { useWideViewPort = it })
    var loadWithOverviewMode: Boolean by setting({ loadWithOverviewMode }, { loadWithOverviewMode = it })
    var userAgent: String by setting({ userAgentString }, { userAgentString = it })

    /** Runs [block] on every navigation start. */
    fun onPageStarted(block: (url: String) -> Unit) {
        pageStartedHooks += { url ->
            try {
                block(url)
            } catch (t: Throwable) {
                fail(t)
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
                fail(t)
            }
        }
    }

    /** Runs [block] on navigation failures (DNS/SSL/network errors). */
    fun onReceivedError(block: (request: WebResourceRequest, error: WebResourceError) -> Unit) {
        receivedErrorHooks += { request, error ->
            try {
                block(request, error)
            } catch (t: Throwable) {
                fail(t)
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
                fail(t)
                null
            }
        }
    }

    /**
     * Exposes `window.$name.post(message)` to the page, invoking [handler] with the message.
     * Must be called before [loadUrl]/[loadData]; interfaces added afterwards are not visible
     * to the already-loaded page.
     */
    fun jsBridge(name: String, handler: (String) -> Unit) {
        bridgeNames += name
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun post(message: String) {
                    if (destroyed) return
                    try {
                        handler(message)
                    } catch (t: Throwable) {
                        fail(t)
                    }
                }
            },
            name,
        )
    }

    /** Loads [url]. Can only be called once per scope. */
    fun loadUrl(url: String, headers: Map<String, String> = emptyMap()) {
        markLoaded()
        webView.loadUrl(url, headers)
    }

    /** Loads [html] as the page content, with [baseUrl] as its origin. Can only be called once per scope. */
    fun loadData(html: String, baseUrl: String? = null, mimeType: String = "text/html") {
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
                        fail(t)
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
     * Repeats [block] on the main thread every [interval] until [resume]/[fail] is called or
     * the scope is destroyed. The first run happens after one [interval], not immediately.
     */
    fun poll(interval: Duration = 500.milliseconds, block: () -> Unit) {
        val runnable = object : Runnable {
            override fun run() {
                if (destroyed || deferred.isCompleted) return
                try {
                    block()
                } catch (t: Throwable) {
                    fail(t)
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

    /** Property delegate backed by a [WebSettings] field. */
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

/** Forwards WebViewClient callbacks to the owning [WebViewScope]'s hooks. */
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
        if (scope.destroyed) return null
        val hook = scope.interceptHook ?: return null
        return request?.let(hook)
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
        // didCrash() requires API 26; below that, report as "killed".
        val didCrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail?.didCrash() == true
        scope.fail(RenderProcessGoneException(didCrash))
        // Claim the crash as handled so the system doesn't kill the app process.
        return true
    }
}

/** Forwards WebView console output to logcat under tag "KeiyoushiWebView". */
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

/**
 * Reuses one WebView across multiple [runWebView] calls, destroying it after [idleTimeout]
 * of inactivity. Concurrent [runWebView] calls sharing a session are serialized via [mutex].
 * [obtain] and [release] are only ever called on the main thread by [runWebView].
 */
class WebViewSession(private val idleTimeout: Duration = 30.seconds) {
    internal val mutex = Mutex()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var webView: WebView? = null
    private var destroyTask: Runnable? = null

    internal fun obtain(): WebView {
        destroyTask?.let(mainHandler::removeCallbacks)
        destroyTask = null
        return webView ?: WebView(applicationContext).also { webView = it }
    }

    /** Parks the WebView for reuse, or destroys it immediately if [dead]. */
    internal fun release(dead: Boolean) {
        val current = webView ?: return
        if (dead) {
            current.destroy()
            webView = null
            return
        }
        // Detach clients and park on about:blank so stray callbacks can't fire while idle.
        current.webViewClient = WebViewClient()
        current.webChromeClient = null
        current.loadUrl("about:blank")
        val task = Runnable {
            webView?.destroy()
            webView = null
            destroyTask = null
        }
        destroyTask = task
        mainHandler.postDelayed(task, idleTimeout.inWholeMilliseconds)
    }

    /** Immediately destroys the underlying WebView, if any. */
    fun destroy() {
        mainHandler.post {
            destroyTask?.let(mainHandler::removeCallbacks)
            destroyTask = null
            webView?.destroy()
            webView = null
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun setupWebView(webView: WebView) {
    // Reset every scope-exposed setting to a known default, so a pooled WebView doesn't
    // leak the previous run's configuration into this one.
    webView.settings.apply {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = false
        blockNetworkImage = false
        useWideViewPort = false
        loadWithOverviewMode = false
        userAgentString = null // resets to the system default UA
    }

    // Give the off-screen WebView real screen dimensions; some pages (JS challenges,
    // lazy-loading scripts) check viewport size and misbehave at 0x0.
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
 * Runs a WebView with [configure], suspending until it calls `resume`/`fail` or [timeout]
 * elapses. Pass [session] to reuse a WebView across calls instead of spinning up a new one.
 * The WebView is torn down (or returned to [session]) whether this returns, throws, or is
 * canceled.
 */
suspend fun <T> runWebView(
    session: WebViewSession? = null,
    timeout: Duration = 30.seconds,
    configure: WebViewScope<T>.() -> Unit,
): T {
    suspend fun execute(): T = withContext(Dispatchers.Main) {
        val deferred = CompletableDeferred<T>()
        val webView = session?.obtain() ?: WebView(applicationContext)
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
            webView.stopLoading()
            if (session != null) {
                // Strip this run's JS bridges so they don't leak into the next pooled run.
                scope.bridgeNames.forEach(webView::removeJavascriptInterface)
                session.release(dead = scope.renderDead)
            } else {
                webView.destroy()
            }
        }
    }
    return session?.mutex?.withLock { execute() } ?: execute()
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
    session: WebViewSession? = null,
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
                        // Cancel the whole runBlocking scope; cancelling only this watcher
                        // coroutine would not propagate to the runWebView call below.
                        this@runBlocking.cancel("OkHttp call was canceled")
                        break
                    }
                    delay(250.milliseconds)
                }
            }
            try {
                runWebView(session, timeout, configure)
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
suspend fun getLocalStorage(url: String, key: String): String? = runWebView(timeout = 3.seconds) {
    onPageFinished {
        // JSONObject.quote produces a valid JS string literal for arbitrary keys.
        evaluateJs("localStorage.getItem(${JSONObject.quote(key)})") { value ->
            resume(value.parseAs<String?>())
        }
    }
    loadData("", url)
}
