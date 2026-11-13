package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
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
import keiyoushi.utils.intOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.stringOrNull
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.xor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Cookie
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

    // Page budget the site allows a guest reader
    override fun OkHttpClient.Builder.addReaderInterceptors(): OkHttpClient.Builder = addInterceptor(::pageInterceptor)
        .rateLimit(2, 5.seconds) { it.encodedPath.startsWith("/lector/p/") }

    override fun getChapterUrl(chapter: SChapter) = chapterPageUrl(chapter.url).toString()

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

    // The pass cookie from an earlier session
    override fun storedReaderPass(): ReaderPassDto? = client.cookieJar.loadForRequest(baseUrl.toHttpUrl())
        .firstOrNull { it.name == PASS_COOKIE }
        ?.let { ReaderPassDto(it.value, it.expiresAt) }

    // The pass is the cookie this call sets
    override suspend fun redeemReaderPass(capToken: String, sitekey: String, fetch: Fetch): ReaderPassDto {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("lector/pase")
            .build()
        val response = client.post(url, fetch, ReaderPassRequest(capToken, sitekey).toJsonRequestBody(), ensureSuccess = false)
            .ensureSiteSuccess()
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
        return try {
            val session = runBlocking { chapterSession(chapterUrl) }
            if (session.passRefused) throw Exception(PASS_REFUSED)
            fetchPage(chain, request, session, pageIndex)
                ?: runBlocking { chapterSession(chapterUrl, rejected = session) }.let { reopened ->
                    fetchPage(chain, request, reopened, pageIndex) ?: run {
                        if (!reopened.passSpent) throw HttpException(401)
                        reopened.passRefused = true
                        throw Exception(PASS_REFUSED)
                    }
                }
        } catch (e: Exception) {
            throw e as? IOException ?: IOException(e.message, e)
        }
    }

    private fun Response.descrambled(secret: ReaderSecret, scrambleKey: String, gutter: Int): Response {
        val image = Buffer()
        body.source().use(image::writeAll)
        val bitmap = BitmapFactory.decodeStream(image.peek().inputStream())
        // The site's reader draws pages under 200 px as they come
        if (bitmap == null || bitmap.width < 200 || bitmap.height < 200) {
            bitmap?.recycle()
            return newBuilder()
                .body(image.asResponseBody(body.contentType(), image.size))
                .build()
        }
        val descrambled = descramble(bitmap, gutter) { width, height -> secret.tilePlan(scrambleKey, width, height) }
        bitmap.recycle()
        val out = Buffer()
        descrambled.compress(Bitmap.CompressFormat.JPEG, 90, out.outputStream())
        descrambled.recycle()
        return newBuilder()
            .body(out.asResponseBody("image/jpeg".toMediaType(), out.size))
            .build()
    }

    // The page as the site's reader draws it; null when the site wants a new reader pass
    private fun fetchPage(chain: Interceptor.Chain, request: Request, session: ChapterSession, pageIndex: Int): Response? {
        val token = session.pageTokens[pageIndex]
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("lector/p")
            .addPathSegment(token)
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
                401, 403, 409 -> {
                    val code = response.apiError()?.code
                    when {
                        // A stale live token is minted again once, like the site's reader
                        code == "LIVE_REQUIRED" && !liveRetried -> {
                            liveRetried = true
                            session.liveToken = null
                        }
                        // The site's other 403s refuse the client or its IP
                        response.code == 403 -> throw response.siteError(code)
                        else -> return null
                    }
                }
                // Waits out READING_TOO_FAST like the site's reader
                429 -> {
                    val error = response.apiError()
                    if (error?.code != "READING_TOO_FAST") throw HttpException(429)
                    // Without a wait to honour, the site has spent the reader pass
                    val delay = error.retryAfterMs?.takeIf { it > 0 }?.plus(150) ?: run {
                        session.passSpent = true
                        return null
                    }
                    if (waited + delay > PACE_WAIT.inWholeMilliseconds) throw HttpException(429)
                    Thread.sleep(delay)
                    waited += delay
                }
                else -> {
                    if (!response.isSuccessful) return response
                    // Like the site's reader, a missing or invalid X-Page-Xor keeps the chapter's last one
                    val xorKey = response.header("X-Page-Xor")?.toIntOrNull()?.takeIf { it in 1..255 } ?: session.xorKey
                    session.xorKey = xorKey
                    val plain = response.xor(xorKey)
                    val scrambleKey = response.header("X-Page-Key") ?: return plain
                    val gutter = response.header("X-Tile-Gutter")?.toIntOrNull()?.takeIf { it in 1..63 } ?: 0
                    return plain.descrambled(session.secret, scrambleKey, gutter)
                }
            }
        }
    }

    // Minted again every 20 s like the site's heartbeat; null when the site wants a new reader pass
    private fun liveToken(chain: Interceptor.Chain, request: Request, session: ChapterSession): String? = synchronized(session) {
        val now = Clock.System.now()
        session.liveToken?.takeIf { now - it.mintedAt < LIVE_REFRESH }?.let { return it.value }
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("lector")
            .addPathSegment(session.secret.heartbeatPath)
            .build()
        val heartbeat = request.newBuilder()
            .url(url)
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

        @Volatile var xorKey = DEFAULT_XOR_KEY

        @Volatile var passSpent = false

        // A renewed pass was spent too; the chapter's other pages fail without asking again
        @Volatile var passRefused = false
    }

    private class FailedOpen(val chapterUrl: String, val failedAt: Instant, val error: Exception)

    private val sessionMutex = Mutex()
    private val chapterSessions = LruCache<String, ChapterSession>(10)
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
            openChapter(chapterUrl, renewPass = rejected?.passSpent == true)
        } catch (e: Exception) {
            failedOpen = FailedOpen(chapterUrl, now, e)
            throw e
        }
    }

    // Caller holds sessionMutex
    private suspend fun openChapter(chapterUrl: String, renewPass: Boolean = false): ChapterSession {
        val fetch = Fetch.XHR.from(chapterPageUrl(chapterUrl))
        val secret = readerSecret(chapterUrl)
        readerPass(fetch, renew = renewPass)
        val blob = loadChapter(chapterUrl, secret, fetch)
            ?: run {
                readerPass(fetch, renew = true)
                loadChapter(chapterUrl, secret, fetch)
            }
            ?: throw Exception("Reader verification failed")
        val data = secret.decryptLoader(blob).parseAs<ReaderDataDto>()
        val session = ChapterSession(secret, data.pageTokens, data.heartbeat, fetch)
        chapterSessions.put(chapterUrl, session)
        failedOpen = null
        return session
    }

    // The loader's encrypted reader data; null while it asks for a reader pass
    private suspend fun loadChapter(chapterUrl: String, secret: ReaderSecret, fetch: Fetch): String? {
        val response = client.get(chapterPageUrl(chapterUrl, ".data"), fetch, ensureSuccess = false).ensureSiteSuccess()
        val stream = response.parseAs<JsonArray>()
        val state = stream.turboStreamValue("state")?.stringOrNull
        when (state) {
            "captcha" -> return null
            "error" -> {
                val status = stream.turboStreamValue("status")?.intOrNull
                if (status == 401) throw Exception("This chapter requires login on the website")
                throw response.siteError(stream.turboStreamValue("code")?.stringOrNull, status ?: response.code)
            }
        }
        val blob = stream.turboStreamValue(secret.loaderField)?.stringOrNull
        if (blob == null && state == "ok") throw ReaderChangedException()
        return blob ?: throw Exception("Chapter unavailable: $state")
    }

    private var readerSecret: Pair<String, ReaderSecret>? = null

    // The secret of the reader build the chapter page loads, kept per bundle; caller holds sessionMutex
    private suspend fun readerSecret(chapterUrl: String): ReaderSecret {
        val chapterPage = chapterPageUrl(chapterUrl)
        val bundleUrl = client.get(chapterPage, Fetch.DOCUMENT).asJsoup()
            .selectFirst("link[rel=modulepreload][href*=/chapter-viewer-]")
            ?.absUrl("href")
            ?: throw ReaderChangedException()
        readerSecret?.takeIf { it.first == bundleUrl }?.let { return it.second }
        val bundle = client.get(bundleUrl, Fetch.SCRIPT.from(chapterPage)).body.string()
        return ReaderSecret.fromBundle(bundle).also { readerSecret = bundleUrl to it }
    }

    // "<manga id>/<number>" as the site's chapter page
    private fun chapterPageUrl(chapterUrl: String, suffix: String = ""): HttpUrl {
        val (mangaId, number) = chapterUrl.split("/", limit = 2)
        return baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("manga")
            .addPathSegment(mangaId)
            .addPathSegment("chapter")
            .addPathSegment(number + suffix)
            .build()
    }

    // React Router turbo-stream: a flat array whose objects map "_<index of a key>" to the index of its value;
    // negative indexes stand for null and other constants
    private fun JsonArray.turboStreamValue(key: String): JsonElement? {
        val keyIndex = indexOfFirst { it is JsonPrimitive && it.isString && it.content == key }
        if (keyIndex < 0) return null
        val valueIndex = firstNotNullOfOrNull { (it as? JsonObject)?.getIntOrNull("_$keyIndex") } ?: return null
        return getOrNull(valueIndex)
    }

    companion object {
        private val PACE_WAIT = 2.minutes
        private val LIVE_REFRESH = 20.seconds
        private const val DEFAULT_XOR_KEY = 67
        private const val PASS_COOKIE = "cw_rp"
        private const val PASS_REFUSED = "The site refuses the reader pass"
        private const val PAGE_HOST = "catharsisworld.invalid"
    }
}
