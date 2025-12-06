package eu.kanade.tachiyomi.lib.webviewfetchinterceptor

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.Headers.Companion.toHeaders
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * An OkHttp interceptor that executes HTTP requests through a WebView using JavaScript `fetch` API.
 *
 * This interceptor is useful for bypassing certain protections (like Cloudflare Turnstile) or when you need
 * to execute requests in a browser context. It works by:
 * 1. Optionally loading a URL in the WebView to establish a specific domain context
 * 2. Executing a JavaScript `fetch` with the original request details via `evaluateJavascript`
 * 3. Encoding the response in base64 and sending it back via a JavaScript interface
 * 4. Building an OkHttp Response from the WebView response
 *
 * **Best Practices:**
 * - In most cases, you don't need to specify `loadUrl` - the interceptor will work without it
 * - Only use `loadUrl` in special cases when you need a specific URL/domain context for the fetch
 * - Always use the `filter` parameter to only intercept requests from the same domain to avoid CORS issues
 * - The same-domain context allows JavaScript execution and avoids Cross-Origin Resource Sharing problems
 *
 * @param filter Optional function that determines which requests should be intercepted.
 *   Returns `true` to intercept the request via WebView, `false` to proceed normally.
 *   If `null`, all requests are intercepted.
 *   **Recommended**: Filter by domain to only intercept requests from the same domain:
 *   ```kotlin
 *   filter = { request -> request.url.toString().startsWith(baseUrl) }
 *   ```
 *
 * @param timeout Timeout in seconds for waiting for the WebView response. Default is 60 seconds.
 *
 * @param loadUrl Optional URL to load in the WebView to establish a specific domain context before executing the fetch.
 *   **Only use in special cases** when you need a specific URL/domain context. In most cases, this can be `null`.
 *   If provided, use a lightweight file from the same `baseUrl` domain:
 *   - `/robots.txt` (recommended - very lightweight)
 *   - `/favicon.ico` (small image file)
 *   - A small CSS file
 *   This ensures fast loading, same domain context (avoiding CORS), and proper JavaScript execution.
 *
 * @sample
 * ```kotlin
 * // Basic usage without loadUrl
 * override val client = network.client.newBuilder()
 *     .addInterceptor(
 *         WebViewFetchInterceptor(
 *             filter = { request -> request.url.toString().startsWith(baseUrl) }
 *         )
 *     )
 *     .build()
 *
 * // Special case: with loadUrl for specific domain context
 * override val client = network.client.newBuilder()
 *     .addInterceptor(
 *         WebViewFetchInterceptor(
 *             filter = { request -> request.url.toString().startsWith(baseUrl) },
 *             loadUrl = "$baseUrl/robots.txt"
 *         )
 *     )
 *     .build()
 * ```
 */
class WebViewFetchInterceptor(
    private val filter: ((Request) -> Boolean)? = null,
    private val timeout: Long = 60,
    private val loadUrl: String? = null,
) : Interceptor {

    private val handler = Handler(Looper.getMainLooper())
    private val context: Application by lazy { Injekt.get() }

    companion object {
        private const val DELAY_MILLIS: Long = 1000
    }

    internal class FetchResponse(
        var statusCode: Int = 0,
        var statusMessage: String = "",
        var headers: String = "",
        var bodyBase64: String = "",
        var error: String = "",
    )

    internal class JsInterface(
        private val latch: CountDownLatch,
        var response: FetchResponse = FetchResponse(),
    ) {
        @JavascriptInterface
        fun onResponse(
            statusCode: Int,
            statusMessage: String,
            headers: String,
            bodyBase64: String,
        ) {
            response.statusCode = statusCode
            response.statusMessage = statusMessage
            response.headers = headers
            response.bodyBase64 = bodyBase64
            Log.d(
                "WebViewFetchInterceptor",
                "WebView fetch response: status=$statusCode, message=$statusMessage, bodySize=${bodyBase64.length} bytes (base64), headersLength=${headers.length}",
            )
            latch.countDown()
        }

        @JavascriptInterface
        fun onError(error: String) {
            response.error = error
            Log.e("WebViewFetchInterceptor", "WebView fetch error: $error")
            latch.countDown()
        }
    }

    /**
     * Intercepts the HTTP request and either processes it through WebView or proceeds normally.
     *
     * If the filter function returns `false` or the request doesn't match the filter criteria,
     * the request proceeds normally through the OkHttp chain. Otherwise, it's executed via WebView.
     *
     * @param chain The OkHttp interceptor chain
     * @return The HTTP response, either from WebView or from the normal chain
     * @throws IOException If the WebView request times out or encounters an error
     */
    @Synchronized
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        // Use filter function if provided
        val shouldIntercept = filter?.invoke(request) ?: true

        if (!shouldIntercept) {
            return chain.proceed(request)
        }

        Log.d("WebViewFetchInterceptor", "Intercepting request: ${request.url}")

        return proceedWithWebView(request)
    }

    /**
     * Executes the HTTP request through a WebView using JavaScript fetch API.
     *
     * This method:
     * 1. Prepares the request data (URL, method, headers, body)
     * 2. Creates a WebView and establishes context:
     *    - If `loadUrl` is provided, loads that URL
     *    - Otherwise, uses the request's domain as base URL with empty HTML content
     * 3. Executes a JavaScript `fetch` with the original request details
     * 4. Waits for the response (with configurable timeout)
     * 5. Decodes the base64-encoded response and builds an OkHttp Response
     *
     * @param request The original OkHttp request to execute
     * @return An OkHttp Response built from the WebView fetch response
     * @throws IOException If the request times out or the WebView returns an error
     */
    @SuppressLint("SetJavaScriptEnabled", "AddJavascriptInterface")
    private fun proceedWithWebView(request: Request): Response {
        val latch = CountDownLatch(1)
        var webView: WebView? = null
        val jsInterface = JsInterface(latch)

        // Prepare request data
        val requestUrl = request.url.toString()
        val requestMethod = request.method
        val requestHeaders = request.headers.toMultimap().mapValues {
            it.value.lastOrNull() ?: ""
        }.toMutableMap()

        // Get User-Agent from headers
        val userAgent = request.header("User-Agent") ?: ""

        // Get contentType from body if available, otherwise from headers
        val contentType = request.body?.contentType()?.toString() ?: ""

        // If body has contentType, use it in headers instead of the one from headers
        if (contentType.isNotEmpty()) {
            requestHeaders["Content-Type"] = contentType
        }

        // Convert body to string (always use string format)
        val bodyString = request.body?.let { body ->
            val buffer = Buffer()
            body.writeTo(buffer)
            buffer.readUtf8()
        } ?: ""

        Log.d(
            "WebViewFetchInterceptor",
            "Starting WebView fetch: method=$requestMethod, url=$requestUrl, contentType=$contentType, hasBody=${bodyString.isNotEmpty()}, bodySize=${bodyString.length} chars",
        )

        // JavaScript script that performs the fetch
        val jsScript = """
            (function() {
                const requestUrl = ${requestUrl.toJsonString()};
                const requestMethod = ${requestMethod.toJsonString()};
                const requestHeaders = ${requestHeaders.toJsonString()};
                const bodyString = ${bodyString.toJsonString()};
                const userAgent = ${userAgent.toJsonString()};

                // Prepare body (always use string format)
                let body = null;
                if (bodyString && bodyString.length > 0) {
                    body = bodyString;
                }

                // Prepare headers
                const headers = new Headers();
                for (const [key, value] of Object.entries(requestHeaders)) {
                    headers.append(key, value);
                }

                // Ensure User-Agent is set
                if (userAgent && userAgent.length > 0) {
                    headers.set('User-Agent', userAgent);
                }

                // Perform fetch
                fetch(requestUrl, {
                    method: requestMethod,
                    headers: headers,
                    body: body,
                    credentials: 'include',
                    mode: 'cors',
                    cache: 'no-store',
                })
                .then(async (response) => {
                    // Read body as ArrayBuffer
                    const arrayBuffer = await response.arrayBuffer();

                    // Convert ArrayBuffer to base64
                    const bytes = new Uint8Array(arrayBuffer);
                    let binary = '';
                    for (let i = 0; i < bytes.length; i++) {
                        binary += String.fromCharCode(bytes[i]);
                    }
                    const bodyBase64 = btoa(binary);

                    // Convert headers to JSON string
                    const headersObj = {};
                    response.headers.forEach((value, key) => {
                        headersObj[key] = value;
                    });
                    const headersJson = JSON.stringify(headersObj);

                    // Call Android interface
                    window.android.onResponse(
                        response.status,
                        response.statusText,
                        headersJson,
                        bodyBase64
                    );
                })
                .catch((error) => {
                    window.android.onError(error.toString());
                });
                return true;
            })();
        """.trimIndent()

        handler.post {
            val webview = WebView(context)
            webView = webview

            with(webview.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                useWideViewPort = false
                loadWithOverviewMode = false
                userAgentString = request.header("User-Agent")
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
            }

            webview.addJavascriptInterface(jsInterface, "android")

            webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Execute script after page loads
                    view.evaluateJavascript(jsScript) { result ->
                        // Handle JavaScript errors here
                        if (result == null) {
                            Log.e("WebViewFetchInterceptor", "JavaScript evaluation returned null")
                            jsInterface.onError("Error executing JavaScript script")
                        }
                    }
                }
            }

            // Establish context by using the same domain as the request
            val baseUrl = loadUrl ?: "${request.url.scheme}://${request.url.host}/"
            webview.loadDataWithBaseURL(baseUrl, " ", "text/html", null, null)
        }

        // Wait for response
        val success = latch.await(timeout, TimeUnit.SECONDS)

        handler.postDelayed(
            { webView?.destroy() },
            DELAY_MILLIS,
        )

        if (!success) {
            Log.e(
                "WebViewFetchInterceptor",
                "Timeout waiting for WebView response after ${timeout}s",
            )
            throw IOException("Timeout executing request in WebView")
        }

        val fetchResponse = jsInterface.response

        if (fetchResponse.error.isNotEmpty()) {
            Log.e("WebViewFetchInterceptor", "WebView returned error: ${fetchResponse.error}")
            throw IOException("WebView error: ${fetchResponse.error}")
        }

        // Decode body from base64
        val bodyBytes = if (fetchResponse.bodyBase64.isNotEmpty()) {
            Base64.decode(fetchResponse.bodyBase64, Base64.NO_WRAP)
        } else {
            ByteArray(0)
        }

        // Convert JSON headers to OkHttp Headers
        val responseHeaders = try {
            val headersMap = fetchResponse.headers.parseAs<Map<String, String>>()
            headersMap.toHeaders()
        } catch (e: Exception) {
            Log.w("WebViewFetchInterceptor", "Failed to parse response headers: ${e.message}")
            okhttp3.Headers.headersOf()
        }

        // Determine content type
        val contentTypeHeader = responseHeaders["Content-Type"]
        val mediaType = contentTypeHeader?.toMediaType() ?: "application/octet-stream".toMediaType()

        Log.d(
            "WebViewFetchInterceptor",
            "Building response: statusCode=${fetchResponse.statusCode}, statusMessage=${fetchResponse.statusMessage}, bodySize=${bodyBytes.size} bytes, contentType=$contentTypeHeader",
        )

        // Build Response
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(fetchResponse.statusCode)
            .message(fetchResponse.statusMessage)
            .headers(responseHeaders)
            .body(bodyBytes.toResponseBody(mediaType))
            .build()
    }
}

