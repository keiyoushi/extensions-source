package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import android.webkit.WebResourceResponse
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

/**
 * The site's firewall only lets decode.php through for a real tap on the read button, and decode.php hands the viewer
 * an opaque token, so the viewer is left to run and its request for the volume file is caught.
 */
internal class VolumeDecoder(private val baseUrl: String, private val userAgent: String) {

    private val baseHost = baseUrl.toHttpUrl().host

    @OptIn(NativeWebViewApi::class)
    suspend fun decode(titleUrl: String, label: String): HttpUrl = runWebView(timeout = 90.seconds) {
        userAgent = this@VolumeDecoder.userAgent
        blockImages = true
        // The firewall turns away requests that name the app, which WebView does unless told otherwise
        setOriginMatchedHeader("X-Requested-With", "", setOf(baseUrl, "https://*.$baseHost"))
        // Only the site, its challenge and the viewer's reader library load: ads hijack the page
        interceptRequest { request ->
            val url = request.url.toString().toHttpUrlOrNull() ?: return@interceptRequest emptyResponse()
            when {
                url.host == baseHost || url.host == "challenges.cloudflare.com" || url.host in VIEWER_LIBRARY_HOSTS -> null
                url.host.endsWith(".$baseHost") && url.pathSegments.last().substringAfterLast('.', "") !in ASSET_EXTENSIONS -> {
                    resolve(url)
                    emptyResponse()
                }
                else -> emptyResponse()
            }
        }

        val bridge = List(12) { ('a'..'z').random() }.joinToString("")
        webMessageBridge(bridge, setOf(baseUrl)) { message ->
            if (message == MISSING) {
                reject(VolumeMissing())
            } else {
                val (x, y) = message.split(',').map(String::toFloat)
                tap(x, y)
            }
        }
        injectAtDocumentStart(pressReadScript(bridge, label), setOf(baseUrl))

        var challengedFor = 0
        poll(1.seconds) {
            evaluateJs(STATE_JS) { json ->
                when (json.parseAs<String?>()) {
                    // Cloudflare clears a passable challenge in seconds, a stuck one would only run out the timeout
                    CHALLENGE -> if (++challengedFor >= CHALLENGE_PATIENCE) {
                        reject(IOException("Cloudflare is checking the site, open the title in WebView and try again"))
                    }
                    RATE_LIMITED -> reject(IOException("Site is rate limiting volume requests, try again in a few minutes"))
                    BLOCKED -> reject(IOException("Blocked by Cloudflare, open the title in WebView and try again"))
                    MAINTENANCE -> reject(IOException("Site is under maintenance, try again later"))
                }
            }
        }
        loadUrl(titleUrl)
    }

    private fun pressReadScript(bridge: String, label: String) = """
        (() => {
            if (window.top !== window || !location.pathname.startsWith('/manga/')) return;
            document.addEventListener('DOMContentLoaded', () => {
                // The first read tap opens an ad unless the site's timer, keyed by a hash of the UA and language, says one just did
                const raw = navigator.userAgent + navigator.language;
                let hash = 0;
                for (let i = 0; i < raw.length; i++) { hash = ((hash << 5) - hash) + raw.charCodeAt(i); hash |= 0; }
                localStorage.setItem('vi4' + Math.abs(hash), Date.now());
                // Wait for the site's own handler to restore the last read volume first, then pick ours
                setTimeout(() => {
                    const select = document.querySelector('select.vi13');
                    const button = document.querySelector('input.vi12');
                    const option = select && [...select.options].find(o => o.text === ${label.toJsonString()});
                    if (!option || !button) return window.$bridge.postMessage('$MISSING');
                    select.value = option.value;
                    button.scrollIntoView({ block: 'center' });
                    setTimeout(() => {
                        const r = button.getBoundingClientRect();
                        window.$bridge.postMessage((r.left + r.width / 2) + ',' + (r.top + r.height / 2));
                    }, 300);
                }, 500);
            });
        })();
    """.trimIndent()

    private fun emptyResponse() = WebResourceResponse(null, null, null)

    private companion object {
        const val CHALLENGE_PATIENCE = 30
        const val MISSING = "__missing__"
        const val CHALLENGE = "__challenge__"
        const val RATE_LIMITED = "__rate_limited__"
        const val BLOCKED = "__blocked__"
        const val MAINTENANCE = "__maintenance__"

        // Where the tap has taken the page. It runs in the WebView, where the app's own Cloudflare handling can't help
        const val STATE_JS = """(() => {
            if (location.pathname === '/maintenance.html') return '$MAINTENANCE';
            if (/Just a moment/.test(document.title)) return '$CHALLENGE';
            if (location.pathname.startsWith('/manga/') || document.readyState !== 'complete') return null;
            const nav = performance.getEntriesByType('navigation')[0];
            if (nav && nav.responseStatus === 429) return '$RATE_LIMITED';
            if (/Attention Required/.test(document.title)) return '$BLOCKED';
            return null;
        })()"""

        val ASSET_EXTENSIONS = setOf("js", "css", "png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "woff", "woff2")

        // Script hosts the viewer loads its PDF reader from
        val VIEWER_LIBRARY_HOSTS = setOf("cdnjs.cloudflare.com", "cdn.jsdelivr.net", "ajax.googleapis.com", "unpkg.com")
    }
}
