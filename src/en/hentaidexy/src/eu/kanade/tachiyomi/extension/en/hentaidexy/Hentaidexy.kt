package eu.kanade.tachiyomi.extension.en.hentaidexy

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class Hentaidexy : HttpSource() {

    override val name = "Hentaidexy"

    override val baseUrl = "https://hentaidexy.net"

    private val apiUrl = "https://backend.hentaidexy.net"

    override val lang = "en"

    override val supportsLatest = true

    override val versionId = 2

    private val json: Json by injectLazy()

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimitHost(apiUrl.toHttpUrl(), 1)
        .build()

    override fun headersBuilder() = Headers.Builder().apply {
        add("Referer", "$baseUrl/")
    }

    // popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/api/v1/mangas?page=$page&limit=100&sort=-views", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<ApiMangaResponse>(response.body.string())
        return MangasPage(
            result.mangas.map { manga ->
                toSManga(manga).apply {
                    initialized = true
                }
            },
            hasNextPage = result.totalPages > result.page,
        )
    }

    // latest
    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$apiUrl/api/v1/mangas?page=$page&limit=100&sort=-updatedAt", headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(ID_SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val id = query.substringAfter(ID_SEARCH_PREFIX)
        return fetchMangaDetails(SManga.create().apply { url = id }).map {
            MangasPage(listOf(it), false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$apiUrl/api/v1/mangas?page=$page&altTitles=$query&sort=createdAt", headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // manga details
    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$apiUrl/api/v1/mangas/${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val mangaDetails = json.decodeFromString<MangaDetails>(response.body.string())
        return toSManga(mangaDetails.manga)
    }

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url
        val slug = manga.title.replace(' ', '-')
        return "$baseUrl/manga/$mangaId/$slug"
    }

    // chapter list
    override fun chapterListRequest(manga: SManga): Request {
        return paginatedChapterListRequest(manga.url, 1)
    }

    private fun paginatedChapterListRequest(mangaID: String, page: Int): Request {
        return GET("$apiUrl/api/v1/mangas/$mangaID/chapters?sort=-serialNumber&limit=100&page=$page", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val chapterListResponse = json.decodeFromString<ApiChapterResponse>(response.body.string())

        val mangaId = response.request.url.toString()
            .substringAfter("/mangas/")
            .substringBefore("/chapters")

        val totalPages = chapterListResponse.totalPages
        var currentPage = 1

        while (totalPages > currentPage) {
            currentPage++
            val newRequest = paginatedChapterListRequest(mangaId, currentPage)
            val newResponse = client.newCall(newRequest).execute()
            val newChapterListResponse = json.decodeFromString<ApiChapterResponse>(newResponse.body.string())

            chapterListResponse.chapters += newChapterListResponse.chapters
        }

        return chapterListResponse.chapters.map { chapter ->
            SChapter.create().apply {
                url = "/manga/$mangaId/chapter/${chapter._id}"
                name = "Chapter " + chapter.serialNumber.parseChapterNumber()
                date_upload = chapter.createdAt.parseDate()
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    // page list
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast('/')
        return GET("$apiUrl/api/v1/chapters/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<PageList>(response.body.string())
        return result.chapter.images.mapIndexed { index, image ->
            Page(index = index, imageUrl = image)
        }
    }

    // unused
    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException("Not Used")
    }

    // Helpers
    private fun toSManga(manga: Manga): SManga {
        return SManga.create().apply {
            url = manga._id
            title = manga.title
            author = manga.authors?.joinToString { it.trim() }
            artist = author
            description = manga.summary.trim() + "\n\nAlternative Names: ${manga.altTitles?.joinToString { it.trim() }}"
            genre = manga.genres?.joinToString { it.trim() }
            status = manga.status.parseStatus()
            thumbnail_url = manga.coverImage
        }
    }

    private fun String.parseStatus(): Int {
        return when {
            this.contains("ongoing", true) -> SManga.ONGOING
            this.contains("complete", true) -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun Float.parseChapterNumber(): String {
        return if (this.toInt().toFloat() == this) {
            this.toInt().toString()
        } else {
            this.toString()
        }
    }

    private fun String.parseDate(): Long {
        return runCatching { DATE_FORMATTER.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
        }

        const val ID_SEARCH_PREFIX = "id:"
    }
}
