package eu.kanade.tachiyomi.extension.all.lunaranime

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import keiyoushi.utils.applicationContext
import okhttp3.Interceptor
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

const val DESTROY_TIMEOUT_MS = 300000L // 5m

class LunarWebViewSigner(
    private val baseUrl: String,
    private val apiUrl: String,
) {
    private val handler by lazy { Handler(Looper.getMainLooper()) }
    private var cachedWv: WebView? = null
    private var destroyWv: Runnable? = null
    private val bridgeName = "LunarSignerBridge"

    private val globalWebView: WebView
        get() {
            destroyWv?.let { handler.removeCallbacks(it) }
            if (cachedWv == null) {
                cachedWv = WebView(applicationContext).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                }
            }
            destroyWv = Runnable {
                cachedWv?.destroy()
                cachedWv = null
                destroyWv = null
            }.also {
                handler.postDelayed(it, DESTROY_TIMEOUT_MS)
            }
            return cachedWv!!
        }

    fun dpopInterceptor() = Interceptor { chain ->
        var req = chain.request()
        val url = req.url.toString()

        if (!url.contains(apiUrl)) return@Interceptor chain.proceed(req)

        val dpop = signUrlWv(req.method, url.substringBefore('?')) ?: ""
        if (dpop.isNotEmpty()) req = req.newBuilder().addHeader("dpop", dpop).build()

        val resp = chain.proceed(req)

        if (resp.code == 403 && resp.peekBody(1024).string().contains("validate", ignoreCase = true)) {
            handler.post { destroyWv?.run() }
            resp.close()

            throw IOException("Solve captcha in webview and retry")
        }
        resp
    }

    @Synchronized
    private fun signUrlWv(method: String, apiUrl: String): String? {
        val latch = CountDownLatch(1)
        var result: String? = null

        handler.post {
            try {
                val webView = globalWebView

                webView.addJavascriptInterface(
                    object {
                        @JavascriptInterface
                        fun onResult(dpop: String) {
                            result = dpop
                            latch.countDown()
                        }
                    },
                    bridgeName,
                )

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        view.evaluateJavascript(buildJs(method, apiUrl, bridgeName), null)
                    }
                }

                webView.loadDataWithBaseURL(baseUrl, " ", "text/html", "utf-8", null)
            } catch (e: Throwable) {
                latch.countDown()
            }
        }

        if (!latch.await(5000L, TimeUnit.MILLISECONDS)) {
            return null
        }

        return result
    }

    private fun buildJs(method: String, apiUrl: String, bridgeName: String): String = """
        (function() {
            function b64url(str) {
                return btoa(str)
                    .replace(/\+/g, '-')
                    .replace(/\//g, '_')
                    .replace(/=/g, '');
            }

            function bytes(str) {
                return new TextEncoder().encode(str);
            }

            function randJti() {
                return b64url(String.fromCharCode.apply(
                    null,
                    crypto.getRandomValues(new Uint8Array(16))
                ));
            }

            function encode(obj) {
                return b64url(JSON.stringify(obj));
            }

            function loadKey() {
                return new Promise((resolve, reject) => {
                    const req = indexedDB.open("dbinfo");
                    let db;

                    req.onsuccess = function(e) {
                        db = e.target.result;
                        const tx = db.transaction("store", "readonly");
                        const store = tx.objectStore("store");
                        const get = store.get("device-key-secure");

                        get.onsuccess = function() {
                            db.close();
                            resolve(get.result);
                        };

                        get.onerror = function() {
                            db.close();
                            reject(get.error);
                        };
                    };

                    req.onerror = function() {
                        reject(req.error);
                    };
                });
            }

            loadKey().then(async function(keyPair) {
                if (!window.__cachedKeyPair) {
                    window.__cachedKeyPair = keyPair;
                }

                const kp = window.__cachedKeyPair;

                const header = {
                    typ: "dpop+jwt",
                    alg: "ES256",
                    jwk: kp.publicJwk
                };

                const payload = {
                    htm: "$method",
                    htu: "$apiUrl",
                    iat: Math.floor(Date.now() / 1000),
                    jti: randJti()
                };

                const h = encode(header);
                const p = encode(payload);
                const input = h + "." + p;

                const sig = await crypto.subtle.sign(
                    { name: "ECDSA", hash: "SHA-256" },
                    kp.privateKey,
                    bytes(input)
                );

                const s = b64url(String.fromCharCode.apply(null, new Uint8Array(sig)));

                window.$bridgeName.onResult(input + "." + s);

            }).catch(function() {
                window.$bridgeName.onResult("");
            });
        })();
    """.trimIndent()
}
