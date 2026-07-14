package eu.kanade.tachiyomi.extension.pt.mangalivre

import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import java.io.IOException

/**
 * Adds the reading-gate header to same-host requests and, when the server rejects the cached one,
 * re-derives it via a hidden WebView ([TokenResolver]), validating each captured candidate against
 * the real endpoint before caching it since the site rotates the header and serves decoys.
 * Single-flighted, with a cooldown so a persistent failure can't spawn a WebView per request.
 */
class ReadingGateInterceptor(
    private val baseUrl: String,
    private val userAgent: String?,
) : Interceptor {

    private val baseUrlHost = baseUrl.toHttpUrl().host

    @Volatile
    private var cachedToken = DEFAULT_TOKEN

    @Volatile
    private var lastFailedAttemptAt = 0L

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != baseUrlHost) {
            return chain.proceed(request)
        }

        val token = cachedToken
        val response = request.withToken(token).proceedOrNull(chain)
        if (response != null && !response.needsTokenRefresh()) {
            return response
        }
        response?.close()

        return refreshAndRetry(chain, request, staleToken = token)
    }

    private fun refreshAndRetry(chain: Interceptor.Chain, request: Request, staleToken: TokenResolver.ClientToken): Response = synchronized(this) {
        if (cachedToken != staleToken) {
            return@synchronized chain.proceed(request.withToken(cachedToken))
        }

        // Only chapter-page requests can capture the real header; the homepage serves the decoy.
        val readerPath = request.tag(ReaderPath::class.java)
            ?: return@synchronized chain.proceed(request.withToken(staleToken))

        if (System.currentTimeMillis() - lastFailedAttemptAt < REFRESH_COOLDOWN_MS) {
            return@synchronized chain.proceed(request.withToken(staleToken))
        }

        val candidates = try {
            TokenResolver.resolve("$baseUrl${readerPath.path}", userAgent)
        } catch (_: Exception) {
            emptyList()
        }

        for (candidate in candidates) {
            if (candidate == staleToken) continue
            val validation = request.withToken(candidate).proceedOrNull(chain)
            if (validation != null && validation.unlockedGate()) {
                cachedToken = candidate
                return@synchronized validation
            }
            validation?.close()
        }

        lastFailedAttemptAt = System.currentTimeMillis()
        chain.proceed(request.withToken(staleToken))
    }

    private fun Request.withToken(token: TokenResolver.ClientToken): Request = newBuilder().header(token.header, token.value).build()

    private fun Request.proceedOrNull(chain: Interceptor.Chain): Response? = try {
        chain.proceed(this)
    } catch (_: IOException) {
        null
    }

    private fun Response.needsTokenRefresh(): Boolean {
        if (request.url.host != baseUrlHost) return true
        if (code != 403) return false
        return try {
            peekBody(MAX_PEEK).string().contains(GATE_ERROR_MARKER, ignoreCase = true)
        } catch (_: Exception) {
            false
        }
    }

    // Trust a candidate only on a real page-list: a 2xx JSON body on our own host.
    private fun Response.unlockedGate(): Boolean {
        if (!isSuccessful || needsTokenRefresh()) return false
        return try {
            peekBody(MAX_PEEK).string().trimStart().startsWith("{")
        } catch (_: Exception) {
            false
        }
    }

    data class ReaderPath(val path: String)

    companion object {
        private const val MAX_PEEK = 1024L
        private const val GATE_ERROR_MARKER = "aplicativo oficial"
        private const val REFRESH_COOLDOWN_MS = 60_000L

        private val DEFAULT_TOKEN = TokenResolver.ClientToken("toonlivre-pass", "auth2028xy")
    }
}
