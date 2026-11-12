package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import eu.kanade.tachiyomi.multisrc.catharsis.Catharsis
import eu.kanade.tachiyomi.multisrc.catharsis.MangaDto
import eu.kanade.tachiyomi.multisrc.catharsis.ReaderPassDto
import eu.kanade.tachiyomi.multisrc.catharsis.ReaderPassRequest
import eu.kanade.tachiyomi.multisrc.catharsis.descramble
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getIntOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.xor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Cookie
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import java.io.IOException
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class CatharsisWorld : Catharsis() {

    override val apiUrl get() = "$baseUrl/api"

    override val apiHeaders get() = headersBuilder()
        .set("Accept", "application/json, text/plain, */*")
        .set("System", "catharsis")
        .set("X-FK-Sistema", "3")
        .build()

    override val mangaPath = "manga"

    override fun mangaKey(manga: MangaDto) = manga.id.toString()

    // The site's reader loads two pages at a time
    override fun OkHttpClient.Builder.addReaderInterceptors(): OkHttpClient.Builder = addInterceptor(::pageInterceptor)
        .rateLimit(2, 1.seconds) { it.pathSegments.size == 3 && it.pathSegments[0] == "lector" && it.pathSegments[1] == "p" }

    override fun getChapterUrl(chapter: SChapter): String {
        val (mangaId, number) = chapter.url.split("/", limit = 2)
        return "$baseUrl/manga/$mangaId/chapter/$number"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val session = sessionMutex.withLock { openChapter(chapter.url) }
        return List(session.pageTokens.size) { i ->
            val url = HttpUrl.Builder()
                .scheme("http")
                .host(PAGE_HOST)
                .addPathSegments(chapter.url)
                .addQueryParameter("page", i.toString())
                .build()
            Page(i, imageUrl = url.toString())
        }
    }

    // The pass is the cookie this call sets
    override suspend fun redeemReaderPass(capToken: String, sitekey: String, headers: Headers, fetch: Fetch): ReaderPassDto {
        val response = client.post(
            "$baseUrl/lector/pase",
            fetch,
            ReaderPassRequest(capToken, sitekey).toJsonRequestBody(),
            ensureSuccess = false,
        ).ensureSiteSuccess()
        val cookie = Cookie.parseAll(response.request.url, response.headers).first { it.name == PASS_COOKIE }
        response.close()
        return ReaderPassDto(cookie.value, cookie.expiresAt)
    }

    // Page image: the chapter's page token, signed, with the heartbeat's live token
    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != PAGE_HOST) return chain.proceed(request)
        val chapterUrl = request.url.encodedPath.removePrefix("/")
        val pageIndex = request.url.queryParameter("page")!!.toInt()
        val response = try {
            val session = runBlocking { chapterSession(chapterUrl) }
            fetchPage(chain, request, session, pageIndex)
                ?: fetchPage(chain, request, runBlocking { chapterSession(chapterUrl, rejected = session) }, pageIndex)
                ?: throw HttpException(401)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message, e)
        }
        if (!response.isSuccessful) return response

        val plain = response.xor(response.header("X-Page-Xor")!!.toInt())
        val scrambleKey = response.header("X-Page-Key") ?: return plain
        val image = Buffer()
        plain.body.source().use(image::writeAll)
        val bitmap = BitmapFactory.decodeStream(image.peek().inputStream())
        // The site's reader draws pages under 200 px as they come
        if (bitmap == null || bitmap.width < 200 || bitmap.height < 200) {
            bitmap?.recycle()
            return plain.newBuilder()
                .body(image.asResponseBody(plain.body.contentType(), image.size))
                .build()
        }
        val descrambled = descramble(bitmap, scrambleKey, response.header("X-Tile-Gutter")?.toInt() ?: 0)
        bitmap.recycle()
        val out = Buffer()
        descrambled.compress(Bitmap.CompressFormat.JPEG, 90, out.outputStream())
        descrambled.recycle()
        return response.newBuilder()
            .body(out.asResponseBody("image/jpeg".toMediaType(), out.size))
            .build()
    }

    // Null when the site wants a new reader pass
    private fun fetchPage(chain: Interceptor.Chain, request: Request, session: ChapterSession, pageIndex: Int): Response? {
        val token = session.pageTokens[pageIndex]
        val url = "$baseUrl/lector/p/$token".toHttpUrl().newBuilder()
            .addQueryParameter("q", "hq")
            .build()
        var liveRetried = false
        var waited = 0L
        while (true) {
            val liveToken = liveToken(chain, request, session) ?: return null
            val pageRequest = request.newBuilder()
                .url(url)
                .fetch(session.fetch)
                .header(session.secret.clientHeader, session.secret.clientValue)
                .header(session.secret.signatureHeader, session.secret.signature("p", token))
                .header("X-Catharsis-Reader", "1")
                .header(session.secret.liveHeader, liveToken)
                .build()
            val response = chain.proceed(pageRequest)
            when (response.code) {
                401, 409 -> {
                    // A stale live token is minted again once, like the site's reader
                    if (response.apiError()?.code != "LIVE_REQUIRED" || liveRetried) return null
                    liveRetried = true
                    session.liveToken = null
                }
                // Waits out READING_TOO_FAST like the site's reader
                429 -> {
                    val error = response.apiError()
                    val delay = error?.retryAfterMs?.takeIf { it > 0 && error.code == "READING_TOO_FAST" } ?: throw HttpException(429)
                    if (waited + delay > PACE_WAIT.inWholeMilliseconds) throw HttpException(429)
                    Thread.sleep(delay)
                    waited += delay
                }
                else -> return response
            }
        }
    }

    // Minted again every 20 s like the site's heartbeat; null when the site wants a new reader pass
    private fun liveToken(chain: Interceptor.Chain, request: Request, session: ChapterSession): String? = synchronized(session) {
        val now = Clock.System.now()
        session.liveToken?.takeIf { now - it.mintedAt < LIVE_REFRESH }?.let { return it.value }
        val heartbeat = request.newBuilder()
            .url("$baseUrl/lector/${session.secret.heartbeatPath}")
            .post(HeartbeatRequest(session.heartbeat).toJsonRequestBody())
            .fetch(session.fetch)
            .header(session.secret.clientHeader, session.secret.clientValue)
            .header(session.secret.signatureHeader, session.secret.signature("v", session.heartbeat))
            .build()
        val response = chain.proceed(heartbeat)
        if (response.code == 401) {
            response.close()
            return null
        }
        val liveToken = response.ensureSiteSuccess().parseAs<HeartbeatDto>().liveToken
        session.liveToken = LiveToken(liveToken, now)
        liveToken
    }

    private class LiveToken(val value: String, val mintedAt: Instant)

    private class ChapterSession(
        val secret: ReaderSecret,
        val pageTokens: List<String>,
        val heartbeat: String,
        val fetch: Fetch,
    ) {
        @Volatile var liveToken: LiveToken? = null
    }

    private class FailedOpen(val chapterUrl: String, val failedAt: Instant, val error: Exception)

    private val sessionMutex = Mutex()
    private val chapterSessions = LinkedHashMap<String, ChapterSession>()
    private var failedOpen: FailedOpen? = null

    // Session for pageInterceptor; a failed open replays for 10 s
    private suspend fun chapterSession(chapterUrl: String, rejected: ChapterSession? = null): ChapterSession = sessionMutex.withLock {
        val now = Clock.System.now()
        chapterSessions[chapterUrl]
            ?.takeIf { it !== rejected }
            ?.let { return@withLock it }
        failedOpen
            ?.takeIf { it.chapterUrl == chapterUrl && now - it.failedAt < 10.seconds }
            ?.let { throw it.error }
        try {
            openChapter(chapterUrl)
        } catch (e: Exception) {
            failedOpen = FailedOpen(chapterUrl, now, e)
            throw e
        }
    }

    // Caller holds sessionMutex
    private suspend fun openChapter(chapterUrl: String): ChapterSession {
        val chapterPage = getChapterUrl(SChapter.create().apply { url = chapterUrl })
        val fetch = Fetch.XHR.from(chapterPage)
        val secret = readerSecret(chapterPage)
        readerPass(apiHeaders, fetch)
        val blob = loadChapter(chapterPage, secret, fetch)
            ?: run {
                readerPass(apiHeaders, fetch, renew = true)
                loadChapter(chapterPage, secret, fetch)
            }
            ?: throw Exception("Reader verification failed")
        val data = secret.decryptLoader(blob).parseAs<ReaderDataDto>()
        val session = ChapterSession(secret, data.pageTokens, data.heartbeat, fetch)
        chapterSessions.remove(chapterUrl)
        if (chapterSessions.size >= MAX_SESSIONS) chapterSessions.remove(chapterSessions.keys.first())
        chapterSessions[chapterUrl] = session
        failedOpen = null
        return session
    }

    // The loader's encrypted reader data; null while it asks for a reader pass
    private suspend fun loadChapter(chapterPage: String, secret: ReaderSecret, fetch: Fetch): String? {
        val stream = client.get("$chapterPage.data", fetch, ensureSuccess = false).ensureSiteSuccess().parseAs<JsonArray>()
        val state = stream.turboStreamString("state")
        if (state == "captcha") return null
        return stream.turboStreamString(secret.loaderField)
            ?: throw Exception("Chapter unavailable: $state")
    }

    private var readerSecret: Pair<String, ReaderSecret>? = null

    // The secret of the reader build the chapter page loads, kept per bundle; caller holds sessionMutex
    private suspend fun readerSecret(chapterPage: String): ReaderSecret {
        val bundleUrl = client.get(chapterPage, Fetch.DOCUMENT).asJsoup()
            .selectFirst("link[rel=modulepreload][href*=/chapter-viewer-]")!!
            .absUrl("href")
        readerSecret?.takeIf { it.first == bundleUrl }?.let { return it.second }
        val bundle = client.get(bundleUrl, Fetch.SCRIPT.from(chapterPage)).body.string()
        return ReaderSecret(ReaderSecret.extract(bundle)).also { readerSecret = bundleUrl to it }
    }

    // React Router turbo-stream: a flat array whose objects map "_<index of a key>" to the index of its value
    private fun JsonArray.turboStreamString(key: String): String? {
        val keyIndex = indexOfFirst { it is JsonPrimitive && it.isString && it.content == key }
        if (keyIndex < 0) return null
        val valueIndex = firstNotNullOfOrNull { (it as? JsonObject)?.getIntOrNull("_$keyIndex") } ?: return null
        return this[valueIndex].stringOrNull
    }

    companion object {
        private val PACE_WAIT = 2.minutes
        private val LIVE_REFRESH = 20.seconds
        private const val MAX_SESSIONS = 10
        private const val PASS_COOKIE = "cw_rp"
        private const val PAGE_HOST = "catharsisworld.invalid"
    }
}
