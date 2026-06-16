package eu.kanade.tachiyomi.extension.pt.lycantoons

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import uy.kohesive.injekt.injectLazy
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
            val buffer = Buffer()
            req.body!!.writeTo(buffer)
            buffer.readUtf8()
        } else {
            null
        }

        val contentType = req.body?.contentType()?.toString()

        val resultData = fetchViaJs(url, req.method, requestBody, contentType, isImage)
            ?: error("Failed webview fetch for: $url")

        return if (isImage) {
            Base64.decode(resultData, Base64.DEFAULT).toResponse(req, "image/jpeg")
        } else {
            resultData.toResponse(req, "text/html")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchViaJs(
        url: String,
        method: String,
        requestBody: String?,
        contentType: String?,
        isImage: Boolean,
    ): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        var localWebView: WebView? = null

        mainHandler.post {
            try {
                val webView = WebView(context).also { localWebView = it }
                webView.settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    if (!userAgent.isNullOrBlank()) userAgentString = userAgent
                }

                webView.addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun passResult(data: String) {
                            result = data
                            latch.countDown()
                        }

                        @JavascriptInterface
                        fun passError(error: String) {
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
                                return res.blob();
                            })
                            .then(blob => {
                                var reader = new FileReader();
                                reader.onloadend = function() {
                                    if (!reader.result) { window.Bridge.passError('Empty payload'); return; }
                                    var parts = reader.result.split(',');
                                    window.Bridge.passResult(parts.length > 1 ? parts[1] : parts[0]);
                                };
                                reader.onerror = function() { window.Bridge.passError('Reader failed'); };
                                reader.readAsDataURL(blob);
                            })
                            """
                        } else {
                            """
                            .then(res => {
                                if (!res.ok) throw new Error('HTTP ' + res.status);
                                return res.text();
                            })
                            .then(text => window.Bridge.passResult(text))
                            """
                        }

                        val jsHeaders = if (contentType != null) "{ 'Content-Type': '$contentType' }" else "{}"
                        val requestBody = if (requestBody != null) "body: `$requestBody`," else ""

                        val jsFetchScript = """
                            (function() {
                                fetch('$url', {
                                    method: '$method',
                                    credentials: 'include',
                                    headers: $jsHeaders,
                                    $requestBody
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

        return if (completed) result else null
    }

    private fun String.toResponse(request: Request, contentType: String): Response = this.toByteArray(Charsets.UTF_8).toResponse(request, contentType)

    private fun ByteArray.toResponse(request: Request, contentType: String): Response = Response.Builder()
        .request(request)
        .protocol(Protocol.HTTP_1_1)
        .code(200)
        .message("OK")
        .body(this.toResponseBody(contentType.toMediaTypeOrNull()))
        .build()
}
