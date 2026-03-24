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

    private val domain = "mimimoe.moe"

    override val baseUrl: String = "https://$domain"

    private val apiUrl: String = "https://api.$domain/api/v2/manga"

    override val lang = "vi"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .addInterceptor(MiMiImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int): Request = GET(
        apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("tatcatruyen")
            addQueryParameter("page", (page - 1).toString())
            addQueryParameter("sort", "views")
            addQueryParameter("ex", "196")
            addQueryParameter("reup", "true")
        }.build(),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<DataDto>()
        val mangas = result.data.map { it.toSManga() }
        val hasNextPage = result.currentPage < result.totalPage - 1
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int): Request = GET(
        apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("tatcatruyen")
            addQueryParameter("page", (page - 1).toString())
            addQueryParameter("sort", "updated_at")
            addQueryParameter("ex", "196")
            addQueryParameter("reup", "true")
        }.build(),
        headers,
    )

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ======================================

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = when {
        query.startsWith(PREFIX_ID_SEARCH) -> {
            val id = query.removePrefix(PREFIX_ID_SEARCH)
            client.newCall(searchMangaByIdRequest(id))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response) }
        }
        query.toIntOrNull() != null -> {
            client.newCall(searchMangaByIdRequest(query))
                .asObservableSuccess()
                .map { response -> searchMangaByIdParse(response) }
        }
        else -> super.fetchSearchManga(page, query, filters)
    }

    private fun searchMangaByIdRequest(id: String) = GET("$apiUrl/info/$id", headers)

    private fun searchMangaByIdParse(response: Response): MangasPage {
        val details = mangaDetailsParse(response)
        return MangasPage(listOf(details), false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("advance-search")
            (if (filters.isEmpty()) getFilterList() else filters).forEach { filter ->
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

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    private fun genresRequest(): Request = GET("$apiUrl/genres", headers)

    private fun parseGenres(response: Response): List<Pair<String, Int>> = response.parseAs<List<GenreDto>>().map { Pair(it.name, it.id) }

    private var fetchGenresAttempts: Int = 0
    private fun fetchGenres() {
        if (fetchGenresAttempts >= 3 || genreList.isEmpty()) {
            launchIO {
                try {
                    client.newCall(genresRequest()).await()
                        .use { parseGenres(it) }
                        .takeIf { it.isNotEmpty() }
                        ?.also { genreList = it }
                } catch (_: Exception) {
                } finally {
                    fetchGenresAttempts++
                }
            }
        }
    }

    private fun launchIO(block: suspend () -> Unit) = CoroutineScope(SupervisorJob() + Dispatchers.IO).launch { block() }

    private var genreList: List<Pair<String, Int>> = emptyList()

    // ============================== Filters ======================================

    override fun getFilterList(): FilterList {
        fetchGenres()
        return getFilters(genreList)
    }

    // ============================== Details ======================================

    override fun mangaDetailsRequest(manga: SManga): Request {
        val checkUrl = if (manga.url.startsWith("/g/")) manga.url.replace("/g/", "") else manga.url
        return GET("$apiUrl/info/$checkUrl", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<MangaDto>().toSManga()

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/g/${manga.url}"

    // ============================== Chapters ======================================

    override fun chapterListRequest(manga: SManga): Request {
        val checkUrl = if (manga.url.startsWith("/g/")) manga.url.replace("/g/", "") else manga.url
        return GET("$apiUrl/gallery/$checkUrl", headers)
    }

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
