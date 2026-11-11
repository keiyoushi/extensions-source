package keiyoushi.lib.webviewpatch

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import kotlinx.coroutines.suspendCancellableCoroutine
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.coroutines.resume

/**
 * In-process loopback self-test: stands up a tiny HTTP server bound to localhost, loads it in a
 * throwaway [WebView], and reports whether the requests still carried `X-Requested-With`. This is the
 * outcome check that makes an on-device patch safe — it verifies the header is actually gone from the
 * wire (which [android.webkit.WebResourceRequest] headers never show), not just that bytes were written.
 *
 * The page runs JavaScript and, besides the main navigation, issues a `fetch` and loads a subresource
 * back to the server, and the server checks the header is absent on all of them — so a build that
 * re-adds the header on an XHR (not only the main GET) is still caught. The server is loopback-only
 * (bound to 127.0.0.1), short-lived, and torn down immediately after.
 *
 * Cleartext caveat: the self-test loads `http://127.0.0.1`, so an app whose network-security-config forbids
 * cleartext to localhost gets a permanent [Result.INCONCLUSIVE] here — the stripper caches that back-off and
 * leaves the header in (fail-safe), making the patch a cached no-op on such a host. Mihon permits it.
 */
internal object LoopbackProbe {

    enum class Result { STRIPPED, PRESENT, INCONCLUSIVE }

    private const val TIMEOUT_MS = 4_000L

    private const val MAIN_PATH = "/xrw-selftest"
    private const val XHR_PATH = "/xrw-xhr"
    private const val SUB_PATH = "/xrw-sub.png"

    private val PAGE = (
        "<!doctype html><html><head><meta charset=utf-8></head><body>" +
            "<img src=\"$SUB_PATH\" alt=\"\">" +
            "<script>fetch(\"$XHR_PATH\").catch(function(){});</script>" +
            "</body></html>"
        ).toByteArray(Charsets.US_ASCII)

    /**
     * Suspending self-test. Resumes once with the result. The WebView is driven on the main thread (posted by [run]);
     * the caller stays on whatever dispatcher it suspended from, so callers on [kotlinx.coroutines.Dispatchers.IO]
     * keep their memory/store work off the main thread.
     */
    suspend fun probe(context: Context): Result = suspendCancellableCoroutine { cont ->
        run(context) { result -> if (cont.isActive) cont.resume(result) }
    }

    /** Runs the self-test and delivers [onResult] exactly once, on a background thread. */
    @SuppressLint("SetJavaScriptEnabled")
    fun run(context: Context, onResult: (Result) -> Unit) {
        val done = AtomicBoolean(false)
        fun finish(r: Result, webView: WebView?) {
            if (!done.compareAndSet(false, true)) return
            Handler(Looper.getMainLooper()).post { runCatching { webView?.destroy() } }
            onResult(r)
        }

        val server = try {
            ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        } catch (t: Throwable) {
            onResult(Result.INCONCLUSIVE)
            return
        }
        val port = server.localPort
        val webViewRef = arrayOfNulls<WebView>(1)

        thread(name = "xrw-loopback", isDaemon = true) {
            server.use { srv ->
                srv.soTimeout = TIMEOUT_MS.toInt()
                val deadline = System.currentTimeMillis() + TIMEOUT_MS
                var anyHeader = false
                var sawXhr = false
                try {
                    // Accept the main page, the subresource and the XHR; stop once the XHR arrived (JS ran) or time runs out.
                    while (!sawXhr && System.currentTimeMillis() < deadline) {
                        val socket = try {
                            srv.accept()
                        } catch (t: Throwable) {
                            break
                        }
                        val (path, header) = socket.use { handle(it) }
                        if (header) anyHeader = true
                        if (path.startsWith(XHR_PATH)) sawXhr = true
                    }
                } catch (t: Throwable) {
                    finish(Result.INCONCLUSIVE, webViewRef[0])
                    return@thread
                }
                val result = when {
                    anyHeader -> Result.PRESENT
                    sawXhr -> Result.STRIPPED
                    else -> Result.INCONCLUSIVE // the XHR never reached us: JS or cleartext blocked, can't conclude
                }
                finish(result, webViewRef[0])
            }
        }

        // Watchdog: if the WebView never reaches the server (cleartext blocked, load failed), don't hang.
        Handler(Looper.getMainLooper()).postDelayed({
            if (!done.get()) {
                runCatching { server.close() }
                finish(Result.INCONCLUSIVE, webViewRef[0])
            }
        }, TIMEOUT_MS + 1_500L)

        Handler(Looper.getMainLooper()).post {
            // A throwaway WebView for the localhost self-test, not a source page load (runWebView is for those):
            // it loads only 127.0.0.1 to confirm the patch dropped the header on the wire. Constructing a WebView
            // can throw (a host with no/updating WebView provider); catch it and finish INCONCLUSIVE rather than
            // crash the app from this main-thread post.
            try {
                // ast-grep-ignore: manual-webview -- the self-test IS the raw-WebView check; runWebView can't test it.
                val webView = WebView(context)
                webViewRef[0] = webView
                webView.settings.javaScriptEnabled = true
                webView.webViewClient = WebViewClient()
                webView.loadUrl("http://127.0.0.1:$port$MAIN_PATH")
            } catch (t: Throwable) {
                runCatching { server.close() }
                finish(Result.INCONCLUSIVE, webViewRef[0])
            }
        }
    }

    /** Reads one request and returns its path and whether an `X-Requested-With` header was present, then answers it. */
    private fun handle(socket: Socket): Pair<String, Boolean> {
        val input = socket.getInputStream()
        val head = StringBuilder()
        val buf = ByteArray(1)
        // Read until the blank line that ends the request head (bounded).
        while (head.length < 16_384 && !head.endsWith("\r\n\r\n")) {
            if (input.read(buf) < 0) break
            head.append(buf[0].toInt().toChar())
        }
        val requestLine = head.lineSequence().firstOrNull().orEmpty()
        val path = requestLine.split(' ').getOrElse(1) { "" }
        val hasHeader = head.lineSequence().any { it.substringBefore(':').trim().equals("X-Requested-With", ignoreCase = true) }

        val body = if (path.startsWith(MAIN_PATH)) PAGE else "ok".toByteArray(Charsets.US_ASCII)
        val type = if (path.startsWith(MAIN_PATH)) "text/html" else "text/plain"
        socket.getOutputStream().apply {
            write(
                (
                    "HTTP/1.1 200 OK\r\n" +
                        "Content-Type: $type\r\n" +
                        "Content-Length: ${body.size}\r\n" +
                        "Connection: close\r\n\r\n"
                    ).toByteArray(Charsets.US_ASCII),
            )
            write(body)
            flush()
        }
        return path to hasHeader
    }
}
