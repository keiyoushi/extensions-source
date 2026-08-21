package eu.kanade.tachiyomi.extension.all.manhuarm.interceptors

import eu.kanade.tachiyomi.network.GET
import okhttp3.Headers
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicLong

/**
 * Interceptor that warms up the client by making a request to the base URL
 * when encountering Cloudflare-like challenge responses (403/503). This helps
 * solve Cloudflare challenges before retrying the original request.
 *
 * Also detects Cloudflare challenge pages returned with 200 status by checking
 * for challenge markers in the HTML body.
 */
class CloudflareWarmupInterceptor(
    private val baseUrl: String,
    private val headers: Headers,
) : Interceptor {

    private val lastWarmupAttempt = AtomicLong(0)

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        if (response.code == 404) {
            return response
        }

        if (response.isSuccessful) {
            val challenge = isChallengePage(response)
            response.close()
            if (challenge && tryWarmup(chain)) {
                return chain.proceed(request)
            }
            return chain.proceed(request)
        }

        if (response.code != 403 && response.code != 503) {
            return response
        }

        response.close()

        if (tryWarmup(chain)) {
            return chain.proceed(request)
        }

        return chain.proceed(request)
    }

    private fun tryWarmup(chain: Interceptor.Chain): Boolean {
        val now = System.currentTimeMillis()
        val lastAttempt = lastWarmupAttempt.get()
        if (now - lastAttempt < WARMUP_COOLDOWN_MS) {
            return false
        }
        lastWarmupAttempt.set(now)

        return try {
            val warmupResponse = chain.proceed(GET(baseUrl, headers))
            val success = warmupResponse.isSuccessful
            warmupResponse.close()
            success
        } catch (_: IOException) {
            false
        }
    }

    private fun isChallengePage(response: Response): Boolean = try {
        val peek = response.peekBody(MAX_CHALLENGE_BODY_BYTES).string()
        CHALLENGE_MARKERS.any { peek.contains(it, ignoreCase = true) }
    } catch (_: Exception) {
        false
    }

    companion object {
        private const val WARMUP_COOLDOWN_MS = 5 * 60 * 1000L
        private const val MAX_CHALLENGE_BODY_BYTES = 8192L
        private val CHALLENGE_MARKERS = listOf(
            "cf-browser-verification",
            "challenge-platform",
            "Just a moment",
            "Attention Required",
            "cf-turnstile",
            "Ray ID",
        )
    }
}
