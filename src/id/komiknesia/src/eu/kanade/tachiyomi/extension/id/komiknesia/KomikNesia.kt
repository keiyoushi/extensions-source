package eu.kanade.tachiyomi.extension.id.komiknesia

import eu.kanade.tachiyomi.network.GET
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
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class KomikNesia : HttpSource() {

    override val name = "KomikNesia"
    override val baseUrl = "https://02.komiknesia.asia"
    private val apiUrl = "https://api-be.komiknesia.my.id/api"
    override val lang = "id"
    override val supportsLatest = true

    private var genresList: List<Pair<String, String>> = emptyList()
    private var genresFetched: Boolean = false
    private var fetchGenresAttempts: Int = 0

    private val scope = CoroutineScope(Dispatchers.IO)
    private fun launchIO(block: () -> Unit) = scope.launch { block() }

    // ===============================
    // Popular
    // ===============================

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(OrderFilter().apply { state = 2 }))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // ===============================
    // Latest
    // ===============================

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(OrderFilter().apply { state = 0 }))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // ===============================
    // Search
    // ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/contents".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        if (query.isNotEmpty()) {
            url.addQueryParameter("q", query)
        }

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.state
                        .filter { it.state }
                        .forEach { url.addQueryParameter("genre[]", it.id) }
                }
                is StatusFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("status", filter.toUriPart())
                    }
                }
                is OrderFilter -> {
                    if (filter.state != 0) {
                        url.addQueryParameter("orderBy", filter.toUriPart())
                    }
                }
                else -> {}
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val payload = response.parseAs<PayloadDto<List<MangaDto>>>()
        val mangas = payload.data.map { it.toSManga() }
        val hasNextPage = payload.meta?.let { it.page < it.totalPages } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    // ===============================
    // Details
    // ===============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$apiUrl/comic/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val payload = response.parseAs<PayloadDto<MangaDto>>()
        return payload.data.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/komik/${manga.url}"

    // ===============================
    // Chapters
    // ===============================

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val payload = response.parseAs<PayloadDto<MangaDto>>()
        return payload.data.chapters?.map { it.toSChapter() } ?: emptyList()
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/view/${chapter.url}"

    // ===============================
    // Pages
    // ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET("$apiUrl/chapters/slug/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val payload = response.parseAs<PayloadDto<PageListDto>>()
        return payload.data.images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ===============================
    // Filters
    // ===============================

    override fun getFilterList(): FilterList {
        launchIO { fetchGenres() }

        val filters = mutableListOf<Filter<*>>(
            OrderFilter(),
            StatusFilter(),
        )

        if (genresList.isNotEmpty()) {
            filters += listOf(
                Filter.Separator(),
                GenreFilter(genresList.map { Genre(it.first, it.second) }),
            )
        } else {
            filters += listOf(
                Filter.Header("Press 'Reset' to load genres"),
            )
        }

        return FilterList(filters)
    }

    private fun fetchGenres() {
        if (fetchGenresAttempts < 3 && !genresFetched) {
            try {
                client.newCall(GET("$apiUrl/contents/genres", headers)).execute().use { response ->
                    val payload = response.parseAs<PayloadDto<List<GenreDto>>>()
                    genresList = payload.data.map { it.name to it.id.toString() }
                    genresFetched = true
                }
            } catch (_: Exception) {
            } finally {
                fetchGenresAttempts++
            }
        }
    }
}
