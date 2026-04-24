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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable

class MiMi : HttpSource() {

    override val name = "MiMi"

    private val domain = "mimimoe.moe"

    override val baseUrl: String = "https://$domain"

    private val apiUrl: String = "https://api.$domain/api/v2/manga"

    override val lang = "vi"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .addInterceptor(MiMiImageInterceptor())
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var genreList: List<Pair<String, Int>> = emptyList()
    private var fetchGenresAttempts: Int = 0

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Common ======================================

    private fun HttpUrl.Builder.addCommonParams(page: Int) = apply {
        addQueryParameter("page", (page - 1).toString())
        addQueryParameter("ex", "196")
        addQueryParameter("reup", "true")
    }

    private fun parseMangaPage(response: Response): MangasPage {
        val result = response.parseAs<DataDto>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.currentPage < result.totalPage - 1
        return MangasPage(mangas, hasNextPage)
    }

    private fun SManga.pureUrl() = url.removePrefix("/g/").removePrefix("/")

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("tatcatruyen")
            .addQueryParameter("sort", "views")
            .addCommonParams(page)
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = apiUrl.toHttpUrl().newBuilder()
            .addPathSegments("tatcatruyen")
            .addQueryParameter("sort", "updated_at")
            .addCommonParams(page)
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = parseMangaPage(response)

    // ============================== Search ======================================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val isIdSearch = query.startsWith(PREFIX_ID_SEARCH) ||
            (query.length >= 4 && query.toIntOrNull() != null)

        if (isIdSearch) {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            return client.newCall(GET("$apiUrl/info/$id", headers))
                .asObservableSuccess()
                .map { MangasPage(listOf(it.parseAs<MangaDto>().toSManga()), false) }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("advance-search")
            (filters.ifEmpty { getFilterList() }).forEach { filter ->
                when (filter) {
                    is SortByList -> addQueryParameter("sort", filter.values[filter.state].id)

                    is GenresFilter -> filter.state.forEach {
                        when (it.state) {
                            Filter.TriState.STATE_INCLUDE -> addQueryParameter("genre", it.id)
                            Filter.TriState.STATE_EXCLUDE -> addQueryParameter("ex", it.id)
                        }
                    }

                    is TextField -> if (filter.state.isNotEmpty()) setQueryParameter(filter.key, filter.state)
                    else -> {}
                }
            }
            addQueryParameter("max", "18")
            addQueryParameter("page", (page - 1).toString())
            if (query.isNotBlank()) addQueryParameter("name", query)
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
                                .map { Pair(it.name, it.id) }
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

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/info/${manga.pureUrl()}", headers)

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDto>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/g/${manga.url}"

    // ============================== Chapters ======================================

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/gallery/${manga.pureUrl()}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val segments = response.request.url.pathSegments
        val mangaId = segments.last()
        val result = response.parseAs<List<ChapterDto>>()
        return result.map { it.toSChapter(mangaId) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaId = chapter.url.substringBefore('/')
        val chapterId = chapter.url.substringAfter('/')
        return "$baseUrl/g/$mangaId/chapter/$chapterId"
    }

    // ============================== Pages ======================================

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfter("/")
        return GET("$apiUrl/chapter?id=$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> = response.parseAs<PageDto>().toPage()

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
