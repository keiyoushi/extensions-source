package eu.kanade.tachiyomi.extension.vi.mimi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class MiMi : HttpSource() {

    override val name = "MiMi"

    override val baseUrl = "https://mimimoe.moe"

    private val apiUrl get() = baseUrl.replace("://", "://api.") + "/api/v2"

    override val lang = "vi"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/manga/advance-search".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "views")
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("max", "18")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<SearchResponse>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.currentPage < result.totalPage - 1
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/manga/advance-search".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "updated_at")
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("max", "18")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/manga/advance-search".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "updated_at")
            .addQueryParameter("page", (page - 1).toString())
            .addQueryParameter("max", "18")

        if (query.isNotBlank()) {
            url.addQueryParameter("name", query)
        }

        filters.filterIsInstance<GenreFilter>().firstOrNull()?.let { filter ->
            val genreId = filter.toUriPart()
            if (genreId.isNotEmpty()) {
                url.addQueryParameter("genre", genreId)
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Filters ======================================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ======================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$apiUrl/manga/info/$mangaId", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<MangaInfo>()
        return result.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // ============================== Chapters ======================================

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringAfterLast("/")
        return GET("$apiUrl/manga/gallery/$mangaId", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<List<ChapterDto>>()
        return result.mapIndexed { index, chapter ->
            SChapter.create().apply {
                url = "/chapter/${chapter.id}"
                name = chapter.title?.takeIf { it.isNotBlank() } ?: "Chapter ${chapter.order}"
                chapter_number = chapter.order.toFloat()
                date_upload = chapter.createdAt?.let { parseDate(it) } ?: 0L
            }
        }
    }

    private fun parseDate(dateString: String): Long = try {
        dateFormat.parse(dateString)?.time ?: 0L
    } catch (_: Exception) {
        0L
    }

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT)
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ============================== Pages ======================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/manga/chapter?id=$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<ChapterPages>()
        return result.pages.mapIndexed { index, page ->
            Page(index, imageUrl = page.imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Helpers ======================================

    private inline fun <reified T> Response.parseAs(): T = json.decodeFromString<T>(body.string())
}
