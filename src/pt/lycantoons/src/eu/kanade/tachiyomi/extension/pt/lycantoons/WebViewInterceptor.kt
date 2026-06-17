package eu.kanade.tachiyomi.extension.pt.lycantoons

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.toJsonString
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

// proxy Request through WebView since OkHttp gets 403 and fails Cloudflare TLS signature checks
class WebViewInterceptor(private val userAgent: String?) : Interceptor {

    private val context: Application by injectLazy()
    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }

    override fun intercept(chain: Interceptor.Chain): Response {
        val req = chain.request()
        val url = req.url.toString()
        val isImage = url.contains("/cdn")

        val requestBody = if (req.method == "POST") {
            req.body?.let {
                val buffer = Buffer()
                it.writeTo(buffer)
                buffer.readUtf8()
            }
        } else {
            null
        }

        val resultData = fetchViaJs(url, req.method, req.headers, requestBody, isImage)
        if (!resultData.success) throw IOException("[JS]: " + resultData.result)

        val resultContentType = resultData.contentType ?: "text/html"
        return if (isImage) {
            Base64.decode(resultData.result, Base64.DEFAULT).toResponse(req, resultContentType)
        } else {
            resultData.result.toResponse(req, resultContentType)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchViaJs(
        url: String,
        method: String,
        headers: Headers,
        requestBody: String?,
        isImage: Boolean,
    ): FetchResult {
        val latch = CountDownLatch(1)
        lateinit var result: FetchResult
        var localWebView: WebView? = null

        mainHandler.post {
            try {
                val webView = WebView(context).also { localWebView = it }
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    userAgentString = userAgent
                }

                webView.addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun passResult(data: String, contentType: String?) {
                            result = FetchResult(true, data, contentType)
                            latch.countDown()
                        }

                        @JavascriptInterface
                        fun passError(error: String) {
                            result = FetchResult(false, error)
                            latch.countDown()
                        }
                    },
                    "Bridge",
                )

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, pageUrl: String?) {
                        val handlingScript = if (isImage) {
                            """
                            .then(res => {
                                if (!res.ok) throw new Error('HTTP ' + res.status);
                                contentType = res.headers.get('content-type')
                                return res.blob();
                            })
                            .then(blob => {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    if (!reader.result) { window.Bridge.passError('Empty payload'); return; }
                                    var parts = reader.result.split(',');
                                    window.Bridge.passResult(parts.length > 1 ? parts[1] : parts[0], contentType);
                                };
                                reader.onerror = function() { window.Bridge.passError('Reader failed'); };
                                reader.readAsDataURL(blob);
                            })
                            """
                        } else {
                            """
                            .then(res => {
                                if (!res.ok) throw new Error('HTTP ' + res.status);
                                contentType = res.headers.get('content-type')
                                return res.text();
                            })
                            .then(text => window.Bridge.passResult(text, contentType))
                            """
                        }

                        val jsHeaders = buildMap {
                            headers.names().forEach { name ->
                                put(name, headers[name])
                            }
                        }.toJsonString()

                        val jsRequestBody = if (requestBody != null) "body: ${requestBody.toJsonString()}," else ""

                        val jsFetchScript = """
                            (function() {
                                let contentType;

                                fetch(${url.toJsonString()}, {
                                    method: ${method.toJsonString()},
                                    credentials: 'include',
                                    headers: $jsHeaders,
                                    $jsRequestBody
                                })
                                $handlingScript
                                .catch(err => window.Bridge.passError(err.message));
                            })();
                        """.trimIndent()

                        view.evaluateJavascript(jsFetchScript, null)
                    }
                }

                webView.loadDataWithBaseURL(url, " ", "text/html", "utf-8", null)
            } catch (_: Throwable) {
                latch.countDown()
            }
        }

        val completed = latch.await(8, TimeUnit.SECONDS)

        mainHandler.post {
            try {
                localWebView?.apply {
                    stopLoading()
                    destroy()
                }
            } catch (_: Throwable) {}
        }

        return if (completed) result else FetchResult(false, "Timed out")
    }

    private fun String.toResponse(request: Request, contentType: String): Response = this.toByteArray(Charsets.UTF_8).toResponse(request, contentType)

    private fun ByteArray.toResponse(request: Request, contentType: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .header("Content-Type", contentType)
        .body(this.toResponseBody(contentType.toMediaTypeOrNull()))
        .build()
}
