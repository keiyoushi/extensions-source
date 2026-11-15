package keiyoushi.runtime.webviewpatch

import keiyoushi.utils.runWebView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * In-process loopback self-test: stands up a tiny HTTP server bound to localhost, loads it in a
 * throwaway WebView (through the core [runWebView], not a hand-built WebView), and reports whether the
 * requests still carried `X-Requested-With`. This is the outcome check that makes an on-device patch
 * safe — it verifies the header is actually gone from the wire (which [android.webkit.WebResourceRequest]
 * headers never show), not just that bytes were written.
 *
 * The page runs JavaScript and, besides the main navigation, issues a `fetch` and loads a subresource
 * back to the server, and the server checks the header is absent on all of them — so a build that
 * re-adds the header on an XHR (not only the main GET) is still caught. The server is loopback-only
 * (bound to 127.0.0.1), short-lived, and torn down immediately after.
 *
 * Cleartext caveat: the self-test loads `http://127.0.0.1`, so an app whose network-security-config forbids
 * cleartext to localhost gets a permanent [Result.INCONCLUSIVE] here — the stripper caches that back-off and
 * the attempt reports failed or unsupported, so the patch never applies on such a host. Mihon permits it.
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
     * Suspending self-test. Stands up the loopback server, drives a [runWebView] run that loads it, and returns
     * the server's verdict. The server thread computes the verdict within [TIMEOUT_MS] (even when nothing reaches
     * it) and hands it to the run via [CompletableDeferred]; the run's own timeout is only a backstop for a wedged
     * thread. Any failure — a WebView that can't be constructed, a render-process death, the backstop timeout —
     * maps to [Result.INCONCLUSIVE], so the self-test always yields a verdict rather than throwing. [runWebView]
     * owns the WebView and its context.
     */
    suspend fun probe(): Result {
        val server = try {
            ServerSocket(0, 8, InetAddress.getByName("127.0.0.1"))
        } catch (t: Throwable) {
            return Result.INCONCLUSIVE
        }
        val port = server.localPort
        val verdict = CompletableDeferred<Result>()

        thread(name = "xrw-loopback", isDaemon = true) {
            val result = try {
                server.use { srv ->
                    srv.soTimeout = TIMEOUT_MS.toInt()
                    val deadline = TimeSource.Monotonic.markNow() + TIMEOUT_MS.milliseconds
                    var anyHeader = false
                    var sawXhr = false
                    // Accept the main page, the subresource and the XHR; stop once the XHR arrived (JS ran) or time runs out.
                    while (!sawXhr && deadline.hasNotPassedNow()) {
                        val socket = try {
                            srv.accept()
                        } catch (t: Throwable) {
                            break
                        }
                        val (path, header) = socket.use { handle(it) }
                        if (header) anyHeader = true
                        if (path.startsWith(XHR_PATH)) sawXhr = true
                    }
                    when {
                        anyHeader -> Result.PRESENT
                        sawXhr -> Result.STRIPPED
                        else -> Result.INCONCLUSIVE // the XHR never reached us: JS or cleartext blocked, can't conclude
                    }
                }
            } catch (t: Throwable) {
                Result.INCONCLUSIVE
            }
            verdict.complete(result)
        }

        return try {
            // A throwaway run for the localhost self-test, not a source page load: it loads only 127.0.0.1 to
            // confirm the patch dropped the header on the wire. The run suspends on the server's verdict; the
            // timeout is a backstop past the server's own [TIMEOUT_MS] deadline for a wedged accept thread.
            runWebView<Result>(timeout = TIMEOUT_MS.milliseconds + 2.seconds) {
                loadUrl("http://127.0.0.1:$port$MAIN_PATH")
                resolve(verdict.await())
            }
        } catch (c: CancellationException) {
            throw c
        } catch (t: Throwable) {
            Result.INCONCLUSIVE
        } finally {
            runCatching { server.close() }
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
