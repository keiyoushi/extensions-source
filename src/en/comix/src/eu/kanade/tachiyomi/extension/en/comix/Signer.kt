package eu.kanade.tachiyomi.extension.en.comix

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
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
 * whose name rotates per deploy (`vmf_4366f8`, `vmC_13cf2c`, `vmx_...`):
 *
 *   - a **signer** `fn(path) -> token` for the `_=<token>` query parameter;
 *   - an **installer** `fn(axiosInstance)` that registers a response
 *     interceptor — the `{e:"..."}` decryptor — on its argument.
 *
 * The VM's anti-tamper protection refuses to run outside a real browser,
 * so we host one off-screen [WebView] for the whole session. On boot:
 *
 *   1. Load `comix.to/` so the bundle bootstraps and mounts its namespace.
 *   2. Detect signer + installer purely by behaviour. **Fast path** matches
 *      `^vm[A-Za-z]_` (every observed deploy so far); **fallback** walks
 *      remaining top-level objects and keeps those whose own keys look
 *      like obfuscator output (≥5 keys, ≥3 short 1-3 char alphabetic
 *      identifiers — `Qi`, `Xi`, `Gi`, …). Excludes every nominally-named
 *      browser global, so the probe never invokes `alert`/`confirm`/`open`
 *      as a side effect. If the site ever rotates the namespace beyond
 *      `vm[A-Za-z]_`, no extension update is needed.
 *   3. Hand a fake axios to the installer, capture the response interceptor,
 *      stash it on `window.__cmxRes`. Done once per session — every later
 *      `proxyFetch` reuses the same captured function instead of paying the
 *      fakeAxios + installer round-trip per call. On big chapter lists
 *      (10s-100s of pages) that's the difference between minutes and tens
 *      of seconds.
 *
 * Once detected, the signer/installer expressions are persisted to
 * [SharedPreferences] so warm starts skip the probe step entirely — the
 * WebView still has to load the bundle (anti-tamper checks need a real
 * `window`), but it can install the decryptor directly from the cached
 * installer expression on `onPageFinished`. If those cached expressions
 * are stale (site rotated since the last successful probe), the install
 * JS signals [JsBridge.installerMissing] and the next poll runs a fresh
 * probe inside the same boot cycle — so post-rotation cold starts don't
 * wait the full timeout before recovering.
 *
 * On a 401/403 from the API, [WebViewProxyInterceptor] drops every cached
 * piece of state via [invalidate] and the next request triggers a fresh
 * probe. That's how the extension self-heals when the site rotates.
 *
 * Requests carrying [PROXY_HEADER]` : 1` are intercepted by
 * [WebViewProxyInterceptor]; everything else flows through plain OkHttp.
 */
@SuppressLint("StaticFieldLeak")
object Signer {
    private const val BASE_URL = "https://comix.to/"
    private const val PROBE_PATH = "/manga/g2rk/chapters"
    private const val LOAD_TIMEOUT_S = 12L
    private const val FETCH_TIMEOUT_S = 25L
    private const val PROBE_INTERVAL_MS = 200L
    private const val WEBVIEW_WIDTH = 1080
    private const val WEBVIEW_HEIGHT = 1920
    private const val BRIDGE_NAME = "ComixSignerBridge"
    private const val CMX_RES_GLOBAL = "__cmxRes"

    private const val PREF_FILE = "comix_signer"
    private const val KEY_SIGNER_EXPR = "signer_expr"
    private const val KEY_INSTALLER_EXPR = "installer_expr"

    const val PROXY_HEADER = "X-Comix-WebView-Proxy"

    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private val prefs: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences(PREF_FILE, 0)
    }

    @Volatile private var webView: WebView? = null

    @Volatile private var signerExpr: String? = null

    @Volatile private var installerExpr: String? = null

    @Volatile private var decryptorReady: Boolean = false

    @Volatile private var ready: CountDownLatch = CountDownLatch(1)
    private val initLock = Any()

    /** Per-call correlation slot; one outstanding per async bridge call. */
    private val pending = ConcurrentHashMap<String, Slot>()

    private class Slot {
        val latch = CountDownLatch(1)

        @Volatile var value: String = ""

        @Volatile var error: String = ""
    }

    /** Receives all JS->Kotlin callbacks from inside the hidden WebView. */
    private class JsBridge {
        @JavascriptInterface
        fun probeResult(sigExpr: String, instExpr: String) {
            if (sigExpr.isEmpty() || instExpr.isEmpty()) return
            signerExpr = sigExpr
            installerExpr = instExpr
            prefs.edit()
                .putString(KEY_SIGNER_EXPR, sigExpr)
                .putString(KEY_INSTALLER_EXPR, instExpr)
                .apply()
            // Probe succeeded — kick off decryptor install on the WebView.
            webView?.let { wv -> handler.post { wv.evaluateJavascript(installDecryptorJs(instExpr), null) } }
        }

        @JavascriptInterface
        fun decryptorInstalled() {
            decryptorReady = true
            ready.countDown()
        }

        /**
         * Called by the install JS when the cached installer expression
         * doesn't resolve to a function — happens on the first warm start
         * after a site bundle rotation. Drops the stale cached refs and
         * falls back to a full probe.
         */
        @JavascriptInterface
        fun installerMissing() {
            signerExpr = null
            installerExpr = null
            prefs.edit()
                .remove(KEY_SIGNER_EXPR)
                .remove(KEY_INSTALLER_EXPR)
                .apply()
        }

        @JavascriptInterface
        fun deliver(token: String, value: String, error: String) {
            pending[token]?.let {
                it.value = value
                it.error = error
                it.latch.countDown()
            }
        }
    }

    // ============================== Public API =============================

    /**
     * Signs [apiPath], fetches it via the WebView's native HTTP stack, runs
     * the response through the cached site-side response interceptor, and
     * returns the resulting `{result: <decoded>}` JSON body. Sign + fetch +
     * decrypt happen in a single WebView round-trip so the response body
     * never has to cross the Kotlin↔JS boundary as a source literal —
     * Chromium's HTTP stack hands it directly to the decryptor closure.
     *
     * The decryptor is pre-captured into `window.__cmxRes` at WebView init,
     * so per-call we skip the fakeAxios setup + installer round-trip that
     * D-Brox's original implementation paid every request. On big chapter
     * lists (10s-100s of pages) this saves seconds.
     */
    fun proxyFetch(apiPath: String): String {
        ensureReady()
        val signer = signerExpr ?: throw IOException("Comix: signer not initialized")
        val pathLiteral = jsString(apiPath)
        // The signer takes the API path *before* the `/api/v1` prefix and
        // *without* query parameters — e.g. `/manga/g2rk/chapters?page=1`
        // signs `/manga/g2rk/chapters`.
        val signablePath = apiPath.substringBefore('?').removePrefix("/api/v1")
        val signerArg = jsString(signablePath)
        return exec(FETCH_TIMEOUT_S, "proxyFetch") { corr ->
            """
            (async function(__t) {
                try {
                    var sig = $signer($signerArg);
                    if (!sig) {
                        $BRIDGE_NAME.deliver(__t, '', 'signer returned empty token');
                        return;
                    }
                    var p = $pathLiteral;
                    var sep = p.indexOf('?') === -1 ? '?' : '&';
                    var url = p + sep + '_=' + encodeURIComponent(sig);
                    var resp = await fetch(url, {
                        credentials: 'include',
                        headers: { 'Accept': 'application/json', 'X-Requested-With': 'XMLHttpRequest' }
                    });
                    var text = await resp.text();
                    if (resp.status < 200 || resp.status >= 300) {
                        $BRIDGE_NAME.deliver(__t, '', 'API returned HTTP ' + resp.status + (text ? ': ' + text.slice(0, 200) : ''));
                        return;
                    }
                    var raw;
                    try { raw = JSON.parse(text); }
                    catch (e) {
                        $BRIDGE_NAME.deliver(__t, '', 'response is not JSON');
                        return;
                    }
                    var bodyOut;
                    if (raw && typeof raw === 'object' && 'e' in raw) {
                        // Encrypted — run through the pre-captured response
                        // interceptor (`window.__cmxRes`, installed once on
                        // WebView boot). The fakeResp shape mirrors what
                        // axios feeds its interceptors so the site's logic
                        // recognises it as one of its own responses.
                        if (typeof window.$CMX_RES_GLOBAL !== 'function') {
                            $BRIDGE_NAME.deliver(__t, '', 'decryptor missing on window');
                            return;
                        }
                        var fakeResp = {
                            data: raw,
                            status: resp.status,
                            statusText: resp.statusText,
                            headers: Object.fromEntries([...resp.headers.entries()]),
                            config: { url: url, method: 'get', baseURL: '/api/v1' },
                            request: {}
                        };
                        var decoded = await window.$CMX_RES_GLOBAL(fakeResp);
                        bodyOut = JSON.stringify({result: decoded && decoded.data});
                    } else if (raw && typeof raw === 'object' && 'result' in raw) {
                        // Already in the expected shape — pass through.
                        bodyOut = text;
                    } else {
                        bodyOut = JSON.stringify({result: raw});
                    }
                    $BRIDGE_NAME.deliver(__t, bodyOut, '');
                } catch (e) {
                    $BRIDGE_NAME.deliver(__t, '', String((e && e.message) || e));
                }
            })(${jsString(corr)});
            """.trimIndent()
        }
    }

    /**
     * Kicks off WebView creation + probe + decryptor install on a
     * background thread. Optional — if the caller invokes this at source
     * construction time, the first user-triggered request lands on a
     * warm WebView instead of paying the cold-start latency.
     */
    fun warmUp() {
        Thread { runCatching { ensureReady() } }.start()
    }

    /**
     * Drops every cached piece of state — signer/installer expressions,
     * token cache, the live WebView — so the next call re-bootstraps from
     * scratch. Called by [WebViewProxyInterceptor] on 401/403, which is
     * how the extension self-heals when the site rotates its bundle.
     */
    fun invalidate() {
        synchronized(initLock) {
            val wv = webView
            webView = null
            signerExpr = null
            installerExpr = null
            decryptorReady = false
            // Remove the keys we own explicitly so unrelated state added by
            // future contributors to this prefs file isn't silently wiped.
            prefs.edit()
                .remove(KEY_SIGNER_EXPR)
                .remove(KEY_INSTALLER_EXPR)
                .apply()
            // Release any thread already waiting on the old latch before
            // swapping in a fresh one — otherwise they'd block until timeout.
            ready.countDown()
            ready = CountDownLatch(1)
            // Mark any awaiters with an error so they don't see "" and
            // mistake it for a successful empty token, then cache it.
            pending.values.forEach {
                it.error = "invalidated mid-flight (site rotation)"
                it.latch.countDown()
            }
            pending.clear()
            if (wv != null) {
                handler.post {
                    runCatching {
                        wv.stopLoading()
                        try {
                            wv.clearCache(true)
                        } catch (_: RuntimeException) { /* Suwayomi stub */ }
                        wv.destroy()
                    }
                }
            }
        }
    }

    // ============================== Internals ==============================

    private fun ensureReady() {
        if (decryptorReady) return
        synchronized(initLock) {
            if (decryptorReady) return
            // Don't spawn a second WebView while a first one is still loading
            // / probing — fall through to the latch await below.
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
        // Warm-start: try cached expressions so onPageFinished can skip probe
        // and install the decryptor directly.
        signerExpr = prefs.getString(KEY_SIGNER_EXPR, null)
        installerExpr = prefs.getString(KEY_INSTALLER_EXPR, null)

        val context = Injekt.get<Application>()
        val started = CountDownLatch(1)
        handler.post {
            val wv = WebView(context)
            try {
                wv.layoutParams = ViewGroup.LayoutParams(WEBVIEW_WIDTH, WEBVIEW_HEIGHT)
                wv.measure(
                    View.MeasureSpec.makeMeasureSpec(WEBVIEW_WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(WEBVIEW_HEIGHT, View.MeasureSpec.EXACTLY),
                )
                wv.layout(0, 0, WEBVIEW_WIDTH, WEBVIEW_HEIGHT)
                wv.clearCache(true)
            } catch (_: RuntimeException) {
                // Suwayomi's `WebView` shim throws "Stub!" on these. Mihon's
                // real WebView and Komu's WKWebView shim handle them fine.
            }

            with(wv.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
                cacheMode = WebSettings.LOAD_NO_CACHE
            }

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true)

            wv.addJavascriptInterface(JsBridge(), BRIDGE_NAME)

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    // Suwayomi fires onPageFinished with about:blank as the
                    // initial state — skip it, the real page hasn't loaded yet.
                    if (url == "about:blank") return
                    if (view !== webView) return
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
                // captured WebView is no longer the active one — bail.
                if (wv !== webView) return
                if (decryptorReady) return
                val cachedInstaller = installerExpr
                if (signerExpr != null && cachedInstaller != null) {
                    // Either warm start (cached expressions present) or
                    // cold start that has just succeeded the probe — try
                    // installing the decryptor.
                    wv.evaluateJavascript(installDecryptorJs(cachedInstaller), null)
                } else {
                    wv.evaluateJavascript(PROBE_JS, null)
                }
                handler.postDelayed(this, PROBE_INTERVAL_MS)
            }
        }
        handler.postDelayed(poll, PROBE_INTERVAL_MS)
    }

    /**
     * Runs a JS expression that resolves via [JsBridge.deliver]. The caller
     * receives a unique correlation token and must reference it as `__t`
     * inside the JS (we pass it as the IIFE argument); the function blocks
     * until the JS calls `deliver(token, value, error)` or the timeout is
     * reached.
     */
    private fun exec(timeoutS: Long, label: String, buildJs: (token: String) -> String): String {
        val wv = webView ?: throw IOException("Comix: WebView not initialized ($label)")
        val token = UUID.randomUUID().toString()
        val slot = Slot()
        pending[token] = slot
        try {
            val js = buildJs(token)
            handler.post { wv.evaluateJavascript(js, null) }
            if (!slot.latch.await(timeoutS, TimeUnit.SECONDS)) {
                throw IOException("Comix: $label timed out after ${timeoutS}s")
            }
            if (slot.error.isNotEmpty()) {
                throw IOException("Comix: $label failed — ${slot.error}")
            }
            return slot.value
        } finally {
            pending.remove(token)
        }
    }

    /** Captures the site's axios response interceptor into [CMX_RES_GLOBAL]
     * so per-call decrypts can skip the fake-axios setup. Idempotent;
     * signals [JsBridge.installerMissing] if the cached installer
     * expression no longer resolves to a function (site rotated). */
    private fun installDecryptorJs(installer: String): String = """
        (function() {
            try {
                if (typeof window.$CMX_RES_GLOBAL === 'function') {
                    $BRIDGE_NAME.decryptorInstalled();
                    return;
                }
                if (typeof $installer !== 'function') {
                    $BRIDGE_NAME.installerMissing();
                    return;
                }
                $installer({
                    interceptors: {
                        request:  { use: function() {} },
                        response: { use: function(fn) { window.$CMX_RES_GLOBAL = fn; } }
                    },
                    defaults: { headers: { common: {} }, transformRequest: [], transformResponse: [] }
                });
                if (typeof window.$CMX_RES_GLOBAL === 'function') {
                    $BRIDGE_NAME.decryptorInstalled();
                }
            } catch (e) {}
        })();
    """.trimIndent()

    /**
     * Identifies, by behaviour only, two functions on the obfuscated
     * namespace:
     *  - signer — `fn(path) -> ≥40-char base64url token`
     *  - installer — `fn(axiosInstance)` registers a response interceptor
     *    on its argument (the `{e:"..."}` decryptor)
     *
     * Two-tier candidate selection so the common case is cheap:
     *  1. Fast path matches `^vm[A-Za-z]_` (every observed deploy so far).
     *  2. Fallback walks remaining top-level objects and keeps those whose
     *     own keys look like obfuscator output (≥5 keys, ≥3 short 1-3
     *     char alphabetic identifiers — `Qi`, `Xi`, `Gi`, …). Excludes
     *     every nominally-named browser global, so we never accidentally
     *     invoke `alert`, `confirm`, `open`, etc. as a side effect.
     *
     * Delivers via `$BRIDGE_NAME.probeResult(sig, inst)`.
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
                        var ref = 'window[' + JSON.stringify(topName) + '][' + JSON.stringify(fnames[j]) + ']';
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
                    if (hit) { $BRIDGE_NAME.probeResult(hit.sig, hit.inst); return; }
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
                    if (hit) { $BRIDGE_NAME.probeResult(hit.sig, hit.inst); return; }
                }
            } catch (e) {}
        })()
    """.trimIndent()
}

/**
 * Intercepts requests carrying [Signer.PROXY_HEADER] = `1`, signs them via
 * the cached signer, and lets OkHttp do the actual fetch (native HTTP +
 * Mihon's rate limiter handle concurrency properly, which the previous
 * single-WebView pipeline couldn't). On the way back, if the body carries
 * the `{"e":"..."}` envelope, [Signer.decrypt] unwraps it.
 *
 * On 401/403 with a `_=` query parameter, the signer is invalidated and
 * the request is retried once with a fresh token — that's how cache
 * rotation heals.
 */
object WebViewProxyInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(Signer.PROXY_HEADER) != "1") {
            return chain.proceed(request)
        }

        val apiPath = request.url.encodedPath +
            (request.url.encodedQuery?.takeIf { it.isNotEmpty() }?.let { "?$it" } ?: "")
        val body = try {
            Signer.proxyFetch(apiPath)
        } catch (e: IOException) {
            // On a server-side rejection (HTTP 4xx error message embedded in
            // the exception, see proxyFetch JS), drop the cached signer and
            // give one more attempt with a fresh probe — that's how we self-
            // heal across site rotations.
            if (e.message?.contains("HTTP 401") == true || e.message?.contains("HTTP 403") == true) {
                Signer.invalidate()
                Signer.proxyFetch(apiPath)
            } else {
                throw e
            }
        }

        return Response.Builder()
            .request(request)
            .protocol(okhttp3.Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }
}

/** Wraps [s] as a valid JS string literal with all special characters escaped. */
private fun jsString(s: String): String = "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"")
    .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t") + "\""
