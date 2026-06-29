package eu.kanade.tachiyomi.extension.pt.lycantoons

import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.applicationContext
import keiyoushi.utils.toJsonString
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

const val REUSE_TIMEOUT_MS = 30 * 1000L // 30s

// proxy Request through WebView since OkHttp gets 403 and fails Cloudflare TLS signature checks
class WebViewInterceptor(val baseUrl: String, private val userAgent: String?) : Interceptor {

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private var destroyWv: Runnable? = null

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

        val resultData = fetchViaJs(url, req.method, req.headers, requestBody, isImage)
        if (!resultData.success) throw IOException("[WebView]: " + resultData.result)

        val resultConentType = resultData.contentType ?: "text/html"
        return if (isImage) {
            Base64.decode(resultData.result, Base64.DEFAULT).toResponse(req, resultConentType)
        } else {
            resultData.result.toResponse(req, resultConentType)
        }
    }

    private var cachedWv: WebView? = null
    private var accessTime = 0L

    private val globalWebView: WebView
        get() {
            destroyWv?.let { mainHandler.removeCallbacks(it) }

            if (cachedWv == null) {
                cachedWv = WebView(applicationContext).apply {
                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        userAgentString = userAgent
                    }
                }
            }

            destroyWv = Runnable {
                cachedWv?.destroy()
                cachedWv = null
                destroyWv = null
            }.also {
                mainHandler.postDelayed(it, REUSE_TIMEOUT_MS)
            }

            return cachedWv!!
        }

    @Synchronized
    private fun fetchViaJs(
        url: String,
        method: String,
        headers: Headers,
        requestBody: String?,
        isImage: Boolean,
    ): FetchResult {
        val bridgeName = "Lycan_${System.currentTimeMillis()}"
        val latch = CountDownLatch(1)
        var result: FetchResult? = null
        var errorMessage: Throwable? = null

        mainHandler.post {
            try {
                val webView = globalWebView

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
                    bridgeName,
                )

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, pageUrl: String?) {
                        val jsScript = if (isImage) {
                            """
                            (function() {
                                const img = document.getElementById('_image');
                                const toBase64 = (data, type) => {
                                    if (data instanceof Blob) {
                                        const reader = new FileReader();
                                        reader.onload = () => window.$bridgeName.passResult(btoa(reader.result), type);
                                        reader.onerror = () => window.$bridgeName.passError('Reader error');
                                        reader.readAsBinaryString(data);
                                    } else {
                                        window.$bridgeName.passResult(data.toDataURL('image/jpeg', 0.8), 'image/jpeg');
                                    }
                                };
                                fetch(img.src, { cache: 'force-cache' })    // refech url to get compressed version
                                    .then(r => r.blob())
                                    .then(b => toBase64(b, b.type))
                                    .catch(() => {                         // Fallback to canvas just in case if webivew acts up
                                        const canvas = document.createElement('canvas');
                                        canvas.width = img.naturalWidth;
                                        canvas.height = img.naturalHeight;
                                        canvas.getContext('2d').drawImage(img, 0, 0);
                                        toBase64(canvas);
                                    });
                            })();
                            """.trimIndent()
                        } else {
                            val jsHeaders = buildMap {
                                headers.names().forEach { name ->
                                    put(name, headers[name])
                                }
                            }.toJsonString()

                            val requestBody = if (requestBody != null) "body: `$requestBody`," else ""
                            """
                            (function() {
                                let contentType;

                                fetch('$url', {
                                    method: '$method',
                                    credentials: 'include',
                                    headers: $jsHeaders,
                                    $requestBody
                                })

                                .then(res => {
                                if (!res.ok) throw new Error('HTTP ' + res.status);
                                contentType = res.headers.get('content-type')
                                return res.text();
                            })
                            .then(text => window.$bridgeName.passResult(text, contentType))

                                .catch(err => window.$bridgeName.passError(err.message));
                            })();
                            """.trimIndent()
                        }

                        view.evaluateJavascript(jsScript, null)
                    }
                }

                val pageHtml = if (isImage) "<html><body><img id='_image' src='$url'/></body></html>" else " " // fetch-dest as image

                webView.loadDataWithBaseURL(baseUrl, pageHtml, "text/html", "utf-8", null)
            } catch (e: Throwable) {
                errorMessage = e
                latch.countDown()
            }
        }

        latch.await(if (isImage) 10 else 5, TimeUnit.SECONDS)

        return result ?: FetchResult(false, (errorMessage ?: "Timed out").toString())
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
