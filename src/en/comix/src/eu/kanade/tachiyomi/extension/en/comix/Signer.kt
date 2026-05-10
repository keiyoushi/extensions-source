package eu.kanade.tachiyomi.extension.en.comix

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Signs requests and decrypts responses for Comix's protected endpoints
 * (`/manga/{hid}/chapters`, `/chapters/{cid}`, …).
 *
 * The site ships a Jscrambler-style obfuscated VM bundle (`secure-*.js`)
 * that exposes two pieces of glue on a single `globalThis` namespace
 * whose name rotates per deploy (`vmf_4366f8`, `vmC_13cf2c`, …):
 *
 *  - a **signer** `fn(path) -> token` used to compute the `_=<token>`
 *    query parameter every protected endpoint requires;
 *  - an **installer** `fn(axiosInstance)` that registers the request
 *    interceptor (signing) *and* the response interceptor that decrypts
 *    the `{e:"<blob>"}` envelope the server returns.
 *
 * The namespace name, the prefix, and the function names all rotate on
 * every site deploy (`vmf_<id>` → `vmC_<id>` was just #15729), and the
 * VM's anti-tamper protection refuses to run outside a real browser
 * context — which is why hard-coding any of those is fragile
 * (issues #15643, #15703, #15729 — same bug, three times).
 *
 * The probe therefore identifies the namespace **purely by behaviour**:
 * it walks every top-level object on `window`, keeps only those whose
 * own keys look like obfuscator output (≥5 keys with ≥3 short alpha
 * identifiers à la `Qi`, `Xi`), and then on each surviving candidate
 * tries every member function for one that signs a known path or one
 * that registers a response interceptor on a fake axios. No name match
 * is required — future name rotations no longer need an extension
 * update.
 *
 * Instead, we host a hidden [WebView], load `comix.to/`, and let the
 * site's own bundle bootstrap. We then:
 *
 *   1. Pre-filter top-level objects on `window` by structural fingerprint
 *      (≥5 own keys, ≥3 short alpha names) so we never accidentally
 *      invoke nominally-named browser globals like `alert` / `confirm`.
 *      On surviving candidates, probe each member function: one
 *      returning a base64url token for a known path is the signer; one
 *      that registers a response interceptor on a fake axios is the
 *      installer.
 *   2. For every protected request, sign + fetch + run the response
 *      through the captured response interceptor, then re-wrap as
 *      `{result: <decoded>}` to match the DTOs in [Dto.kt].
 *   3. Hand the synthetic JSON back to OkHttp through
 *      [WebViewProxyInterceptor], which fabricates a [Response] so the
 *      rest of the extension stays an ordinary `HttpSource`.
 *
 * Because we delegate to the site's own crypto primitives, this survives
 * key rotation, name rotation, and full algorithm rewrites for free — as
 * long as `comix.to` itself can sign and decrypt in a browser, so can we.
 *
 * Requests carrying [PROXY_HEADER]` : 1` are intercepted here; everything
 * else flows through plain OkHttp.
 */
object Signer {
    private const val BASE_URL = "https://comix.to/"
    private const val PROBE_PATH = "/manga/g2rk/chapters"
    private const val LOAD_TIMEOUT_S = 25L
    private const val FETCH_TIMEOUT_S = 25L
    private const val PROBE_INTERVAL_MS = 250L
    private const val WEBVIEW_WIDTH = 1080
    private const val WEBVIEW_HEIGHT = 1920
    private const val BRIDGE_NAME = "ComixSignerBridge"

    const val PROXY_HEADER = "X-Comix-WebView-Proxy"

    private val handler by lazy { Handler(Looper.getMainLooper()) }

    @Volatile private var webView: WebView? = null

    @Volatile private var signerExpr: String? = null

    @Volatile private var installerExpr: String? = null

    @Volatile private var ready: CountDownLatch = CountDownLatch(1)
    private val initLock = Any()

    /** Per-call slot keyed by a unique correlation token. */
    private val pending = ConcurrentHashMap<String, FetchSlot>()

    private class FetchSlot {
        val latch = CountDownLatch(1)

        @Volatile var status: Int = 0

        @Volatile var body: String = ""

        @Volatile var error: String = ""
    }

    /** JS calls into this from inside the WebView to deliver fetch results. */
    private class JsBridge {
        @JavascriptInterface
        fun deliver(token: String, status: Int, body: String, error: String) {
            pending[token]?.let {
                it.status = status
                it.body = body
                it.error = error
                it.latch.countDown()
            }
        }
    }

    /** JSON body of a 2xx response, otherwise throws. */
    fun proxyFetch(apiPath: String): String {
        ensureReady()
        val signer = signerExpr ?: throw IOException("Comix: signer not initialized")
        val installer = installerExpr ?: throw IOException("Comix: response decryptor not initialized")
        val token = UUID.randomUUID().toString()
        val slot = FetchSlot()
        pending[token] = slot

        val pathLiteral = jsString(apiPath)
        val signerArg = jsString(extractSignablePath(apiPath))
        val tokenLiteral = jsString(token)
        // The site ships an axios response interceptor that decrypts the
        // `{e:"..."}` body into the real `{items,...}` payload. We don't have
        // an axios instance, so we capture the interceptor by feeding `v` a
        // fake axios, then call it manually with our fetch's response. The
        // result is wrapped as `{result: <decoded>}` to match the shape
        // `ChapterDetailsResponse` / `ChapterResponse` parse against.
        val js = """
            (async function() {
                var __t = $tokenLiteral;
                try {
                    var captured = { req: null, res: null };
                    var fakeAxios = {
                        interceptors: {
                            request:  { use: function(fn){ captured.req = fn; } },
                            response: { use: function(fn){ captured.res = fn; } }
                        },
                        defaults: { headers: { common: {} }, transformRequest: [], transformResponse: [] }
                    };
                    $installer(fakeAxios);

                    var sig = $signer($signerArg);
                    var p = $pathLiteral;
                    var sep = p.indexOf('?') === -1 ? '?' : '&';
                    var url = p + sep + '_=' + encodeURIComponent(sig);
                    var resp = await fetch(url, {
                        credentials: 'include',
                        headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
                    });
                    var text = await resp.text();
                    if (resp.status < 200 || resp.status >= 300) {
                        $BRIDGE_NAME.deliver(__t, resp.status, text, '');
                        return;
                    }

                    var raw;
                    try { raw = JSON.parse(text); }
                    catch (e) {
                        $BRIDGE_NAME.deliver(__t, resp.status, text, 'response is not JSON');
                        return;
                    }

                    var bodyOut;
                    if (raw && typeof raw === 'object' && 'e' in raw && captured.res) {
                        var fakeResp = {
                            data: raw,
                            status: resp.status,
                            statusText: resp.statusText,
                            headers: Object.fromEntries([...resp.headers.entries()]),
                            config: { url: url, method: 'get', baseURL: '/api/v1' },
                            request: {}
                        };
                        var decoded = await captured.res(fakeResp);
                        bodyOut = JSON.stringify({ result: decoded && decoded.data });
                    } else if (raw && typeof raw === 'object' && 'result' in raw) {
                        bodyOut = text;
                    } else {
                        bodyOut = JSON.stringify({ result: raw });
                    }
                    $BRIDGE_NAME.deliver(__t, resp.status, bodyOut, '');
                } catch (e) {
                    $BRIDGE_NAME.deliver(__t, 0, '', String((e && e.message) || e));
                }
            })();
        """.trimIndent()

        try {
            handler.post {
                webView?.evaluateJavascript(js, null)
            }
            if (!slot.latch.await(FETCH_TIMEOUT_S, TimeUnit.SECONDS)) {
                throw IOException("Comix: WebView fetch timed out for $apiPath")
            }
            if (slot.error.isNotEmpty()) {
                throw IOException("Comix: WebView fetch failed — ${slot.error}")
            }
            if (slot.status !in 200..299) {
                val excerpt = slot.body.take(200).replace("\n", " ").trim()
                throw IOException(
                    "Comix: API returned HTTP ${slot.status} for $apiPath" +
                        if (excerpt.isNotEmpty()) " (body: $excerpt)" else "",
                )
            }
            if (slot.body.isEmpty()) {
                throw IOException("Comix: API returned 2xx but empty body for $apiPath")
            }
            return slot.body
        } finally {
            pending.remove(token)
        }
    }

    /** Drops the cached WebView so the next call re-detects against a fresh bundle. */
    fun invalidate() {
        synchronized(initLock) {
            val wv = webView
            webView = null
            signerExpr = null
            installerExpr = null
            // Release any thread already waiting on the old latch before
            // swapping in a fresh one — otherwise they'd block until timeout.
            ready.countDown()
            ready = CountDownLatch(1)
            pending.values.forEach { it.latch.countDown() }
            pending.clear()
            if (wv != null) {
                handler.post {
                    runCatching {
                        wv.stopLoading()
                        wv.clearCache(true)
                        wv.destroy()
                    }
                }
            }
        }
    }

    private fun ensureReady() {
        if (signerExpr != null && installerExpr != null) return
        synchronized(initLock) {
            if (signerExpr != null && installerExpr != null) return
            // Don't spawn a second WebView while one is still loading /
            // probing — fall through to the latch await below.
            if (webView == null) initWebView()
        }
        if (!ready.await(LOAD_TIMEOUT_S, TimeUnit.SECONDS)) {
            invalidate()
            throw IOException(
                "Comix: signer not detected after ${LOAD_TIMEOUT_S}s. Try opening the source " +
                    "in WebView once to clear any anti-bot challenge, then refresh.",
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled", "JavascriptInterface")
    private fun initWebView() {
        val context = Injekt.get<Application>()
        val started = CountDownLatch(1)
        handler.post {
            val wv = WebView(context)
            wv.layoutParams = ViewGroup.LayoutParams(WEBVIEW_WIDTH, WEBVIEW_HEIGHT)
            wv.measure(
                View.MeasureSpec.makeMeasureSpec(WEBVIEW_WIDTH, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(WEBVIEW_HEIGHT, View.MeasureSpec.EXACTLY),
            )
            wv.layout(0, 0, WEBVIEW_WIDTH, WEBVIEW_HEIGHT)

            with(wv.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }
            wv.clearCache(true)

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

            wv.addJavascriptInterface(JsBridge(), BRIDGE_NAME)

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    schedulePoll(view)
                }
            }
            webView = wv
            wv.loadUrl(BASE_URL)
            started.countDown()
        }
        started.await()
    }

    private fun schedulePoll(wv: WebView) {
        val poll = object : Runnable {
            override fun run() {
                // If invalidate() ran between scheduling and firing, the
                // captured WebView is no longer the active one (or was
                // destroyed) — drop the poll silently.
                if (wv !== webView) return
                if (signerExpr != null && installerExpr != null) return
                wv.evaluateJavascript(PROBE_JS) { raw ->
                    // Returns either '' (not ready) or 'sig=<expr>;inst=<expr>'.
                    val unwrapped = raw?.trim()?.removeSurrounding("\"")
                        .orEmpty()
                        .replace("\\\"", "\"")
                    val parts = unwrapped.takeIf { it.isNotEmpty() && it != "null" }
                        ?.split(';')
                        ?.mapNotNull { it.trim().split('=', limit = 2).takeIf { p -> p.size == 2 }?.let { (k, v) -> k to v } }
                        ?.toMap()
                    val sig = parts?.get("sig")
                    val inst = parts?.get("inst")
                    if (!sig.isNullOrEmpty() && !inst.isNullOrEmpty()) {
                        signerExpr = sig
                        installerExpr = inst
                        ready.countDown()
                    } else {
                        handler.postDelayed(this, PROBE_INTERVAL_MS)
                    }
                }
            }
        }
        handler.postDelayed(poll, PROBE_INTERVAL_MS)
    }

    private fun jsString(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
        .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""

    /**
     * The signer takes the API path *before* the `/api/v1` prefix and *without* query
     * parameters. A request URL of `/api/v1/manga/g2rk/chapters?page=1` therefore
     * signs `/manga/g2rk/chapters`.
     */
    private fun extractSignablePath(apiPath: String): String = apiPath.substringBefore('?').removePrefix("/api/v1")

    /**
     * Identifies, by behaviour, two functions on the obfuscated namespace:
     *  - `sig`:  signer — `fn(path) -> ≥40-char base64url token`
     *  - `inst`: axios installer — `fn(axiosInstance)` registers a request
     *            interceptor that signs URLs *and* a response interceptor
     *            that decrypts `{e:"..."}` bodies. Probed by feeding a
     *            fake axios and checking whether
     *            `interceptors.response.use` was called.
     *
     * Two-tier candidate selection so the common case is cheap:
     *  1. **Fast path** — names matching `^vm[A-Za-z]_` (every observed
     *     deploy so far: `vmf_<id>`, `vmC_<id>`). Hits with one regex
     *     test per top-level key; nearly free.
     *  2. **Slow fallback** — only if the fast path finds nothing, walk
     *     remaining top-level objects and keep those with the structural
     *     fingerprint of obfuscator output: ≥5 own keys, ≥3 of them 1-
     *     to 3-character alphabetic identifiers (`Qi`, `Xi`, `Gi`, …).
     *     This excludes every nominally-named browser global, so we
     *     never invoke `alert`, `confirm`, `open`, etc. as a side
     *     effect.
     *
     * If the site ever rotates the namespace beyond `vm[A-Za-z]_*` —
     * something that hasn't happened yet — the fallback catches it
     * without an extension update.
     *
     * Returns `'sig=window[<ns>].<a>;inst=window[<ns>].<b>'` once both
     * are found, otherwise `''` so the polling loop tries again.
     */
    private val PROBE_JS = """
        (function() {
            try {
                var probe = '$PROBE_PATH';
                var tokenRe = /^[A-Za-z0-9_-]{40,200}${'$'}/;
                var shortRe = /^[A-Za-z]{1,3}${'$'}/;
                var nameRe  = /^vm[A-Za-z]_/;

                function tryProbe(ns, topName) {
                    var sig = '', inst = '';
                    var fnames;
                    try { fnames = Object.keys(ns); } catch (e) { return null; }
                    for (var j = 0; j < fnames.length; j++) {
                        var fn = ns[fnames[j]];
                        if (typeof fn !== 'function') continue;
                        var ref = 'window[' + JSON.stringify(topName) + '].' + fnames[j];
                        if (!sig) {
                            try {
                                var out = fn(probe);
                                if (typeof out === 'string' && out !== probe && tokenRe.test(out)) {
                                    sig = ref;
                                }
                            } catch (e) {}
                        }
                        if (!inst) {
                            try {
                                var got = false;
                                fn({
                                    interceptors: {
                                        request:  { use: function() {} },
                                        response: { use: function() { got = true; } }
                                    },
                                    defaults: { headers: { common: {} }, transformRequest: [], transformResponse: [] }
                                });
                                if (got) inst = ref;
                            } catch (e) {}
                        }
                        if (sig && inst) return { sig: sig, inst: inst };
                    }
                    return null;
                }

                var keys = Object.keys(window);

                // Fast path: matches every observed deploy.
                for (var i = 0; i < keys.length; i++) {
                    var topName = keys[i];
                    if (!nameRe.test(topName)) continue;
                    var ns = window[topName];
                    if (!ns || typeof ns !== 'object' || ns === window) continue;
                    var hit = tryProbe(ns, topName);
                    if (hit) return 'sig=' + hit.sig + ';inst=' + hit.inst;
                }

                // Fallback: structural fingerprint, no name constraint.
                for (var i = 0; i < keys.length; i++) {
                    var topName = keys[i];
                    if (nameRe.test(topName)) continue; // already tried
                    var ns = window[topName];
                    if (!ns || typeof ns !== 'object' || ns === window) continue;
                    var fnames;
                    try { fnames = Object.keys(ns); } catch (e) { continue; }
                    if (fnames.length < 5) continue;
                    var shortAlpha = 0;
                    for (var s = 0; s < fnames.length; s++) {
                        if (shortRe.test(fnames[s])) shortAlpha++;
                    }
                    if (shortAlpha < 3) continue;
                    var hit = tryProbe(ns, topName);
                    if (hit) return 'sig=' + hit.sig + ';inst=' + hit.inst;
                }
            } catch (e) {}
            return '';
        })()
    """.trimIndent()
}

/**
 * Intercepts requests carrying [Signer.PROXY_HEADER] = `1` and routes them
 * through the WebView (where signing + cookies live) instead of letting
 * OkHttp issue a fresh request that would arrive with a different session.
 *
 * If the proxy fetch fails, the [IOException] is allowed to propagate so
 * Mihon surfaces the real cause as a toast. Returning a synthetic 502 with
 * an empty body would just cause a downstream `MissingFieldException` in
 * `parseAs` and bury the original error.
 */
object WebViewProxyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(Signer.PROXY_HEADER) != "1") {
            return chain.proceed(request)
        }
        val apiPath = request.url.encodedPath +
            (request.url.encodedQuery?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")

        val body = Signer.proxyFetch(apiPath)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}
