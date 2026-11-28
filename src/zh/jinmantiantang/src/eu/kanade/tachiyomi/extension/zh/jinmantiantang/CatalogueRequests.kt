package eu.kanade.tachiyomi.extension.zh.jinmantiantang

import eu.kanade.tachiyomi.network.HttpException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import okhttp3.HttpUrl
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.atomic.AtomicReference

internal class CatalogueReply<T>(
    val response: Response,
    val decode: (Response) -> T,
)

internal class CatalogueAlternate(
    val url: HttpUrl,
    val recordSuccess: () -> Unit,
)

internal class ApiServer(
    val host: String,
    val candidates: List<String>,
    val discoveredAt: Long = System.currentTimeMillis(),
) {
    fun cacheValue(allowed: List<String>): String? = if (host in allowed) "$discoveredAt:$host" else null
}

// Only reuse hosts shipped with the extension. Newly advertised hosts remain session-local.
internal fun restoreCatalogueServer(
    value: String?,
    allowed: List<String>,
    now: Long,
): ApiServer? {
    val time = value?.substringBefore(':')?.toLongOrNull() ?: return null
    if (time <= 0 || time > now || now - time >= 15 * 60 * 1000L) return null
    val host = value.substringAfter(':')
    return if (host in allowed) ApiServer(host, allowed, time) else null
}

internal fun AtomicReference<ApiServer?>.catalogueAlternate(
    failedUrl: HttpUrl,
    initial: ApiServer?,
): CatalogueAlternate? {
    val current = get() ?: return null
    val alternate =
        (sequenceOf(current.host) + current.candidates.asSequence())
            .mapNotNull { host ->
                try {
                    failedUrl.newBuilder().host(host).build()
                } catch (_: IllegalArgumentException) {
                    null
                }
            }.firstOrNull { it.host != failedUrl.host } ?: return null
    return CatalogueAlternate(alternate) {
        if (current === initial && current.host.equals(failedUrl.host, ignoreCase = true)) {
            compareAndSet(current, ApiServer(alternate.host, current.candidates, current.discoveredAt))
        }
    }
}

internal suspend fun <T> requestCatalogue(
    firstUrl: HttpUrl,
    findAlternate: (HttpUrl) -> CatalogueAlternate?,
    request: suspend (HttpUrl) -> CatalogueReply<T>,
): T {
    val first = request(firstUrl)
    first.response.use { response ->
        if (!response.isNodeUnavailable(firstUrl)) return first.decodeOrThrow(response)
    }
    currentCoroutineContext().ensureActive()
    val alternate = findAlternate(firstUrl)?.takeIf { it.url.host != firstUrl.host } ?: throw HttpException(first.response.code)
    val second = request(alternate.url)
    return second.response.use(second::decodeOrThrow).also { alternate.recordSuccess() }
}

private fun <T> CatalogueReply<T>.decodeOrThrow(response: Response): T {
    if (!response.isSuccessful) throw HttpException(response.code)
    return decode(response)
}

private fun Response.isNodeUnavailable(firstUrl: HttpUrl): Boolean {
    if ((code != 503 && code != 500) || request.method != "GET" || request.url.host != firstUrl.host || priorResponse != null) return false
    if (DENIAL_HEADERS.any { header(it) != null }) return false

    // A bare 503 can also be an HTML error page, so check its content before changing nodes.
    return try {
        val body = peekBody(PROBE_BYTES + 1)
        body.contentLength() <= PROBE_BYTES && !DENIAL_MARKERS.containsMatchIn(body.string())
    } catch (_: IOException) {
        false
    }
}

private const val PROBE_BYTES = 64L * 1024
private val DENIAL_HEADERS = listOf("Retry-After", "Cf-Mitigated", "WWW-Authenticate", "Proxy-Authenticate")
private val DENIAL_MARKERS =
    Regex(
        "challenge-error-|challenge-platform|cf-chl-|cf-turnstile|captcha|just a moment|attention required|" +
            "verify you are human|checking your browser|browser verification|enable javascript and cookies|" +
            "access denied|permission denied|authentication required|forbidden|request (blocked|rejected)|" +
            "too many requests|rate limit|请(?:先)?登录|请完成验证|访问被拒绝|请求过于频繁",
        RegexOption.IGNORE_CASE,
    )
