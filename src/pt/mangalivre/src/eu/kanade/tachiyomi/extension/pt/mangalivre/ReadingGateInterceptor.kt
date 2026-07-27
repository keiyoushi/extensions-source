package eu.kanade.tachiyomi.extension.pt.mangalivre

import android.util.Base64
import eu.kanade.tachiyomi.network.GET
import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import java.io.IOException

/**
 * Chapter contents are only served to visitors that carry a proof of work cookie and a
 * short lived token tied to the chapter being requested.
 *
 * The site rotates the values feeding the proof of work, so whenever it stops accepting ours
 * they are read again from the site script instead of waiting for a new release.
 */
class ReadingGateInterceptor(
    private val baseUrl: String,
    private val client: OkHttpClient,
) : Interceptor {

    private val homeUrl = baseUrl.toHttpUrl()

    private var parameters = PowParameters.DEFAULT
    private var cookieExpiresAt = 0L
    private var cachedToken: CachedToken? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (request.url.host != homeUrl.host) {
            return chain.proceed(request)
        }

        val chapterKey = request.url.chapterKey()
            ?: return chain.proceed(request)

        val token = routeToken(chapterKey, request.headers)
            ?: throw IOException(BLOCKED_MESSAGE)

        return chain.proceed(request.newBuilder().header(ROUTE_TOKEN, token).build())
    }

    /** Matches `/api/mangas/{mangaId}/chapters/{chapterId}`, the only gated endpoint. */
    private fun HttpUrl.chapterKey(): String? {
        val segments = pathSegments

        if (segments.size != CHAPTER_SEGMENTS ||
            segments[0] != "api" ||
            segments[1] != "mangas" ||
            segments[3] != "chapters"
        ) {
            return null
        }

        return "${segments[2]}/${segments[4]}"
    }

    /** Retries once with freshly read parameters, since a refusal usually means they rotated. */
    private fun routeToken(key: String, headers: Headers): String? {
        cachedToken?.takeIf { it.key == key && it.isValid }?.let { return it.value }

        return requestToken(key, headers) ?: run {
            reloadParameters(headers)
            requestToken(key, headers)
        }
    }

    @Synchronized
    private fun requestToken(key: String, headers: Headers): String? {
        saveVisitorCookie()

        val dto = client.newCall(GET("$baseUrl/api/chapter-token/$key", headers)).execute()
            .use { if (it.isSuccessful) it.parseAs<TokenDto>() else null }
            ?: return null

        cachedToken = CachedToken(
            key = key,
            value = dto.token,
            expiresAt = System.currentTimeMillis() + dto.expiresMs - EXPIRY_MARGIN,
        )

        return dto.token
    }

    @Synchronized
    private fun reloadParameters(headers: Headers) {
        val home = client.newCall(GET(baseUrl, headers)).execute().use { it.body.string() }
        val scriptPath = SCRIPT_REGEX.find(home)?.value ?: return
        val script = client.newCall(GET("$baseUrl$scriptPath", headers)).execute()
            .use { it.body.string() }

        parameters = PowParameters.parse(script)
        cookieExpiresAt = 0L
    }

    /**
     * Saved through the cookie jar so that cookies set by the site, such as the ones from
     * the DDoS protection, are preserved.
     */
    private fun saveVisitorCookie() {
        val now = System.currentTimeMillis()

        if (now < cookieExpiresAt) {
            return
        }

        val payload = """{"ts":$now,"pow":"${ProofOfWork(parameters).solve(now)}"}"""
        val cookie = Cookie.Builder()
            .name(parameters.cookieName)
            .value(Base64.encodeToString(payload.toByteArray(), Base64.NO_WRAP))
            .domain(homeUrl.host)
            .path("/")
            .build()

        client.cookieJar.saveFromResponse(homeUrl, listOf(cookie))
        cookieExpiresAt = now + COOKIE_LIFETIME
    }

    private class CachedToken(val key: String, val value: String, private val expiresAt: Long) {
        val isValid get() = System.currentTimeMillis() < expiresAt
    }

    @Serializable
    private class TokenDto(
        val token: String,
        val expiresMs: Long = 0,
    )

    companion object {
        private const val ROUTE_TOKEN = "x-toon-route-token"
        private const val CHAPTER_SEGMENTS = 5
        private const val COOKIE_LIFETIME = 5 * 60 * 1000L
        private const val EXPIRY_MARGIN = 30 * 1000L
        private const val BLOCKED_MESSAGE =
            "O site recusou a liberação do capítulo. Abra a fonte na WebView e tente de novo."
        private val SCRIPT_REGEX = Regex("""/assets/index-[\w.\-]+\.js""")
    }
}
