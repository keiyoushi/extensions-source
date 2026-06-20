package eu.kanade.tachiyomi.extension.fr.raijinscans

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Page
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/**
 * Runs an external page-list script ([ReaderScriptManager.getScript]) inside a headless WebView and
 * collects the resulting image urls.
 *
 * ## JS contract
 * The script must define `async function rjGetPages(ctx, host)` returning an array of image-url
 * strings (or `{ url }` objects).
 *
 *  - `ctx`  = `{ baseUrl, chapterUrl, html, ajaxHeaders, maxPageRequests }`.
 *  - `host.fetch(spec)` -> Promise of `{ ok, status, body }`. `spec` =
 *    `{ url, method?, headers?, multipart?: [[name, value], ...], body?, contentType? }`. The request
 *    is performed by okhttp on the app's network stack; the WebView itself never hits the network.
 *  - `host.log(msg)` logs to logcat.
 *
 * The WebView is the only JS engine available to extensions (SetJavaScriptEnabled), so the bridge is
 * wired through `addJavascriptInterface`. Bridge calls land on a binder thread; network runs on IO
 * and results are posted back via `evaluateJavascript`, so the JS engine thread is never blocked.
 */
class PageListInterpreter(private val client: OkHttpClient) {

    private val mutex = Mutex()

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun getPages(
        script: String,
        baseUrl: String,
        chapterUrl: String,
        html: String,
        ajaxHeaders: Headers,
        maxPageRequests: Int,
    ): List<Page> = mutex.withLock {
        withContext(Dispatchers.Main.immediate) {
            withTimeout(REQUEST_TIMEOUT) {
                val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
                var webView: WebView? = null
                try {
                    suspendCancellableCoroutine { continuation ->
                        val context = Injekt.get<Application>()
                        val wv = WebView(context)
                        webView = wv
                        with(wv.settings) {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            blockNetworkLoads = true // every request must go through the host bridge
                        }

                        wv.addJavascriptInterface(Bridge(scope, { webView }, continuation), BRIDGE_NAME)

                        val ctx = JSONObject().apply {
                            put("baseUrl", baseUrl)
                            put("chapterUrl", chapterUrl)
                            put("html", html)
                            put("maxPageRequests", maxPageRequests)
                            put("ajaxHeaders", JSONObject().apply { ajaxHeaders.forEach { (k, v) -> put(k, v) } })
                        }
                        val injection = buildInjection(ctx.toString(), script)

                        wv.webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView, url: String) {
                                view.evaluateJavascript(injection, null)
                            }
                        }
                        wv.loadDataWithBaseURL(baseUrl, BLANK_PAGE, "text/html", "utf-8", null)
                    }
                } finally {
                    scope.cancel()
                    webView?.apply {
                        stopLoading()
                        removeJavascriptInterface(BRIDGE_NAME)
                        destroy()
                    }
                }
            }
        }
    }

    private inner class Bridge(
        private val scope: CoroutineScope,
        private val webViewProvider: () -> WebView?,
        private val continuation: CancellableContinuation<List<Page>>,
    ) {
        @JavascriptInterface
        fun fetch(id: String, specJson: String) {
            scope.launch {
                val result = withContext(Dispatchers.IO) { runCatching { doFetch(specJson) } }
                val wv = webViewProvider() ?: return@launch
                val call = result.fold(
                    onSuccess = { "window.__rjResolveFetch(${JSONObject.quote(id)}, ${JSONObject.quote(it)})" },
                    onFailure = { "window.__rjRejectFetch(${JSONObject.quote(id)}, ${JSONObject.quote(it.message ?: it.toString())})" },
                )
                wv.evaluateJavascript(call, null)
            }
        }

        @JavascriptInterface
        fun resolve(json: String) {
            if (continuation.isActive) {
                runCatching { parsePages(json) }.fold(
                    onSuccess = { continuation.resume(it) },
                    onFailure = { continuation.resumeWithException(it) },
                )
            }
        }

        @JavascriptInterface
        fun reject(message: String) {
            if (continuation.isActive) continuation.resumeWithException(Exception(message))
        }

        @JavascriptInterface
        fun log(message: String) {
            Log.d(LOG_TAG, message)
        }
    }

    private suspend fun doFetch(specJson: String): String {
        val spec = JSONObject(specJson)
        client.newCall(buildRequest(spec)).await().use { response ->
            return JSONObject().apply {
                put("ok", response.isSuccessful)
                put("status", response.code)
                put("body", response.body.string())
            }.toString()
        }
    }

    private fun buildRequest(spec: JSONObject): Request {
        val method = spec.optString("method", "GET").uppercase()
        val headers = Headers.Builder().apply {
            spec.optJSONObject("headers")?.let { h ->
                h.keys().forEach { k -> add(k, h.getString(k)) }
            }
        }.build()
        val body = when {
            spec.has("multipart") -> {
                val builder = MultipartBody.Builder().setType(MultipartBody.FORM)
                val parts = spec.getJSONArray("multipart")
                for (i in 0 until parts.length()) {
                    val pair = parts.getJSONArray(i)
                    builder.addFormDataPart(pair.getString(0), pair.getString(1))
                }
                builder.build()
            }
            spec.has("body") -> {
                val mediaType = spec.optString("contentType", "text/plain; charset=utf-8").toMediaTypeOrNull()
                spec.getString("body").toRequestBody(mediaType)
            }
            method == "POST" -> ByteArray(0).toRequestBody(null)
            else -> null
        }
        return Request.Builder().url(spec.getString("url")).headers(headers).method(method, body).build()
    }

    private fun parsePages(json: String): List<Page> {
        val array = JSONArray(json)
        return (0 until array.length()).map { i ->
            val element = array.get(i)
            val url = (element as? JSONObject)?.optString("url") ?: element.toString()
            Page(i, imageUrl = url)
        }
    }

    private fun buildInjection(ctxJson: String, script: String): String = buildString {
        append("window.__RJ_CTX__ = JSON.parse(").append(JSONObject.quote(ctxJson)).append(");\n")
        append(HOST_SHIM).append("\n")
        append(script).append("\n")
        append(RUNNER)
    }

    companion object {
        private val REQUEST_TIMEOUT = 60.seconds
        private const val BRIDGE_NAME = "__rjbridge"
        private const val LOG_TAG = "RaijinScans"
        private const val BLANK_PAGE = "<html><head></head><body></body></html>"

        // Promise-based shim over the synchronous binder bridge. `host.fetch` registers a pending
        // promise and hands the request to Kotlin; Kotlin calls back __rjResolveFetch/__rjRejectFetch.
        private val HOST_SHIM = """
            window.__rjpending = {};
            window.__rjseq = 0;
            window.__rjResolveFetch = function (id, jsonText) {
              var cb = window.__rjpending[id]; delete window.__rjpending[id];
              if (cb) { try { cb.resolve(JSON.parse(jsonText)); } catch (e) { cb.reject(e); } }
            };
            window.__rjRejectFetch = function (id, msg) {
              var cb = window.__rjpending[id]; delete window.__rjpending[id];
              if (cb) cb.reject(new Error(msg));
            };
            window.__rjhost = {
              fetch: function (spec) {
                return new Promise(function (resolve, reject) {
                  var id = String(++window.__rjseq);
                  window.__rjpending[id] = { resolve: resolve, reject: reject };
                  __rjbridge.fetch(id, JSON.stringify(spec));
                });
              },
              log: function (m) { __rjbridge.log(String(m)); }
            };
        """.trimIndent()

        private val RUNNER = """
            (function () {
              try {
                Promise.resolve(rjGetPages(window.__RJ_CTX__, window.__rjhost))
                  .then(function (p) { __rjbridge.resolve(JSON.stringify(p)); })
                  .catch(function (e) { __rjbridge.reject(String((e && e.stack) || e)); });
              } catch (e) {
                __rjbridge.reject(String((e && e.stack) || e));
              }
            })();
        """.trimIndent()
    }
}
