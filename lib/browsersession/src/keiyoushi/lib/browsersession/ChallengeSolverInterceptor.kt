package keiyoushi.lib.browsersession

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/**
 * OkHttp [Interceptor] that automatically detects browser protection challenges (Cloudflare Turnstile,
 * Cloudflare Managed Challenges, DDoS-Guard, etc.), executes [BrowserSessionRunner] to resolve them,
 * synchronizes the resulting clearance cookies via [CookieBridge], and transparently retries the request.
 *
 * @param runner The [BrowserSessionRunner] instance to use for challenge resolution.
 * @param maxRetries Maximum number of challenge solve attempts per request (default 1).
 */
class ChallengeSolverInterceptor(
    private val runner: BrowserSessionRunner = BrowserSessionRunner.DEFAULT,
    private val maxRetries: Int = 1,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        var response = chain.proceed(originalRequest)
        var attempts = 0

        while (isChallengeResponse(response) && attempts < maxRetries) {
            // Avoid retrying requests with one-shot bodies that cannot be replayed
            if (originalRequest.body?.isOneShot() == true) {
                return response
            }

            response.close()
            attempts++

            val url = originalRequest.url
            val userAgent = originalRequest.header("User-Agent")

            when (val result = runner.solve(url, chain.call(), userAgent)) {
                is SolveResult.Failed -> {
                    val error = result.error
                    if (error is IOException) throw error
                    throw IOException("Failed to solve browser challenge for ${url.host}", error)
                }
                SolveResult.Success,
                SolveResult.AlreadySolved,
                -> Unit
            }

            val newRequestBuilder = originalRequest.newBuilder()
            mergeCookies(newRequestBuilder, url, originalRequest.header("Cookie"))

            response = chain.proceed(newRequestBuilder.build())
        }

        return response
    }

    companion object {
        private val CHALLENGE_STATUS_CODES = setOf(403, 503)

        /**
         * Inspects [response] status code, headers, and body signature tokens to determine if it represents
         * an active browser challenge rather than a standard HTTP error.
         *
         * Body inspection uses [Response.peekBody] up to 4096 bytes so the original response stream remains intact.
         */
        fun isChallengeResponse(response: Response): Boolean {
            if (response.code !in CHALLENGE_STATUS_CODES) return false

            if (response.header("cf-mitigated").equals("challenge", ignoreCase = true)) {
                return true
            }

            val server = response.header("Server").orEmpty().lowercase()
            val isCloudflare = server.contains("cloudflare")
            val isDdosGuard = server.contains("ddos-guard")

            val peek = runCatching { response.peekBody(4096).string() }.getOrNull().orEmpty()

            val hasCloudflareSignature = peek.contains("challenge-platform") ||
                peek.contains("turnstile") ||
                peek.contains("__cf_chl_") ||
                peek.contains("_cf_chl_opt") ||
                peek.contains("cf-challenge-running") ||
                peek.contains("challenge-platform/h/b/orchestrate") ||
                (peek.contains("Just a moment...") && (isCloudflare || peek.contains("cloudflare"))) ||
                (peek.contains("ray-id") && (isCloudflare || peek.contains("cloudflare")))

            if (hasCloudflareSignature) return true

            val hasDdosGuardSignature = isDdosGuard ||
                peek.contains("check.ddos-guard.net") ||
                peek.contains("__ddg_") ||
                (peek.contains("ddos-guard", ignoreCase = true) && peek.contains("check"))

            if (hasDdosGuardSignature) return true

            return false
        }

        internal fun mergeCookies(builder: okhttp3.Request.Builder, url: okhttp3.HttpUrl, existingHeader: String?) {
            val managerCookies = CookieBridge.getCookies(url)
            val managerRawHeader = CookieBridge.getCookieHeader(url)

            if (existingHeader.isNullOrBlank()) {
                if (!managerRawHeader.isNullOrBlank()) {
                    builder.header("Cookie", managerRawHeader)
                }
                return
            }

            if (managerCookies.isEmpty()) {
                return
            }

            val existingList = existingHeader.split(';')
                .map { it.trim() }
                .filter { it.isNotEmpty() }

            val merged = buildList {
                for (existing in existingList) {
                    val name = existing.substringBefore('=').trim()
                    if (managerCookies.none { it.name.equals(name, ignoreCase = true) }) {
                        add(existing)
                    }
                }
                for (cookie in managerCookies) {
                    add("${cookie.name}=${cookie.value}")
                }
            }.joinToString("; ")

            builder.header("Cookie", merged)
        }
    }
}
