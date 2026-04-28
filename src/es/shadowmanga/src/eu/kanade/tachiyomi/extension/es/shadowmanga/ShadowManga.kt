package eu.kanade.tachiyomi.extension.es.shadowmanga

import eu.kanade.tachiyomi.extension.es.shadowmanga.interceptor.ImageFallbackInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response

class ShadowManga : HttpSource() {

    override val name = "Shadow Manga"
    override val baseUrl = "https://shademanga.com"
    override val lang = "es"
    override val supportsLatest = true

    private val scope = CoroutineScope(Dispatchers.IO)

    private val cdnHosts = listOf(
        "media.shademanga.com",
        "cdn.shademanga.com",
    )

    private val fallbackPrefix = "/api/media/"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 3, 1)
        .addInterceptor(ImageFallbackInterceptor(cdnHosts, baseUrl.toHttpUrl().host, fallbackPrefix))
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/api/series-locales/popular", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<List<SeriesWrapper>>()
        val series = result.flatMap { it.series }.distinctBy { it.id }.map { it.toSManga() }
        return MangasPage(series, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/series-locales/novedades", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/series-locales/search-candidates".toHttpUrl().newBuilder()
        url.addQueryParameter("q", query)
        url.addQueryParameter("includeAdult", "true")
        url.addQueryParameter("showSinPortada", "false")
        url.addQueryParameter("take", MAX_RESULTS.toString())

        val genreFilter = filters.firstInstanceOrNull<GenreFilter>()

        genreFilter?.getIncluded()?.forEach {
            url.addQueryParameter("tags", it)
        }

        val excludedGenres = genreFilter?.getExcluded().orEmpty()

        return GET(url.build(), headers).newBuilder()
            .tag(List::class.java, excludedGenres)
            .build()
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val excludedGenres = response.request.tag(List::class.java) as? List<String>
        val mangas = response.parseAs<List<Series>>()
            .filter { series ->
                excludedGenres.isNullOrEmpty() || series.getGenreList().none { genre ->
                    genre in excludedGenres
                }
            }.sortedBy { it.title }
            .map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/api/series-locales/${manga.url}", headers)

    override fun getMangaUrl(manga: SManga) = "$baseUrl/serie/local/${manga.url}"

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<Series>().toSMangaDetails()

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val series = response.parseAs<Series>()
        return series.chapters
            .sortedByDescending { it.chapterNumber }
            .map { it.toSChapter(series.id) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterId = chapter.url.substringAfter("/")
        return "$baseUrl/reader/local/$chapterId"
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaId = chapter.url.substringBefore("/")
        val chapterId = chapter.url.substringAfter("/")
        return GET("$baseUrl/api/series-locales/$mangaId/capitulos/$chapterId/paginas", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PagesWrapper>()
        return result.pages.mapIndexed { index, url ->
            Page(index, imageUrl = url)
        }
    }

    override fun getFilterList(): FilterList {
        fetchFilters()
        return FilterList(
            if (genresList.isEmpty()) {
                Filter.Header("Presione 'Reiniciar' para intentar cargar los filtros.")
            } else {
                GenreFilter(genresList)
            },
        )
    }

    private var genresList: List<String> = emptyList()
    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters() {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        scope.launch {
            try {
                val response = client.newCall(GET("$baseUrl/api/series-locales/tags", headers)).execute()
                genresList = response.parseAs<List<String>>()

                filtersState = FiltersState.FETCHED
            } catch (_: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        const val MAX_RESULTS = 120
    }
}
