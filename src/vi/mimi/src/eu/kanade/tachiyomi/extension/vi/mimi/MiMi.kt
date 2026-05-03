package eu.kanade.tachiyomi.extension.vi.mimi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class MiMi : HttpSource() {

    override val name = "MiMi"

    override val baseUrl: String = "https://mimimoe.moe"

    private val apiUrl: String = "$baseUrl/api"

    override val lang = "vi"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var genreList: List<GenreDto> = emptyList()
    private var fetchGenresAttempts: Int = 0

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Common ======================================

    private fun parseMangaPage(response: Response): MangasPage {
        val result = response.parseAs<PaginatedResponse>()
        val mangas = result.items.map { it.toSMangaSimple() }
        return MangasPage(mangas, result.has_next)
    }

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "views")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", PAGE_SIZE.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("sort", "updated_at")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("page_size", PAGE_SIZE.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = parseMangaPage(response)

    // ============================== Search ======================================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_ID_SEARCH)) {
            val id = query.removePrefix(PREFIX_ID_SEARCH).trim()
            return client.newCall(GET("$apiUrl/manga/$id", headers))
                .asObservableSuccess()
                .map { MangasPage(listOf(it.parseAs<MangaDto>().toSManga()), false) }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/manga/advanced-search".toHttpUrl().newBuilder().apply {
            (filters.ifEmpty { getFilterList() }).forEach { filter ->
                when (filter) {
                    is SortByFilter -> addQueryParameter("sort", filter.selectedSort)

                    is GenresFilter -> filter.state.forEach {
                        when (it.state) {
                            Filter.TriState.STATE_INCLUDE -> addQueryParameter("genre", it.id.toString())
                            Filter.TriState.STATE_EXCLUDE -> addQueryParameter("exclude_genre", it.id.toString())
                        }
                    }

                    is TextField -> if (filter.state.isNotEmpty()) {
                        addQueryParameter(filter.key, filter.state)
                    }
                    else -> {}
                }
            }
            addQueryParameter("page", page.toString())
            addQueryParameter("page_size", PAGE_SIZE.toString())
            if (query.isNotBlank()) addQueryParameter("title", query)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = parseMangaPage(response)

    private fun fetchGenres() {
        if (genreList.isEmpty() && fetchGenresAttempts < 3) {
            scope.launch {
                try {
                    client.newCall(GET("$apiUrl/genres", headers)).await()
                        .use { response ->
                            response.parseAs<List<GenreDto>>()
                                .takeIf { it.isNotEmpty() }
                                ?.let { genreList = it }
                        }
                } catch (_: Exception) {
                } finally {
                    fetchGenresAttempts++
                }
            }
        }
    }

    // ============================== Filters ======================================

    override fun getFilterList(): FilterList {
        fetchGenres()
        return getFilters(genreList)
    }

    // ============================== Details ======================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        return GET("$apiUrl/manga/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDto>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // ============================== Chapters ======================================

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        return GET("$apiUrl/manga/$id/chapters", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.parseAs<List<ChapterDto>>().map { it.toSChapter() }.sortedByDescending { it.chapter_number }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ============================== Pages ======================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        return GET("$apiUrl/chapters/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<ChapterPageDto>().toPageList()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headersBuilder()
            .set("Referer", baseUrl)
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        private const val PAGE_SIZE = 24
    }
}
