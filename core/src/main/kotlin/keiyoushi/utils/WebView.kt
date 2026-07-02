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
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Call
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

/** Thrown by [runWebView] on timeout. A plain [Exception], not a [kotlinx.coroutines.CancellationException]. */
class WebViewTimeoutException internal constructor(
    timeout: Duration,
) : Exception("Timed out waiting for WebView after $timeout")

/** DSL for configuring and driving a single [runWebView] run. */
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

    @Volatile
    internal var destroyed = false

    @Volatile
    internal var renderDead = false

    private val loaded = AtomicBoolean(false)
    private val mainHandler = Handler(Looper.getMainLooper())

    /** Completes [runWebView] with [value]. */
    fun resume(value: T) {
        deferred.complete(value)
    }

    /** Completes [runWebView] by throwing [error]. */
    fun fail(error: Throwable) {
        deferred.completeExceptionally(error)
    }

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

    /** Runs [block] on every navigation finish. */
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

    /** Registers [block] to inspect/replace/block every resource request. Can only be registered once. */
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

    /** Exposes `window.$name.post(message)` to the page, invoking [handler] with the message. */
    fun jsBridge(name: String, handler: (String) -> Unit) {
        bridgeNames += name
        webView.addJavascriptInterface(
            object {
                @JavascriptInterface
                fun post(message: String) {
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

    /** Runs [script], optionally passing its result to [callback]. */
    fun evaluateJs(script: String, callback: ((String) -> Unit)? = null) {
        val guarded = callback?.let { cb ->
            { value: String ->
                try {
                    cb(value)
                } catch (t: Throwable) {
                    fail(t)
                }
            }
        }
        runOnMain {
            if (!destroyed) {
                webView.evaluateJavascript(script, guarded)
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

    /** Repeats [block] every [interval] until [resume]/[fail] is called or the scope is destroyed. */
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
        scope.pageStartedHooks.forEach { it(url.orEmpty()) }
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        scope.pageFinishedHooks.forEach { it(url.orEmpty()) }
    }

    override fun shouldInterceptRequest(
        view: WebView?,
        request: WebResourceRequest?,
    ): WebResourceResponse? {
        val hook = scope.interceptHook ?: return null
        return request?.let(hook)
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?,
    ) {
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
        val didCrash = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && detail?.didCrash() == true
        scope.fail(RenderProcessGoneException(didCrash))
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
 * of inactivity. Concurrent [runWebView] calls sharing a session are serialized.
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

    internal fun release(dead: Boolean) {
        val current = webView ?: return
        if (dead) {
            current.destroy()
            webView = null
            return
        }
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
    webView.settings.javaScriptEnabled = true
    webView.settings.domStorageEnabled = true

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
            } catch (e: TimeoutCancellationException) {
                throw WebViewTimeoutException(timeout)
            }
        } finally {
            scope.destroyed = true
            webView.stopLoading()
            if (session != null) {
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
 * aborts if [call] is canceled. OkHttp has no push notification for a single call's
 * cancellation, so this polls [Call.isCanceled] on the side; on cancellation,
 * throws [IOException] (matching how OkHttp itself reports a canceled call to interceptors)
 * instead of leaking a raw [CancellationException].
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
            coroutineScope {
                val watcher = launch {
                    while (isActive) {
                        if (call.isCanceled()) {
                            cancel("OkHttp call was canceled")
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
        }
    } catch (e: CancellationException) {
        throw IOException("Canceled", e)
    }
}

/** Reads [key] from `localStorage` after loading [url], or null if unset. */
suspend fun getLocalStorage(url: String, key: String): String? = runWebView(timeout = 3.seconds) {
    onPageFinished {
        evaluateJs("""localStorage.getItem("$key")""") { value ->
            resume(value.parseAs<String?>())
        }
    }
    loadData("", url)
}
