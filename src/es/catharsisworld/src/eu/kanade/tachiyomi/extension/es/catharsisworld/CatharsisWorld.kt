package eu.kanade.tachiyomi.extension.es.catharsisworld

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.util.Base64
import eu.kanade.tachiyomi.network.HttpException
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.hmacSha256
import keiyoushi.utils.parseAs
import keiyoushi.utils.sha256
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import java.io.IOException
import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

@Source
abstract class CatharsisWorld : KeiSource() {

    private val apiUrl get() = "$baseUrl/api"

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor(::pageInterceptor)
        .addInterceptor(::signatureInterceptor)
        .rateLimit(50, 1.minutes, 1.seconds) { it.encodedPath.endsWith("/page-ticket") }
        .rateLimit(3, 1.seconds) { it.host == baseUrl.toHttpUrl().host }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("System", "catharsis")
        .add("X-FK-Sistema", "3")

    override suspend fun getPopularManga(page: Int) = searchMangas(page, "", "-n_visitas", null, emptyList())

    override suspend fun getLatestUpdates(page: Int) = searchMangas(page, "", "-fecha_ultimo_capitulo", null, emptyList())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty().filter { it.state }.map { it.id }
        return searchMangas(
            page = page,
            query = query,
            sort = filters.firstInstance<SortFilter>().value,
            status = filters.firstInstance<StatusFilter>().value,
            genres = genres,
        )
    }

    private suspend fun searchMangas(page: Int, query: String, sort: String, status: String?, genres: List<String>): MangasPage {
        val url = "$apiUrl/mangas".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            addQueryParameter("limit", PAGE_SIZE.toString())
            if (query.isNotBlank()) addQueryParameter("name", query.trim())
            if (genres.isNotEmpty()) addQueryParameter("genre", genres.joinToString("|"))
            status?.let { addQueryParameter("status", it) }
            addQueryParameter("sort", sort)
        }.build()
        val result = client.get(url).parseAs<MangaListDto>()
        return MangasPage(result.data.map { it.toSManga(baseUrl) }, page < result.totalPages)
    }

    override val supportsFilterFetching = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$apiUrl/mangas/genres").parseAs<List<GenreDto>>().toJsonElement()

    override fun getFilterList(data: JsonElement?) = FilterList(
        buildList {
            add(SortFilter())
            add(StatusFilter())
            data?.parseAs<List<GenreDto>>()?.let { genres ->
                add(GenreFilter(genres.map { GenreCheckBox(it.nombre, it.id) }))
            }
        },
    )

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size < 2 || segments[0] != "manga") return null
        return client.get("$apiUrl/mangas/${segments[1]}").parseAs<MangaDto>().toSMangaDetails(baseUrl)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/manga/${manga.url}"

    override fun getChapterUrl(chapter: SChapter): String {
        val (mangaId, number) = chapter.url.split("/", limit = 2)
        return "$baseUrl/manga/$mangaId/chapter/$number"
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val dto = client.get("$apiUrl/mangas/${manga.url}").parseAs<MangaDto>()
        return SMangaUpdate(
            dto.toSMangaDetails(baseUrl),
            dto.capitulos
                .filter { it.estado == null || it.estado == "publicado" }
                .sortedByDescending { it.number }
                .map { it.toSChapter(manga.url) },
        )
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val dto = sessionMutex.withLock { openChapter(chapter.url) }
        return dto.paginas.mapIndexed { i, page ->
            val url = HttpUrl.Builder()
                .scheme("http")
                .host(PAGE_HOST)
                .addPathSegments(chapter.url)
                .addQueryParameter("page", page.index.toString())
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
        val ticket = try {
            val session = runBlocking { chapterSession(chapterUrl) }
            mintPageTicket(chain, session, pageIndex)
                ?: mintPageTicket(chain, runBlocking { chapterSession(chapterUrl, rejected = session) }, pageIndex)
                ?: throw HttpException(401)
        } catch (e: IOException) {
            throw e
        } catch (e: Exception) {
            throw IOException(e.message, e)
        }
        val response = chain.proceed(request.newBuilder().url("$apiUrl/mangas/pages/${ticket.signedToken}").build())
        if (!response.isSuccessful) return response

        val body = response.body
        val source = body.source()
        if (!source.request(HEADER_SIZE.toLong())) return response
        if (source.buffer.size < HEADER_SIZE) return response
        val header = source.buffer.snapshot(HEADER_SIZE).toByteArray()
        val decoded = header.xor(ticket.xorByte)
        // XOR-ed with the ticket's byte unless it already starts with an image signature
        if (isImage(header) || !isImage(decoded)) return response

        val bytes = body.bytes()
        val decodedBytes = bytes.xor(ticket.xorByte)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
        if (bitmap == null || ticket.scrambleKey == null || bitmap.width < 800 || bitmap.height < 800) {
            return response.newBuilder()
                .body(decodedBytes.toResponseBody(body.contentType()))
                .build()
        }
        val descrambled = descramble(bitmap, ticket.scrambleKey)
        bitmap.recycle()
        val out = Buffer()
        descrambled.compress(Bitmap.CompressFormat.PNG, 100, out.outputStream())
        descrambled.recycle()
        return response.newBuilder()
            .body(out.readByteArray().toResponseBody("image/png".toMediaType()))
            .build()
    }

    private fun ByteArray.xor(key: Int): ByteArray = ByteArray(size) { (this[it].toInt() xor key).toByte() }

    private fun imul(a: Int, b: Int): Int = (a.toLong() * b.toLong()).toInt()

    private fun seededRandom(key: String): () -> Double {
        var hash = 0
        for (c in key) {
            hash = imul(hash, 31) + c.code
        }
        var state = hash
        return {
            state += 1831565813
            var y = state
            y = imul(y xor (y ushr 15), y or 1)
            y = y xor (y + imul(y xor (y ushr 7), y or 61))
            (y xor (y ushr 14)).toUInt().toLong().toDouble() / 4294967296.0
        }
    }

    private fun descramble(bitmap: Bitmap, key: String): Bitmap {
        val rand = seededRandom(key)
        val cols = 8
        val rows = 8
        val w = bitmap.width
        val h = bitmap.height

        val colBounds = IntArray(cols + 1)
        colBounds[0] = 0
        colBounds[cols] = w
        val baseColW = w / cols
        var x = 0
        for (i in 0 until cols - 1) {
            var cw = baseColW + Math.round((rand() * 0.7 - 0.35) * baseColW).toInt()
            val remaining = cols - 1 - i
            val maxW = w - x - remaining * 16
            cw = cw.coerceIn(16, maxOf(16, maxW))
            x += cw
            colBounds[i + 1] = x
        }

        val rowBounds = IntArray(rows + 1)
        rowBounds[0] = 0
        rowBounds[rows] = h
        val baseRowH = h / rows
        var yy = 0
        for (i in 0 until rows - 1) {
            var rh = baseRowH + Math.round((rand() * 0.7 - 0.35) * baseRowH).toInt()
            val remaining = rows - 1 - i
            val maxH = h - yy - remaining * 16
            rh = rh.coerceIn(16, maxOf(16, maxH))
            yy += rh
            rowBounds[i + 1] = yy
        }

        val colPerm = IntArray(cols) { it }
        for (i in cols - 1 downTo 1) {
            val j = (rand() * (i + 1)).toInt()
            val tmp = colPerm[i]
            colPerm[i] = colPerm[j]
            colPerm[j] = tmp
        }

        val rowPerm = IntArray(rows) { it }
        for (i in rows - 1 downTo 1) {
            val j = (rand() * (i + 1)).toInt()
            val tmp = rowPerm[i]
            rowPerm[i] = rowPerm[j]
            rowPerm[j] = tmp
        }

        val invCol = IntArray(cols)
        for (i in 0 until cols) invCol[colPerm[i]] = i
        val invRow = IntArray(rows)
        for (i in 0 until rows) invRow[rowPerm[i]] = i

        val shuffledColPos = IntArray(cols + 1)
        shuffledColPos[0] = 0
        for (i in 0 until cols) {
            val shuffledIdx = colPerm[i]
            shuffledColPos[i + 1] = shuffledColPos[i] + (colBounds[shuffledIdx + 1] - colBounds[shuffledIdx])
        }

        val shuffledRowPos = IntArray(rows + 1)
        shuffledRowPos[0] = 0
        for (i in 0 until rows) {
            val shuffledIdx = rowPerm[i]
            shuffledRowPos[i + 1] = shuffledRowPos[i] + (rowBounds[shuffledIdx + 1] - rowBounds[shuffledIdx])
        }

        val output = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint().apply { isFilterBitmap = false }

        for (row in 0 until rows) {
            for (col in 0 until cols) {
                val dx = colBounds[col]
                val dy = rowBounds[row]
                val dw = colBounds[col + 1] - colBounds[col]
                val dh = rowBounds[row + 1] - rowBounds[row]
                val sx = shuffledColPos[invCol[col]]
                val sy = shuffledRowPos[invRow[row]]
                val src = Rect(sx, sy, sx + dw, sy + dh)
                val dst = Rect(dx, dy, dx + dw, dy + dh)
                canvas.drawBitmap(bitmap, src, dst, paint)
            }
        }
        return output
    }

    private fun isImage(bytes: ByteArray): Boolean {
        val b = IntArray(HEADER_SIZE) { bytes[it].toInt() and 0xFF }
        return (b[0] == 0x52 && b[1] == 0x49 && b[2] == 0x46 && b[3] == 0x46) ||
            (b[0] == 0x89 && b[1] == 0x50 && b[2] == 0x4E && b[3] == 0x47) ||
            (b[0] == 0xFF && b[1] == 0xD8 && b[2] == 0xFF) ||
            (b[0] == 0x47 && b[1] == 0x49 && b[2] == 0x46) ||
            (b[4] == 0x66 && b[5] == 0x74 && b[6] == 0x79 && b[7] == 0x70)
    }

    private class ChapterSession(val chapterId: Int, val token: String, val expiresAt: Instant)

    private class FailedOpen(val chapterUrl: String, val failedAt: Instant, val error: Exception)

    private val sessionMutex = Mutex()
    private val chapterSessions = HashMap<String, ChapterSession>()
    private var failedOpen: FailedOpen? = null
    private var currentReaderPass: ReaderPassDto? = null

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
        val url = "$apiUrl/mangas/$chapterUrl"
        val dto = fetchChapter(url, readerPass())
            ?: run {
                currentReaderPass = null
                fetchChapter(url, readerPass())
            }
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
    private suspend fun fetchChapter(url: String, pass: ReaderPassDto): PagesDto? {
        val passHeaders = headersBuilder().set("X-Catharsis-Reader-Pass", pass.pass).build()
        val response = client.get(url, passHeaders, ensureSuccess = false)
        if (response.isSuccessful) return response.parseAs<PagesDto>()
        if (response.code == 401) {
            response.close()
            throw Exception("This chapter requires login on the website")
        }
        val code = response.errorCode()
        if (code == "READER_PASS_REQUIRED") return null
        throw siteError(response.code, code)
    }

    // Null when the site rejects the session token: expired, or issued to another IP
    private fun mintPageTicket(chain: Interceptor.Chain, session: ChapterSession, pageIndex: Int): PageTicketResponse? {
        val request = POST(
            "$apiUrl/mangas/chapters/${session.chapterId}/page-ticket",
            headers,
            PageTicketRequest(pageIndex, session.token).toJsonRequestBody(),
        )
        val response = chain.proceed(request)
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

    // Reader pass from the site's Cap proof-of-work; caller holds sessionMutex
    private suspend fun readerPass(): ReaderPassDto {
        currentReaderPass
            ?.takeIf { Instant.fromEpochMilliseconds(it.expiresAt) > Clock.System.now() + 1.minutes }
            ?.let { return it }
        val system = client.get("$apiUrl/system").parseAs<SystemDto>()
        val capUrl = system.capApiEndpoint.toHttpUrl()
        val challenge = client.post(capUrl.resolve("challenge")!!, RequestBody.EMPTY).parseAs<CapChallengeResponse>()
        val solutions = (1..challenge.challenge.count).map { i ->
            solveCap(
                salt = capPrng("${challenge.token}$i", challenge.challenge.saltSize),
                target = capPrng("${challenge.token}${i}d", challenge.challenge.difficulty),
            )
        }
        val redeemed = client.post(
            capUrl.resolve("redeem")!!,
            CapRedeemRequest(challenge.token, solutions).toJsonRequestBody(),
        ).parseAs<CapRedeemResponse>()
        val capToken = redeemed.token?.takeIf { redeemed.success }
            ?: throw Exception("Reader verification failed")
        val response = client.post(
            "$apiUrl/mangas/reader-pass",
            ReaderPassRequest(capToken, system.capSitekey).toJsonRequestBody(),
            ensureSuccess = false,
        )
        if (!response.isSuccessful) throw siteError(response.code, response.errorCode())
        return response.parseAs<ReaderPassDto>().also { currentReaderPass = it }
    }

    // FNV-1a seeded xorshift32, as hex
    private fun capPrng(seed: String, length: Int): String {
        var state = seed.fold(FNV_OFFSET_BASIS) { hash, char -> (hash xor char.code) * FNV_PRIME }
        return buildString {
            while (this.length < length) {
                state = state xor (state shl 13)
                state = state xor (state ushr 17)
                state = state xor (state shl 5)
                append(state.toUInt().toString(16).padStart(8, '0'))
            }
        }.take(length)
    }

    private fun solveCap(salt: String, target: String): Int = generateSequence(0) { it + 1 }
        .first { "$salt$it".sha256().toHexString().startsWith(target) }

    private fun Response.errorCode(): String? {
        if (header("Content-Type")?.startsWith("application/json") != true) {
            close()
            return null
        }
        return parseAs<ApiErrorDto>().code
    }

    // Error codes the site's reader shows a message for
    private fun siteError(status: Int, code: String?): Exception = when (code) {
        "DATACENTER_BLOCKED" -> Exception("The site doesn't allow reading through a VPN or proxy")
        "EARLY_ACCESS" -> Exception("This chapter is still in early access on the site")
        else -> HttpException(status)
    }

    private fun signatureInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.encodedPath.startsWith("/api/")) {
            return chain.proceed(request)
        }
        val timestamp = Clock.System.now().toEpochMilliseconds().toString()
        val nonce = generateNonce()
        val signature = "$timestamp:$nonce:${request.method}".hmacSha256(HMAC_KEY.encodeToByteArray()).toHexString()
        val newRequest = request.newBuilder()
            .addHeader("x-catharsis-time", timestamp)
            .addHeader("x-catharsis-nonce", nonce)
            .addHeader("x-catharsis-sig", signature)
            .build()
        return chain.proceed(newRequest)
    }

    private fun generateNonce(): String {
        val chars = ('a'..'z') + ('A'..'Z') + ('0'..'9')
        return (1..16).map { chars[Random.nextInt(chars.size)] }.joinToString("")
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val HMAC_KEY = "4$^£]C8j%La3"
        private const val HEADER_SIZE = 12
        private const val PAGE_HOST = "catharsisworld.invalid"
        private const val FNV_OFFSET_BASIS = -0x7ee3623b
        private const val FNV_PRIME = 0x01000193
    }
}
