package eu.kanade.tachiyomi.extension.de.mangatube

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MemoryCookieJar : CookieJar {
    private val store = mutableMapOf<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val current = store.getOrPut(host) { mutableListOf() }

        for (newCookie in cookies) {
            current.removeAll { old ->
                old.name == newCookie.name &&
                    old.domain == newCookie.domain &&
                    old.path == newCookie.path
            }
            if (!newCookie.expiresAt.let { it < System.currentTimeMillis() }) {
                current += newCookie
            }
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        val hostCookies = store[url.host].orEmpty()

        return hostCookies.filter { cookie ->
            cookie.expiresAt >= now && cookie.matches(url)
        }
    }
}

@Serializable
data class Challenge(val tk: String, val arg1: String, val arg2: String, val arg3: String)

@Serializable
data class QuickSearchResponseData(
    val slug: String,
    val title: String,
    val titleOriginal: String,
    val titleAlternative: String,
    val cover: String,
    val url: String,
)

@Serializable
data class QuickSearchResponse(
    val current_page: Int,
    val data: List<QuickSearchResponseData>,
    val last_page: Int,
    val last_page_url: String,
    val next_page_url: String? = null,
    val per_page: Int,
    val prev_page_url: String? = null,
    val total: Int,
)

@Serializable
data class LatestUpdatesManga(
    val id: Long,
    val title: String,
    val chpFormat: String,
    val volFormat: String,
    val cover: String,
    val url: String,
)

@Serializable
data class LatestUpdatesEntry(
    val manga: LatestUpdatesManga,
)

@Serializable
data class LatestUpdatesData(
    val published: List<LatestUpdatesEntry>,
    val offset: Int,
)

@Serializable
data class LatestUpdatesVersion(
    val current: String,
)

@Serializable
data class LatestUpdatesResponse(
    val success: Boolean,
    val data: LatestUpdatesData,
    val version: LatestUpdatesVersion,
)

@Serializable
data class TopMangaEntry(
    val id: Long,
    val slug: String,
    val title: String,
    val titleOriginal: String,
    val titleAlternative: String,
    val release: String? = null,
    val statusScanlation: Int,
    val cover: String,
    val url: String,
)

@Serializable
data class TopMangaData(
    val manga: List<TopMangaEntry>,
)

@Serializable
data class TopMangaResponse(
    val success: Boolean,
    val data: TopMangaData,
)

@Serializable
data class MangaDetailsPerson(
    val id: Long,
    val name: String,
    val link: String,
)

@Serializable
data class MangaDetailsManga(
    val id: Long,
    val title: String,
    val titleOriginal: String,
    val titleAlternative: String,
    val description: String,
    val cover: String,
    val url: String,
    val status: Int,
    val statusScanlation: Int,
    val author: List<MangaDetailsPerson>,
    val artist: List<MangaDetailsPerson>,
)

@Serializable
data class MangaDetailsData(
    val manga: MangaDetailsManga,
)

@Serializable
data class MangaDetailsResponse(
    val success: Boolean,
    val data: MangaDetailsData,
)

@Serializable
data class MangaChapter(
    val id: Long,
    val number: Double,
    val subNumber: Double,
    val volume: Double,
    val name: String,
    val publishedAt: String,
    val readerURL: String,
)

@Serializable
data class MangaChaptersData(
    val chapters: List<MangaChapter>,
)

@Serializable
data class MangaChaptersResponse(
    val success: Boolean,
    val data: MangaChaptersData,
)

@Serializable
data class ChapterPage(
    val url: String,
    val alt_source: String,
    val height: Int,
    val width: Int,
    val page: Int,
)

@Serializable
data class ChapterDetails(
    val id: Long,
    val pages: List<ChapterPage>,
)

@Serializable
data class ChapterDetailsData(
    val chapter: ChapterDetails,
)

@Serializable
data class ChapterDetailsResponse(
    val success: Boolean,
    val data: ChapterDetailsData,
)

class ChallengeInterceptor(
    private val baseUrl: String,
    private val headers: Headers,
    private val client: OkHttpClient,
    private val cookieJar: CookieJar,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        if (originalRequest.header(CHALLENGE_BYPASSED_HEADER) == "1") {
            return chain.proceed(originalRequest)
        }

        if (originalRequest.url.encodedPath.startsWith("/api/") && needsBootstrap(originalRequest.url)) {
            bootstrapSession()
        }

        val response = chain.proceed(originalRequest)
        val bodyString = response.peekBody(Long.MAX_VALUE).string()
        if (!isChallengePage(bodyString)) {
            return response
        }

        response.close()
        solveChallenge(bodyString)

        return chain.proceed(
            originalRequest.newBuilder()
                .header(CHALLENGE_BYPASSED_HEADER, "1")
                .build(),
        )
    }

    private fun isChallengePage(body: String): Boolean = body.contains("window.__challange") || body.contains("_challange =")

    private fun needsBootstrap(url: HttpUrl): Boolean {
        val cookieNames = cookieJar.loadForRequest(url).map { it.name }.toSet()
        return REQUIRED_API_COOKIES.any { it !in cookieNames }
    }

    private fun bootstrapSession() {
        val response = client.newCall(GET(baseUrl, headers)).execute()
        val bodyString = response.peekBody(Long.MAX_VALUE).string()
        if (isChallengePage(bodyString)) {
            response.close()
            solveChallenge(bodyString)
            client.newCall(GET(baseUrl, headers)).execute().close()
            return
        }
        response.close()
    }

    private fun solveChallenge(body: String) {
        val challengeStr = CHALLENGE_REGEX.find(body)?.groupValues?.getOrNull(1)
            ?: throw IOException("Challenge payload not found")
        val challenge = Json.decodeFromString<Challenge>(challengeStr)
        val solution = solve(challenge.arg1, challenge.arg2, challenge.arg3)
        val challengeHeaders = headers.newBuilder()
            .add("Content-Type", "application/x-www-form-urlencoded")
            .add("x-challange-token", challenge.tk)
            .add("x-challange-arg1", challenge.arg1)
            .add("x-challange-arg2", challenge.arg2)
            .add("x-challange-arg3", challenge.arg3)
            .add("x-challange-arg4", solution)
            .build()

        Thread.sleep(CHALLENGE_DELAY_MS)
        client.newCall(POST("$baseUrl/", challengeHeaders))
            .execute()
            .use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Challenge validation failed: ${response.code}")
                }
            }
    }

    private fun solve(arg1: String, arg2: String, op: String): String {
        val left = arg1.toLong(16).toDouble()
        val right = arg2.toLong(16).toDouble()

        val result = when (op) {
            "a" -> left / right
            "b" -> left * right
            "c" -> left - right
            "d" -> left + right
            else -> throw IOException("Unknown challenge op: $op")
        }

        return if (result == result.toLong().toDouble()) {
            result.toLong().toString()
        } else {
            result.toString()
        }
    }

    companion object {
        private val CHALLENGE_REGEX = """_challange = (.+?);""".toRegex()
        private val REQUIRED_API_COOKIES = setOf("XSRF-TOKEN", "manga_tube_beta_session", "__mtbpass")
        private const val CHALLENGE_BYPASSED_HEADER = "X-MangaTube-Challenge-Bypassed"
        private const val CHALLENGE_DELAY_MS = 1_000L
    }
}

class MangaTube : ParsedHttpSource() {

    override val name = "Manga Tube"

    override val baseUrl = "https://manga-tube.me"

    override val lang = "de"

    override val supportsLatest = true

    val cookieJar = MemoryCookieJar()

    override val client: OkHttpClient by lazy {
        val baseClient = network.client.newBuilder()
            .cookieJar(cookieJar)
            .connectTimeout(1, TimeUnit.MINUTES)
            .readTimeout(1, TimeUnit.MINUTES)
            .writeTimeout(1, TimeUnit.MINUTES)
            .build()

        baseClient.newBuilder()
            .addInterceptor(ChallengeInterceptor(baseUrl, headers, baseClient, cookieJar))
            .build()
    }

    private val xhrHeaders: Headers = headersBuilder().add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8").build()
    private val apiHeaders: Headers = headersBuilder().add("Accept", "application/json").build()

    private val json: Json by injectLazy()
    private val apiJson = Json(json) {
        ignoreUnknownKeys = true
    }

    // Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = client.newCall(popularMangaRequest(page))
        .asObservableSuccess()
        .map(::popularMangaParse)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/home/top-manga", apiHeaders)

    private fun parseMangaFromSearch(response: Response): MangasPage {
        val mangas = apiJson.decodeFromString<QuickSearchResponse>(response.body.string())
            .data
            .map { json ->
                SManga.create().apply {
                    title = json.title
                    url = json.url
                    thumbnail_url = json.cover
                }
            }
        return MangasPage(mangas, false)
    }

    private fun parseLatestUpdates(response: Response): MangasPage {
        val result = apiJson.decodeFromString<LatestUpdatesResponse>(response.body.string())
        val mangas = result.data.published
            .map { entry ->
                SManga.create().apply {
                    title = entry.manga.title
                    url = entry.manga.url
                    thumbnail_url = entry.manga.cover
                }
            }
            .distinctBy { it.url }
        val requestedOffset = response.request.url.queryParameter("offset")?.toIntOrNull() ?: 0
        return MangasPage(mangas, requestedOffset < LATEST_PAGE_SIZE * 2)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = apiJson.decodeFromString<TopMangaResponse>(response.body.string())
        val mangas = result.data.manga.map { entry ->
            SManga.create().apply {
                title = entry.title
                url = entry.url
                thumbnail_url = entry.cover
            }
        }
        return MangasPage(mangas, false)
    }

    override fun popularMangaSelector() = throw UnsupportedOperationException()

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException()

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        val offset = (page - 1) * LATEST_PAGE_SIZE
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/home/updates")
            .addQueryParameter("offset", offset.toString())
            .build()
            .toString()
        return GET(url, apiHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseLatestUpdates(response)

    override fun latestUpdatesSelector() = throw UnsupportedOperationException()

    override fun latestUpdatesFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() = throw UnsupportedOperationException()

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments("api/manga/quick-search")
            .addQueryParameter("query", query)
            .build()
            .toString()
        return GET(url, apiHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaFromSearch(response)

    override fun searchMangaSelector() = throw UnsupportedOperationException()

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException()

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/manga/$slug", mangaApiHeaders(slug))
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = apiJson.decodeFromString<MangaDetailsResponse>(response.body.string())
        val manga = result.data.manga
        return SManga.create().apply {
            title = manga.title
            url = manga.url
            thumbnail_url = manga.cover
            description = manga.description
            author = manga.author.joinToString { it.name }
            artist = manga.artist.joinToString { it.name }
            status = manga.status.toSMangaStatus()
        }
    }

    override fun mangaDetailsParse(document: Document): SManga = throw UnsupportedOperationException()

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/manga/$slug/chapters", mangaApiHeaders(slug))
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = apiJson.decodeFromString<MangaChaptersResponse>(response.body.string())
        val slug = response.request.url.pathSegments[2]
        return result.data.chapters.map { chapter ->
            SChapter.create().apply {
                name = buildString {
                    if (chapter.volume > 0) {
                        append("Vol. ")
                        append(chapter.volume.trimTrailingZero())
                        append(" ")
                    }
                    append("Ch. ")
                    append(chapter.number.trimTrailingZero())
                    if (chapter.subNumber > 0) {
                        append(".")
                        append(chapter.subNumber.trimTrailingZero())
                    }
                    if (chapter.name.isNotBlank()) {
                        append(" - ")
                        append(chapter.name)
                    }
                }
                url = "/api/manga/$slug/chapter/${chapter.id}"
                date_upload = chapter.publishedAt.toDate()
            }
        }
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()
    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        val (slug, apiPath) = when {
            chapter.url.startsWith("/api/manga/") -> {
                val slug = chapter.url.substringAfter("/api/manga/").substringBefore("/chapter/")
                slug to chapter.url
            }
            chapter.url.startsWith("/series/") -> {
                val slug = chapter.url.substringAfter("/series/").substringBefore("/read/")
                val chapterId = chapter.url.substringAfter("/read/").substringBefore("/").toLong()
                slug to "/api/manga/$slug/chapter/$chapterId"
            }
            else -> error("Unsupported chapter url: ${chapter.url}")
        }
        val requestUrl = "$baseUrl$apiPath"
        return GET(requestUrl, mangaApiHeaders(slug))
    }

    override fun pageListParse(response: Response): List<Page> {
        try {
            val result = apiJson.decodeFromString<ChapterDetailsResponse>(response.body.string())
            return result.data.chapter.pages
                .sortedBy { it.page }
                .mapIndexed { index, page ->
                    Page(index, "", page.url.ifBlank { page.alt_source })
                }
        } catch (e: Exception) {
        }
        return emptyList()
    }

    override fun pageListParse(document: Document): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    companion object {
        private const val LATEST_PAGE_SIZE = 40
    }

    private fun mangaApiHeaders(slug: String): Headers = apiHeaders.newBuilder()
        .add("Referer", "$baseUrl/series/$slug")
        .add("Use-Parameter", "manga_slug")
        .build()
}

private fun Int.toSMangaStatus(): Int = when (this) {
    1 -> SManga.ONGOING
    2 -> SManga.COMPLETED
    else -> SManga.UNKNOWN
}

private fun Double.trimTrailingZero(): String = if (this == this.toLong().toDouble()) {
    this.toLong().toString()
} else {
    this.toString()
}

private fun String.toDate(): Long = runCatching {
    SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(this)?.time ?: 0L
}.getOrDefault(0L)
