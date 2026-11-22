package eu.kanade.tachiyomi.extension.pt.xxxyaoi

import android.webkit.CookieManager
import keiyoushi.utils.TURNSTILE_AREA
import keiyoushi.utils.runWebViewBlocking
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

/**
 * Passes Cloudflare's challenge in a WebView with an empty X-Requested-With: the site refuses the app's package name there.
 */
class ChallengeInterceptor : Interceptor {

    private val lock = Any()

    @Volatile
    private var passes = 0

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val passesBefore = passes
        val response = chain.proceed(request)
        if (!response.isChallenge()) return response
        response.close()
        synchronized(lock) {
            // Not already passed for another request meanwhile
            if (passes == passesBefore) {
                pass(chain, request)
                passes++
            }
        }
        return chain.proceed(request)
    }

    private fun pass(chain: Interceptor.Chain, request: Request) {
        val origin = "${request.url.scheme}://${request.url.host}"
        try {
            val previous = clearance(origin)
            runWebViewBlocking<Unit>(chain.call(), TIMEOUT) {
                userAgent = request.header("User-Agent")!!
                setOriginMatchedHeader("X-Requested-With", "", setOf(origin))
                val start = TimeSource.Monotonic.markNow()
                poll(POLL_INTERVAL) {
                    when {
                        clearance(origin).let { it != null && it != previous } -> resolve(Unit)
                        // The check usually passes by itself within a few seconds, otherwise it wants a tap
                        start.elapsedNow() > SHOW_AFTER -> showToUser(TURNSTILE_AREA)
                    }
                }
                loadUrl(request.url.toString())
            }
        } catch (e: UnsupportedOperationException) {
            throw IOException("Update Android System WebView to pass Cloudflare's check", e)
        } catch (e: Throwable) {
            throw IOException("Open in WebView to pass Cloudflare's check", e)
        }
    }

    private fun clearance(origin: String): String? = CookieManager.getInstance().getCookie(origin)
        ?.split("; ")
        ?.firstOrNull { it.startsWith("cf_clearance=") }

    private fun Response.isChallenge() = code == 403 && header("cf-mitigated") == "challenge"

    companion object {
        private val TIMEOUT = 60.seconds
        private val POLL_INTERVAL = 250.milliseconds
        private val SHOW_AFTER = 10.seconds
    }
}
