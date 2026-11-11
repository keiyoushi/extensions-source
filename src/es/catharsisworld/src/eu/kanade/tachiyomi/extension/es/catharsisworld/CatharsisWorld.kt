package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import eu.kanade.tachiyomi.multisrc.catharsis.Catharsis
import eu.kanade.tachiyomi.multisrc.catharsis.MangaDto
import eu.kanade.tachiyomi.multisrc.catharsis.ReaderPassDto
import eu.kanade.tachiyomi.multisrc.catharsis.descramble
import eu.kanade.tachiyomi.multisrc.catharsis.isImageHeader
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.utils.getLocalStorage
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.xor
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.HttpUrl
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

    override fun OkHttpClient.Builder.addReaderInterceptors(): OkHttpClient.Builder = addInterceptor(::pageInterceptor)
        .rateLimit(24, 1.minutes, 2.seconds) { it.pathSegments.last() == "page-ticket" }

    override fun getChapterUrl(chapter: SChapter): String {
        val (mangaId, number) = chapter.url.split("/", limit = 2)
        return "$baseUrl/manga/$mangaId/chapter/$number"
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val dto = sessionMutex.withLock { openChapter(chapter.url) }
        return List(dto.paginas.size) { i ->
            val url = HttpUrl.Builder()
                .scheme("http")
                .host(PAGE_HOST)
                .addPathSegments(chapter.url)
                .addQueryParameter("page", i.toString())
                .build()
            Page(i, imageUrl = url.toString())
        }
    }

    override fun getImageRequest(page: Page): Request = super.getImageRequest(page).newBuilder()
        .addHeader("X-Catharsis-Reader", "1")
        .build()

    // Page image: a ticket per load, then the signed image
    private fun pageInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.url.host != PAGE_HOST) return chain.proceed(request)
        val chapterUrl = request.url.encodedPath.removePrefix("/")
        val pageIndex = request.url.queryParameter("page")!!.toInt()
        val fetch = readerFetch(chapterUrl)
        val ticket = try {
            val session = runBlocking { chapterSession(chapterUrl) }
            mintPageTicket(chain, session, pageIndex, fetch)
                ?: mintPageTicket(chain, runBlocking { chapterSession(chapterUrl, rejected = session) }, pageIndex, fetch)
                ?: throw HttpException(401)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message, e)
        }
        val imageRequest = request.newBuilder()
            .url("$apiUrl/mangas/pages/${ticket.signedToken}")
            .fetch(fetch)
            .build()
        val response = chain.proceed(imageRequest)
        if (!response.isSuccessful) return response

        val body = response.body
        val source = body.source()
        if (!source.request(HEADER_SIZE.toLong())) return response
        val header = source.buffer.snapshot(HEADER_SIZE).toByteArray()
        val decoded = header.xor(ticket.xorByte)
        // XOR-ed with the ticket's byte unless it already starts with an image signature
        if (isImageHeader(header) || !isImageHeader(decoded)) return response

        val plain = response.xor(ticket.xorByte)
        if (ticket.scrambleKey == null) return plain
        val image = Buffer()
        plain.body.source().use(image::writeAll)
        val bitmap = BitmapFactory.decodeStream(image.peek().inputStream())
        if (bitmap == null || bitmap.width < 200 || bitmap.height < 200) {
            bitmap?.recycle()
            return plain.newBuilder()
                .body(image.asResponseBody(body.contentType(), image.size))
                .build()
        }
        val descrambled = descramble(bitmap, ticket.scrambleKey)
        bitmap.recycle()
        val out = Buffer()
        descrambled.compress(Bitmap.CompressFormat.PNG, 100, out.outputStream())
        descrambled.recycle()
        return response.newBuilder()
            .body(out.asResponseBody("image/png".toMediaType(), out.size))
            .build()
    }

    private class ChapterSession(val chapterId: Int, val token: String, val expiresAt: Instant)

    private class FailedOpen(val chapterUrl: String, val failedAt: Instant, val error: Exception)

    private val sessionMutex = Mutex()
    private val chapterSessions = HashMap<String, ChapterSession>()
    private var failedOpen: FailedOpen? = null

    // Session for pageInterceptor; a failed open replays for 10 s
    private suspend fun chapterSession(chapterUrl: String, rejected: ChapterSession? = null): ChapterSession = sessionMutex.withLock {
        val now = Clock.System.now()
        chapterSessions[chapterUrl]
            ?.takeIf { it !== rejected && it.expiresAt > now + 1.minutes }
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
        chapterSessions.getValue(chapterUrl)
    }

    // Caller holds sessionMutex
    private suspend fun openChapter(chapterUrl: String): PagesDto {
        refreshLogin()
        val url = "$apiUrl/mangas/$chapterUrl"
        val fetch = readerFetch(chapterUrl)
        val dto = fetchChapter(url, readerPass(readerHeaders, fetch), fetch)
            ?: fetchChapter(url, readerPass(readerHeaders, fetch, renew = true), fetch)
            ?: throw HttpException(403)
        val token = dto.chapterSessionToken
        val payload = Base64.decode(token.substringBefore("."), Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            .decodeToString()
            .parseAs<ChapterTokenPayload>()
        val now = Clock.System.now()
        chapterSessions.values.removeAll { it.expiresAt < now }
        chapterSessions[chapterUrl] = ChapterSession(payload.cid, token, Instant.fromEpochSeconds(payload.exp))
        failedOpen = null
        return dto
    }

    // Null when the site asks for a new reader pass
    private suspend fun fetchChapter(url: String, pass: ReaderPassDto, fetch: Fetch): PagesDto? {
        val passHeaders = readerHeaders.newBuilder().set("X-Catharsis-Reader-Pass", pass.pass).build()
        val response = client.get(url, passHeaders, fetch, ensureSuccess = false)
        if (response.isSuccessful) return response.parseAs<PagesDto>()
        if (response.code == 401) {
            response.close()
            throw Exception("This chapter requires login on the website")
        }
        val code = response.apiError()?.code
        if (code == "READER_PASS_REQUIRED") return null
        throw response.siteError(code)
    }

    // Null when the site rejects the session token: expired, or issued to another IP
    private fun mintPageTicket(chain: Interceptor.Chain, session: ChapterSession, pageIndex: Int, fetch: Fetch): PageTicketResponse? {
        val request = POST(
            "$apiUrl/mangas/chapters/${session.chapterId}/page-ticket",
            readerHeaders,
            PageTicketRequest(pageIndex, session.token).toJsonRequestBody(),
        ).newBuilder().fetch(fetch).build()
        var response = chain.proceed(request)
        // Waits out READING_TOO_FAST like the site's reader
        var waited = 0L
        while (response.code == 429) {
            val error = response.apiError()
            val retryAfter = error?.retryAfterMs?.takeIf { it > 0 && error.code == "READING_TOO_FAST" } ?: throw HttpException(429)
            val delay = retryAfter + 150
            if (waited + delay > TICKET_WAIT.inWholeMilliseconds) throw HttpException(429)
            Thread.sleep(delay)
            waited += delay
            response = chain.proceed(request)
        }
        if (response.code == 401) {
            response.close()
            return null
        }
        if (!response.isSuccessful) {
            response.close()
            throw HttpException(response.code)
        }
        return response.parseAs<PageTicketResponse>()
    }

    private class Login(val token: String, val expiresAt: Instant)

    @Volatile private var login: Login? = null

    // The site keeps its session in localStorage; caller holds sessionMutex
    private suspend fun refreshLogin() {
        val now = Clock.System.now()
        if (login?.let { it.expiresAt > now + 1.minutes } == true) return
        // Guests read on when there's no WebView to ask
        login = runCatching {
            val token = getLocalStorage(baseUrl, "access_token")?.takeIf { it.isNotEmpty() }
            val expiresAt = token?.let { getLocalStorage(baseUrl, "expires_at") }?.toLongOrNull()
            if (token == null || expiresAt == null) null else Login(token, Instant.fromEpochMilliseconds(expiresAt))
        }.getOrNull()?.takeIf { it.expiresAt > now + 1.minutes }
    }

    // The reader's calls with the login from the WebView, like the site's client; page images go without
    private val readerHeaders get() = login?.let { apiHeaders.newBuilder().set("Authorization", "Bearer ${it.token}").build() } ?: apiHeaders

    private fun readerFetch(chapterUrl: String) = Fetch.XHR.from(getChapterUrl(SChapter.create().apply { url = chapterUrl }))

    companion object {
        private val TICKET_WAIT = 2.minutes
        private const val HEADER_SIZE = 12
        private const val PAGE_HOST = "catharsisworld.invalid"
    }
}
