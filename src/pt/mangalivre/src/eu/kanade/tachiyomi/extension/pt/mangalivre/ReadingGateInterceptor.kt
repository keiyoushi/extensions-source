package eu.kanade.tachiyomi.extension.pt.mangalivre

import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.CacheControl
import okhttp3.Cookie
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException
import kotlin.random.Random

class ReadingGateInterceptor(
    private val baseUrl: String,
    private val seedClient: OkHttpClient,
    private val decryptor: MangaLivreDecryptor,
) : Interceptor {

    private val baseUrlHost = baseUrl.toHttpUrl().host

    private var cachedSeed: CachedSeed? = null

    private var cachedRouteToken: CachedToken? = null

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != baseUrlHost) {
            return chain.proceed(request)
        }
        return proceedDecrypted(chain, request, seedRetried = false, tokenRetried = false)
    }

    private fun proceedDecrypted(
        chain: Interceptor.Chain,
        request: Request,
        seedRetried: Boolean,
        tokenRetried: Boolean,
    ): Response {
        val chapter = request.url.chapterReference()
        val response = chain.proceed(
            request
                .withSignature(forceRefresh = seedRetried)
                .withRouteToken(chapter, forceRefresh = tokenRetried),
        )

        if (response.code == 403 && !seedRetried) {
            response.close()
            return proceedDecrypted(chain, request, seedRetried = true, tokenRetried)
        }

        // A chapter is only served to a request carrying a route token, which expires on its own.
        if (response.code == 404 && chapter != null && !tokenRetried) {
            response.close()
            return proceedDecrypted(chain, request, seedRetried, tokenRetried = true)
        }

        val dataKey = response.headers["x-toon-datakey"] ?: return response

        val contentType = response.body.contentType()
        val cipherWrapperBody = response.body.string()
        val readerPath = request.tag(ReaderPath::class.java)?.path ?: "/"
        val decrypted = decryptor.decrypt(cipherWrapperBody, dataKey)
            ?: decryptor.reloadConstantsAndDecrypt(readerPath, cipherWrapperBody, dataKey)
            ?: throw IOException(NON_JSON_MESSAGE)

        return response.newBuilder()
            .body(decrypted.toResponseBody(contentType))
            .build()
    }

    private fun HttpUrl.chapterReference(): ChapterReference? {
        val segments = pathSegments
        if (segments.size < 5 || segments[0] != "api" || segments[1] != "mangas" || segments[3] != "chapters") {
            return null
        }
        return ChapterReference(segments[2], segments[4])
    }

    private fun Request.withRouteToken(chapter: ChapterReference?, forceRefresh: Boolean): Request {
        val token = chapter?.let { resolveRouteToken(this, it, forceRefresh) } ?: return this
        return newBuilder()
            .header(ROUTE_TOKEN_HEADER, token)
            .build()
    }

    private fun resolveRouteToken(request: Request, chapter: ChapterReference, forceRefresh: Boolean): String? = synchronized(this) {
        val key = "${chapter.mangaId}/${chapter.chapterId}"
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedRouteToken?.takeIf { it.key == key && it.expiresAt > now }?.let { return@synchronized it.token }
        }

        ensureVisitorCookie()

        val tokenRequest = request.newBuilder()
            .url("$baseUrl/api/chapter-token/${chapter.mangaId}/${chapter.chapterId}")
            .get()
            .header(SIGNATURE_HEADER, resolveSeed(request, forceRefresh = false))
            .removeHeader(ROUTE_TOKEN_HEADER)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()

        val dto = runCatching {
            seedClient.newCall(tokenRequest).execute().parseAs<ChapterTokenDto>()
        }.getOrNull() ?: return@synchronized null

        cachedRouteToken = CachedToken(key, dto.token, now + (dto.expiresMs - TOKEN_EXPIRY_MARGIN_MS).coerceAtLeast(0))
        dto.token
    }

    private fun Request.withSignature(forceRefresh: Boolean): Request = newBuilder()
        .header(SIGNATURE_HEADER, resolveSeed(this, forceRefresh))
        .build()

    private fun resolveSeed(request: Request, forceRefresh: Boolean): String = synchronized(this) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedSeed?.takeIf { it.expiresAt > now }?.let { return@synchronized it.token }
        }

        val seedRequest = request.newBuilder()
            .url("$baseUrl/api/seed")
            .get()
            .removeHeader(SIGNATURE_HEADER)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        val token = seedClient.newCall(seedRequest).execute().parseAs<SeedDto>().token
        cachedSeed = CachedSeed(token, now + SEED_CACHE_MS)
        token
    }

    /**
     * The token endpoint only answers a visitor, which the site marks with a cookie generated on
     * the client, so there is nothing to carry over from a previous response. It goes through the
     * cookie jar because a `Cookie` header set here would be replaced by the one the jar builds.
     */
    private fun ensureVisitorCookie() {
        val url = baseUrl.toHttpUrl()
        val jar = seedClient.cookieJar
        if (jar.loadForRequest(url).any { it.name == VISITOR_COOKIE }) return

        val cookie = Cookie.Builder()
            .name(VISITOR_COOKIE)
            .value(List(VISITOR_ID_LENGTH) { VISITOR_ID_ALPHABET.random(Random) }.joinToString(""))
            .domain(url.host)
            .path("/")
            .build()
        jar.saveFromResponse(url, listOf(cookie))
    }

    data class ReaderPath(val path: String)

    private class ChapterReference(val mangaId: String, val chapterId: String)

    private class CachedSeed(val token: String, val expiresAt: Long)

    private class CachedToken(val key: String, val token: String, val expiresAt: Long)

    companion object {
        private const val SIGNATURE_HEADER = "x-toon-signature"
        private const val ROUTE_TOKEN_HEADER = "x-toon-route-token"
        private const val VISITOR_COOKIE = "toon_v"
        private const val VISITOR_ID_LENGTH = 26
        private const val VISITOR_ID_ALPHABET = "abcdefghijklmnopqrstuvwxyz0123456789"
        private const val SEED_CACHE_MS = 25 * 60 * 1000L
        private const val TOKEN_EXPIRY_MARGIN_MS = 30 * 1000L
        private const val NON_JSON_MESSAGE = "Não foi possível decifrar a resposta."
    }
}

@Serializable
private class SeedDto(val token: String)

@Serializable
private class ChapterTokenDto(val token: String, val expiresMs: Long = 0L)
