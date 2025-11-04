package eu.kanade.tachiyomi.extension.all.mangafire

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okio.Buffer
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

class WebViewHelper(
    private val client: OkHttpClient,
    private val headers: Headers,
) {
    private val name = "MangaFire"
    private val mutex = Mutex()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun loadInWebView(
        url: String,
        requestIntercept: (request: WebResourceRequest) -> RequestIntercept,
        onPageFinish: (view: WebView) -> Unit = {},
    ): String = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            withTimeout(20.seconds) {
                suspendCancellableCoroutine { continuation ->
                    val emptyWebViewResponse = runCatching {
                        WebResourceResponse("text/html", "utf-8", Buffer().inputStream())
                    }.getOrElse {
                        continuation.resumeWithException(it)
                        return@suspendCancellableCoroutine
                    }

                    val context = Injekt.get<Application>()
                    var webview: WebView? = WebView(context)

                    fun cleanup() = runBlocking(Dispatchers.Main.immediate) {
                        webview?.stopLoading()
                        webview?.destroy()
                        webview = null
                    }

                    webview?.apply {
                        with(settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            blockNetworkImage = true
                        }

                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView,
                                request: WebResourceRequest,
                            ): WebResourceResponse? {
                                // allow main page
                                if (request.url.toString() == url) {
                                    Log.d(name, "allowed: ${request.url}")

                                    runCatching { fetchWebResource(request) }
                                        .onSuccess { return it }
                                        .onFailure {
                                            if (continuation.isActive) {
                                                continuation.resumeWithException(it)
                                                cleanup()
                                            }
                                        }
                                }

                                // allow script from their cdn
                                if (
                                    request.url.host.orEmpty().contains("mfcdn.cc") &&
                                    request.url.pathSegments.lastOrNull().orEmpty().contains("js")
                                ) {
                                    Log.d(name, "allowed: ${request.url}")

                                    runCatching { fetchWebResource(request) }
                                        .onSuccess { return it }
                                        .onFailure {
                                            if (continuation.isActive) {
                                                continuation.resumeWithException(it)
                                                cleanup()
                                            }
                                        }
                                }

                                // allow jquery script
                                if (
                                    request.url.host.orEmpty().contains("cloudflare.com") &&
                                    request.url.encodedPath.orEmpty().contains("jquery")
                                ) {
                                    Log.d(name, "allowed: ${request.url}")

                                    runCatching { fetchWebResource(request) }
                                        .onSuccess { return it }
                                        .onFailure {
                                            if (continuation.isActive) {
                                                continuation.resumeWithException(it)
                                                cleanup()
                                            }
                                        }
                                }

                                when (requestIntercept(request)) {
                                    RequestIntercept.Allow -> {
                                        Log.d(name, "allowed: ${request.url}")
                                        runCatching { fetchWebResource(request) }
                                            .onSuccess { return it }
                                            .onFailure {
                                                if (continuation.isActive) {
                                                    continuation.resumeWithException(it)
                                                    cleanup()
                                                }
                                            }
                                    }

                                    RequestIntercept.Block -> {
                                        Log.d(name, "denied: ${request.url}")
                                        return emptyWebViewResponse
                                    }

                                    RequestIntercept.Capture -> {
                                        Log.d(name, "captured: ${request.url}")
                                        if (continuation.isActive) {
                                            continuation.resume(request.url.toString())
                                            cleanup()
                                        }
                                        return emptyWebViewResponse
                                    }
                                }

                                return emptyWebViewResponse
                            }

                            override fun onPageFinished(view: WebView, url: String) {
                                super.onPageFinished(view, url)
                                onPageFinish(view)
                            }
                        }

                        loadUrl(url)
                    }

                    continuation.invokeOnCancellation {
                        cleanup()
                    }
                }
            }
        }
    }

    enum class RequestIntercept {
        Allow,
        Block,
        Capture,
    }

    private fun fetchWebResource(request: WebResourceRequest): WebResourceResponse = runBlocking(Dispatchers.IO) {
        val okhttpRequest = Request.Builder().apply {
            url(request.url.toString())
            headers(headers)

            val skipHeaders = setOf("user-agent", "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform", "x-requested-with")
            for ((name, value) in request.requestHeaders) {
                if (skipHeaders.contains(name.lowercase())) continue
                header(name, value)
            }
        }.build()

        client.newCall(okhttpRequest).await().use { response ->
            val mediaType = response.body.contentType()

            WebResourceResponse(
                mediaType?.let { "${it.type}/${it.subtype}" },
                mediaType?.charset()?.name(),
                Buffer().readFrom(
                    response.body.byteStream(),
                ).inputStream(),
            )
        }
    }
}
