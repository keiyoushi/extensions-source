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

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != baseUrlHost) {
            return chain.proceed(request)
        }
        return proceedDecrypted(chain, request, seedRetried = false)
    }

    private fun proceedDecrypted(
        chain: Interceptor.Chain,
        request: Request,
        seedRetried: Boolean,
    ): Response {
        val response = chain.proceed(request.withSignature(forceRefresh = seedRetried))

        if (response.code == 403 && !seedRetried) {
            response.close()
            return proceedDecrypted(chain, request, seedRetried = true)
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

    data class ReaderPath(val path: String)

    private class CachedSeed(val token: String, val expiresAt: Long)

    companion object {
        private const val SIGNATURE_HEADER = "x-toon-signature"
        private const val SEED_CACHE_MS = 25 * 60 * 1000L
        private const val NON_JSON_MESSAGE = "Não foi possível decifrar a resposta."
    }
}

@Serializable
private class SeedDto(val token: String)
