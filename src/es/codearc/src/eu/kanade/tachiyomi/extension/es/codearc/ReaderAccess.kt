package eu.kanade.tachiyomi.extension.es.codearc

import android.util.Base64
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import kotlin.time.Clock
import kotlin.time.Instant

// region Reader pages and access

internal suspend fun fetchReaderPages(
    client: OkHttpClient,
    headers: Headers,
    chapterUrl: String,
    pagesUrl: String,
    offset: Int? = null,
    challengeToken: suspend (String) -> String = { fetchTurnstileToken(chapterUrl, it) },
): List<PageDto> {
    val endpoint = pagesUrl.toHttpUrl()
    val readerCookie = client.cookieJar.loadForRequest(endpoint)
        .firstOrNull { it.name == "codearc_reader" }
    val needsReaderAccess = readerCookie == null ||
        readerCookie.value.let(::tokenExpiry)?.let { it <= Clock.System.now() } == true
    if (needsReaderAccess) {
        requestReaderAccess(client, headers, chapterUrl, challengeToken)
    }
    val batchSize = 100
    val firstOffset = offset ?: 0
    val url = endpoint.newBuilder().setQueryParameter("offset", firstOffset.toString()).build()
    var response = client.get(url, headers, ensureSuccess = false)
    if (response.code == 403 && !needsReaderAccess) {
        val error = response.parseAs<ReaderPagesErrorDto>().error
        check(error == "READER_ACCESS_REQUIRED") { "Reader pages HTTP 403: $error" }
        requestReaderAccess(client, headers, chapterUrl, challengeToken)
        response = client.get(url, headers, ensureSuccess = false)
    }
    val firstBatch = response.use {
        check(it.isSuccessful) { "Reader pages HTTP ${it.code}" }
        it.parseAs<ReaderPagesDto>()
    }
    if (offset != null) return firstBatch.items
    if (firstBatch.items.size < batchSize || firstBatch.items.size >= firstBatch.total) return firstBatch.items

    return coroutineScope {
        val remainingOffsets = batchSize until firstBatch.total step batchSize
        val pendingBatches = remainingOffsets.map { batchOffset ->
            async {
                val batchUrl = endpoint.newBuilder().setQueryParameter("offset", batchOffset.toString()).build()
                client.get(batchUrl, headers).parseAs<ReaderPagesDto>()
            }
        }
        val pages = firstBatch.items.toMutableList()
        for (batch in pendingBatches.awaitAll()) {
            pages.addAll(batch.items)
        }
        pages
    }
}

private suspend fun requestReaderAccess(
    client: OkHttpClient,
    headers: Headers,
    chapterUrl: String,
    challengeToken: suspend (String) -> String,
) {
    val accessUrl = chapterUrl.toHttpUrl().newBuilder().encodedPath("/api/mangas/reader-access").query(null).build()
    val accessHeaders = headers.newBuilder().add("X-Codearc-Reader", "1").build()
    val accessResponse = client.post(accessUrl, accessHeaders, ReaderAccessRequest().toJsonRequestBody(), ensureSuccess = false)
    val access = accessResponse.parseAs<ReaderAccessDto>()
    if (access.ok) {
        return
    }
    val sitekey = access.sitekey?.takeIf { access.challenge } ?: error("Reader access was not granted")
    val token = try {
        challengeToken(sitekey)
    } catch (error: TurnstileInteractiveException) {
        throw IllegalStateException("Turnstile interactivo no compatible", error)
    } catch (error: TurnstileException) {
        val message = when (error.message) {
            "expired" -> "Verificación de Turnstile caducada"
            "unsupported" -> "Turnstile no compatible con WebView"
            "timeout" -> "Turnstile tardó demasiado"
            "error" -> "No se pudo cargar Turnstile"
            else -> "No se pudo verificar con Turnstile: ${error.message}"
        }
        throw IllegalStateException(message, error)
    }
    client.post(accessUrl, accessHeaders, ReaderAccessRequest(token).toJsonRequestBody()).use { response ->
        check(response.parseAs<ReaderAccessDto>().ok) { "Reader verification failed" }
    }
}

internal fun readerPagesUrl(chapterUrl: HttpUrl): String = chapterUrl.newBuilder()
    .encodedPath("/api/mangas/reader-pages")
    .query(null)
    .addQueryParameter("slug", chapterUrl.pathSegments[1])
    .addQueryParameter("capitulo", chapterUrl.pathSegments[2])
    .addQueryParameter("offset", "0")
    .addQueryParameter("limit", "100")
    .build()
    .toString()

// endregion

// region Image refresh

internal class ReaderPageRefresh(
    private val getClient: () -> OkHttpClient,
    private val getHeaders: () -> Headers,
    private val getBaseUrl: () -> String,
) : Interceptor {
    private val client get() = getClient()
    private val headers get() = getHeaders()
    private val baseUrl get() = getBaseUrl()

    private val refreshLock = Mutex()
    private val refreshedPages = LinkedHashMap<String, Map<Int, String>>(4, 0.75f, true)

    override fun intercept(chain: Interceptor.Chain): Response = runBlocking {
        refreshExpiredPage(chain)
    }

    private suspend fun refreshExpiredPage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        val slug = url.queryParameter("reader_slug") ?: return chain.proceed(request)
        val chapter = url.queryParameter("reader_chapter") ?: return chain.proceed(request)
        val page = url.queryParameter("reader_page")?.toIntOrNull() ?: return chain.proceed(request)
        val chapterKey = "$baseUrl/$slug/$chapter"
        val accessToken = url.queryParameter("access")
        val cachedImageUrl = refreshLock.withLock {
            refreshedPages[chapterKey]?.get(page)?.toHttpUrl()?.takeIf { cached ->
                cached.accessExpiry()?.let { it > Clock.System.now() } ?: true
            }
        }
        val imageRequest = if (cachedImageUrl != null && cachedImageUrl.queryParameter("access") != accessToken) {
            request.newBuilder().url(cachedImageUrl).build()
        } else {
            request
        }
        if (imageRequest.url.accessExpiry()?.let { it <= Clock.System.now() } != true) {
            val response = chain.proceed(imageRequest)
            if (response.code != 403) {
                return response
            }
            response.close()
        }

        val imageUrl = refreshLock.withLock {
            var pages = refreshedPages[chapterKey]
            val cachedUrl = pages?.get(page)?.toHttpUrl()
            if (cachedUrl == null || cachedUrl.accessExpiry()?.let { it <= Clock.System.now() } == true ||
                cachedUrl.queryParameter("access") == imageRequest.url.queryParameter("access")
            ) {
                val chapterUrl = baseUrl.toHttpUrl().newBuilder()
                    .addPathSegment("reader")
                    .addPathSegment(slug)
                    .addPathSegment(chapter)
                    .addPathSegment("cascade")
                    .build()
                val batchOffset = (page - 1) / 100 * 100
                val fetchedPages = fetchReaderPages(client, headers, chapterUrl.toString(), readerPagesUrl(chapterUrl), offset = batchOffset) { sitekey ->
                    fetchTurnstileToken(chapterUrl.toString(), sitekey, chain.call())
                }.associate { it.orden to it.imagenUrl }.also {
                    check(page in it) { "Reader page $chapterKey/$page missing from offset $batchOffset" }
                }
                pages = pages.orEmpty() + fetchedPages
                refreshedPages[chapterKey] = pages
                if (refreshedPages.size > 4) refreshedPages.remove(refreshedPages.keys.first())
            }
            pages.getValue(page)
        }

        return chain.proceed(request.newBuilder().url(imageUrl).build())
    }
}

// endregion

// region Token decoding and response models

private fun HttpUrl.accessExpiry(): Instant? = queryParameter("access")?.let(::tokenExpiry)

private fun tokenExpiry(token: String): Instant? = runCatching {
    val payload = token.substringBefore('.').takeIf { it != token } ?: return null
    val decoded = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_WRAP))
    val expirySeconds = decoded.parseAs<AccessPayload>().exp
    Instant.fromEpochSeconds(expirySeconds)
}.getOrNull()

@Serializable
private class ReaderAccessRequest(val token: String? = null)

@Serializable
private class ReaderAccessDto(
    val ok: Boolean,
    val challenge: Boolean = false,
    val sitekey: String? = null,
)

@Serializable
private class ReaderPagesErrorDto(val error: String)

@Serializable
private class AccessPayload(val exp: Long)

// endregion
