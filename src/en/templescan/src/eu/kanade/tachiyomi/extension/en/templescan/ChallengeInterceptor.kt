package eu.kanade.tachiyomi.extension.en.templescan

import keiyoushi.utils.runWebViewBlocking
import okhttp3.Call
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import kotlin.time.Duration.Companion.minutes

/** Passes the site's Turnstile challenge, a redirect to `/challenge`, in a WebView and retries. */
class ChallengeInterceptor : Interceptor {

    private var solvedAt = Long.MIN_VALUE

    private var failedAt: Long? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val sentAt = System.nanoTime()
        val response = chain.proceed(request)
        if (!response.isChallenge()) return response

        response.close()
        synchronized(this) {
            // Skip if another call passed the challenge after this request was sent.
            if (solvedAt < sentAt) {
                // A challenge that just failed to solve would fail again; leave it to the user.
                failedAt?.let { if (System.nanoTime() - it < BACKOFF.inWholeNanoseconds) throw IOException(SOLVE_MANUALLY) }
                try {
                    solve(chain.call(), response.request.url, request.header("User-Agent")!!)
                } catch (e: IOException) {
                    failedAt = System.nanoTime()
                    throw e
                }
                solvedAt = System.nanoTime()
                failedAt = null
            }
        }

        val retried = chain.proceed(request)
        if (retried.isChallenge()) {
            retried.close()
            throw IOException(SOLVE_MANUALLY)
        }
        return retried
    }

    private fun solve(call: Call, challengeUrl: HttpUrl, userAgent: String) {
        try {
            runWebViewBlocking<Unit>(call) {
                this.userAgent = userAgent
                onPageStarted { url ->
                    val started = url.toHttpUrlOrNull() ?: return@onPageStarted
                    if (started.host == challengeUrl.host && !started.isChallenge()) resolve(Unit)
                }
                loadUrl(challengeUrl.toString())
            }
        } catch (e: Throwable) {
            throw IOException(SOLVE_MANUALLY, e)
        }
    }

    private fun Response.isChallenge() = request.url.isChallenge()

    private fun HttpUrl.isChallenge() = encodedPath == "/challenge"

    companion object {
        private const val SOLVE_MANUALLY = "Open in WebView to pass the site's verification"

        private val BACKOFF = 5.minutes
    }
}
