package eu.kanade.tachiyomi.extension.pt.mangalivre

import keiyoushi.utils.parseAs
import kotlinx.serialization.Serializable
import okhttp3.CacheControl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.io.IOException

class ReadingGateInterceptor(
    private val baseUrl: String,
    private val seedClient: OkHttpClient,
    private val decryptor: MangaLivreDecryptor,
) : Interceptor {

    private val baseUrlHost = baseUrl.toHttpUrl().host

    private var cachedSeed: CachedSeed? = null

    private val cachedRouteTokens = mutableMapOf<ChapterReference, CachedRouteToken>()

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != baseUrlHost) {
            return chain.proceed(request)
        }
        return proceedDecrypted(chain, request, seedRetried = false, routeRetried = false)
    }

    private fun proceedDecrypted(
        chain: Interceptor.Chain,
        request: Request,
        seedRetried: Boolean,
        routeRetried: Boolean,
    ): Response {
        val signedRequest = request.withSignature(forceRefresh = seedRetried)
        val response = chain.proceed(
            signedRequest.withRouteToken(forceRefresh = routeRetried || seedRetried),
        )

        if (response.code == 403 && !seedRetried) {
            response.close()
            return proceedDecrypted(chain, request, seedRetried = true, routeRetried)
        }

        if (response.code == 404 && request.chapterReference() != null && !routeRetried) {
            response.close()
            return proceedDecrypted(chain, request, seedRetried, routeRetried = true)
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

    private fun Request.withSignature(forceRefresh: Boolean): Request = newBuilder()
        .header(SIGNATURE_HEADER, resolveSeed(this, forceRefresh))
        .build()

    private fun Request.withRouteToken(forceRefresh: Boolean): Request {
        val reference = chapterReference() ?: return this
        return newBuilder()
            .header(ROUTE_TOKEN_HEADER, resolveRouteToken(this, reference, forceRefresh))
            .build()
    }

    private fun Request.chapterReference(): ChapterReference? {
        val segments = url.pathSegments
        if (segments.size != 5 || segments[0] != "api" || segments[1] != "mangas" || segments[3] != "chapters") {
            return null
        }
        return ChapterReference(segments[2], segments[4])
    }

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

    private fun resolveRouteToken(
        request: Request,
        reference: ChapterReference,
        forceRefresh: Boolean,
    ): String = synchronized(this) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            cachedRouteTokens[reference]
                ?.takeIf { it.expiresAt > now }
                ?.let { return@synchronized it.token }
        }

        val routeTokenRequest = request.newBuilder()
            .url("$baseUrl/api/chapter-token/${reference.mangaId}/${reference.chapterId}")
            .get()
            .removeHeader(ROUTE_TOKEN_HEADER)
            .cacheControl(CacheControl.FORCE_NETWORK)
            .build()
        val routeToken = seedClient.newCall(routeTokenRequest).execute().use { response ->
            if (!response.isSuccessful) throw IOException(INVALID_ROUTE_TOKEN_MESSAGE)
            response.parseAs<RouteTokenDto>()
        }
        val cacheDuration = routeToken.expiresMs
            ?.coerceIn(MIN_ROUTE_TOKEN_CACHE_MS, MAX_ROUTE_TOKEN_CACHE_MS)
            ?: DEFAULT_ROUTE_TOKEN_CACHE_MS
        cachedRouteTokens[reference] = CachedRouteToken(
            routeToken.token,
            now + cacheDuration - ROUTE_TOKEN_EXPIRY_MARGIN_MS,
        )
        routeToken.token
    }

    data class ReaderPath(val path: String)

    private class CachedSeed(val token: String, val expiresAt: Long)

    private data class ChapterReference(val mangaId: String, val chapterId: String)

    private class CachedRouteToken(val token: String, val expiresAt: Long)

    companion object {
        private const val SIGNATURE_HEADER = "x-toon-signature"
        private const val ROUTE_TOKEN_HEADER = "x-toon-route-token"
        private const val SEED_CACHE_MS = 25 * 60 * 1000L
        private const val DEFAULT_ROUTE_TOKEN_CACHE_MS = 5 * 60 * 1000L
        private const val MIN_ROUTE_TOKEN_CACHE_MS = 60 * 1000L
        private const val MAX_ROUTE_TOKEN_CACHE_MS = 10 * 60 * 1000L
        private const val ROUTE_TOKEN_EXPIRY_MARGIN_MS = 30 * 1000L
        private const val NON_JSON_MESSAGE = "Não foi possível decifrar a resposta."
        private const val INVALID_ROUTE_TOKEN_MESSAGE = "O site retornou um token de capítulo inválido."
    }
}

@Serializable
private class SeedDto(val token: String)

@Serializable
private class RouteTokenDto(val token: String, val expiresMs: Long? = null)
