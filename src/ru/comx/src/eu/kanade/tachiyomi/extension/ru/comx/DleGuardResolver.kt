package eu.kanade.tachiyomi.extension.ru.comx

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import keiyoushi.utils.applicationContext
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.Call
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

object DleGuardResolver {

    private const val TIMEOUT_SECONDS = 30L
    private const val POLL_INTERVAL_MS = 250L
    private const val FAILURE_RETRY_DELAY_MS = 5_000L

    private val htmlMediaType = "text/html; charset=UTF-8".toMediaType()

    // The guard briefly renders a 404 /_c page before replacing it with the target document.
    private val pageResponseScript = """
        (() => {
            const root = document.documentElement;
            const isGuardPage = /^\/_c(?:\/|$)/.test(location.pathname);
            const errorMessage =
                document.querySelector(".message-info__content")?.textContent || "";
            const isNotFound =
                /\/404\.html\/?$/.test(location.pathname) ||
                errorMessage.includes("изменен её адрес или она была удалена");
            const hasPageContent =
                document.querySelector("#dle-content") ||
                root?.innerHTML.includes("window.__DATA__") ||
                root?.innerHTML.includes("window.__XFILTER__");
            if (
                !root ||
                document.readyState === "loading" ||
                !/^https?:$/.test(location.protocol) ||
                isGuardPage ||
                (!hasPageContent && !isNotFound)
            ) return null;

            return JSON.stringify({
                url: location.href,
                code: isNotFound ? 404 : 200,
                message: isNotFound ? "Not Found" : "OK",
                html: root.outerHTML,
            });
        })()
    """.trimIndent()

    // Avoid launching another WebView for requests queued behind a failed solve.
    private var lastFailureAtNanos = 0L

    fun interceptor(baseUrl: String): Interceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val response = chain.proceed(originalRequest)
        if (response.request.url.pathSegments.firstOrNull() != "_c") {
            return@Interceptor response
        }
        val responseBuilder = response.newBuilder()
        response.close()

        val url = if (originalRequest.method == "GET") {
            originalRequest.url.toString()
        } else {
            "$baseUrl/"
        }
        val pageResponse = solve(url, originalRequest.header("User-Agent"), chain.call())
        if (pageResponse == null) {
            throw IOException("Open in WebView to bypass site protection")
        }

        if (originalRequest.method != "GET") {
            return@Interceptor chain.proceed(originalRequest)
        }

        val finalRequest = originalRequest.newBuilder()
            .url(pageResponse.url)
            .build()

        responseBuilder
            .request(finalRequest)
            .code(pageResponse.code)
            .message(pageResponse.message)
            .removeHeader("Content-Encoding")
            .removeHeader("Content-Length")
            .removeHeader("Location")
            .header("Content-Type", "text/html; charset=UTF-8")
            .body(pageResponse.html.toResponseBody(htmlMediaType))
            .build()
    }

    @Synchronized
    @SuppressLint("SetJavaScriptEnabled")
    private fun solve(siteUrl: String, userAgent: String?, call: Call): PageResponse? {
        val now = System.nanoTime()
        val retryDelayNanos = TimeUnit.MILLISECONDS.toNanos(FAILURE_RETRY_DELAY_MS)
        if (lastFailureAtNanos != 0L && now - lastFailureAtNanos < retryDelayNanos) return null

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        var poll: Runnable? = null
        var failure: Throwable? = null
        var pageResponse: PageResponse? = null

        handler.post {
            try {
                if (call.isCanceled()) {
                    latch.countDown()
                    return@post
                }

                val wv = WebView(applicationContext)
                webView = wv
                with(wv.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    blockNetworkImage = true
                    if (!userAgent.isNullOrBlank()) userAgentString = userAgent
                }

                // runWebViewBlocking assigns a WebViewClient, which makes Tachimanga intercept
                // the challenge requests instead of letting the WebView handle them.
                val pollTask = object : Runnable {
                    override fun run() {
                        if (latch.count == 0L || call.isCanceled()) {
                            latch.countDown()
                            return
                        }
                        try {
                            wv.evaluateJavascript(pageResponseScript) { value ->
                                try {
                                    if (latch.count == 0L || call.isCanceled()) {
                                        latch.countDown()
                                        return@evaluateJavascript
                                    }
                                    val result = decodePageResponse(value)
                                    when {
                                        result == null -> handler.postDelayed(this, POLL_INTERVAL_MS)
                                        result.code == 0 -> {
                                            failure = IOException(result.message)
                                            latch.countDown()
                                        }
                                        else -> {
                                            pageResponse = result
                                            latch.countDown()
                                        }
                                    }
                                } catch (t: Throwable) {
                                    failure = t
                                    latch.countDown()
                                }
                            }
                        } catch (t: Throwable) {
                            failure = t
                            latch.countDown()
                        }
                    }
                }
                poll = pollTask

                wv.loadUrl(siteUrl)
                handler.postDelayed(pollTask, POLL_INTERVAL_MS)
            } catch (t: Throwable) {
                failure = t
                latch.countDown()
            }
        }

        try {
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS)
            while (latch.count != 0L && !call.isCanceled()) {
                val remainingMs = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime())
                if (remainingMs <= 0L) break
                latch.await(minOf(POLL_INTERVAL_MS, remainingMs), TimeUnit.MILLISECONDS)
            }
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while solving WebView challenge", e)
        } finally {
            latch.countDown()
            handler.post {
                poll?.let(handler::removeCallbacks)
                webView?.stopLoading()
                webView?.destroy()
            }
        }

        if (call.isCanceled()) throw IOException("Canceled")
        failure?.let {
            lastFailureAtNanos = System.nanoTime()
            throw IOException("WebView challenge failed", it)
        }

        lastFailureAtNanos = if (pageResponse == null) System.nanoTime() else 0L
        return pageResponse
    }

    private fun decodePageResponse(value: String?): PageResponse? {
        if (value == null || value == "null") return null
        val json = if (value.startsWith('"')) value.parseAs<String>() else value
        return json.parseAs()
    }

    @Serializable
    private class PageResponse(
        val url: String,
        val code: Int,
        val message: String,
        val html: String,
    )
}
