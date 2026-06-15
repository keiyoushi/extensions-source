package eu.kanade.tachiyomi.extension.es.capibaratraductor

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.network.rateLimit
import keiyoushi.utils.parseAs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response

class CapibaraTraductor : HttpSource() {

    override val name = "CapibaraTraductor"

    override val baseUrl = "https://capibaratraductor.com"

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    override val lang = "es"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(3) { it.host == baseUrlHost }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun getScanHeaders(organizationSlug: String): Headers = headersBuilder()
        .add("x-organization", organizationSlug)
        .build()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/manga-custom?page=$page&limit=$PAGE_LIMIT&order=popular", headers)

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/manga-custom?page=$page&limit=$PAGE_LIMIT&order=latest", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/manga-custom".toHttpUrl().newBuilder()

        url.setQueryParameter("page", page.toString())
        url.setQueryParameter("limit", PAGE_LIMIT.toString())

        var headers = headersBuilder()

        filters.forEach { filter ->
            when (filter) {
                is SortByFilter -> url.setQueryParameter("order", filter.toUriPart())
                is ScanlatorFilter -> if (filter.state != 0) headers["x-organization"] = filter.toUriPart()
                else -> {}
            }
        }

        if (query.isNotBlank()) url.setQueryParameter("search", query)

        return GET(url.build(), headers.build())
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val page = response.request.url.queryParameter("page")!!.toInt()
        val result = response.parseAs<Data<SeriesListDataDto>>()

        val mangas = result.data.series.map { it.toSManga() }
        val hasNextPage = page < result.data.maxPage

        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(): FilterList {
        fetchScanList()

        val filters = mutableListOf<Filter<*>>()

        if (scanList.isEmpty()) {
            filters.add(Filter.Header("Presione 'Restablecer' para intentar cargar la lista de scans"))
        } else {
            filters.add(ScanlatorFilter("Scanlator", scanList))
        }

        filters.add(SortByFilter("Ordenar por", getSortList()))

        return FilterList(filters)
    }

    private fun getSortList() = arrayOf(
        Pair("Recientes", "latest"),
        Pair("Popularidad", "popular"),
        Pair("A-Z", "alphabetical"),
    )

    private var scanList = emptyArray<Pair<String, String>>()
    private var fetchScansAttempts = 0
    private var scansState = FiltersState.NOT_FETCHED

    private fun fetchScanList() {
        if (scansState != FiltersState.NOT_FETCHED || fetchScansAttempts >= 3) {
            return
        }

        scansState = FiltersState.FETCHING
        fetchScansAttempts++

        scope.launch {
            try {
                val sfwScans = fetchAllScans(includeNsfw = false)
                val nsfwScans = fetchAllScans(includeNsfw = true)

                scanList = buildList {
                    add("Todos" to "")
                    addAll(
                        (sfwScans + nsfwScans)
                            .distinctBy { it.second }
                            .sortedBy { it.first },
                    )
                }.toTypedArray()

                scansState = FiltersState.FETCHED
            } catch (_: Exception) {
                scansState = FiltersState.NOT_FETCHED
            }
        }
    }

    private fun fetchAllScans(includeNsfw: Boolean): List<Pair<String, String>> {
        val scans = mutableListOf<Pair<String, String>>()
        var page = 1

        while (true) {
            val url = "$baseUrl/api/landing/scans".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("sort", "name")
                .addQueryParameter("limit", "100")
                .apply {
                    if (includeNsfw) {
                        addQueryParameter("includeNSFW", "true")
                    }
                }
                .build()

            val response = client.newCall(GET(url, headers))
                .execute()
                .parseAs<Data<ScanListDto>>()
                .data

            scans += response.items.map { it.name to it.id }

            if (!response.hasNextPage()) {
                break
            }

            page++
        }

        return scans
    }

    override fun getMangaUrl(manga: SManga): String {
        val (seriesSlug, organizationSlug) = manga.url.split("/", limit = 2)
        return "$baseUrl/$organizationSlug/manga/$seriesSlug"
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val (seriesSlug, organizationSlug) = manga.url.split("/", limit = 2)
        return GET("$baseUrl/api/manga-custom/$seriesSlug", getScanHeaders(organizationSlug))
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<Data<SeriesDto>>()
        return result.data.toSMangaDetails()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val (chapterSlug, seriesSlug, organizationSlug) = chapter.url.split("/", limit = 3)

        return "$baseUrl/$organizationSlug/manga/$seriesSlug/chapters/$chapterSlug"
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<Data<SeriesDto>>()
        val seriesSlug = result.data.manga.slug
        val organizationSlug = result.data.organization.slug
        return result.data.chapters
            ?.filter { it.isUnreleased.not() }
            ?.map { it.toSChapter(seriesSlug, organizationSlug) }
            ?.filter { it.date_upload < System.currentTimeMillis() }
            ?: emptyList()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val (chapterSlug, seriesSlug, organizationSlug) = chapter.url.split("/", limit = 3)

        return GET("$baseUrl/api/manga-custom/$seriesSlug/chapter/$chapterSlug/pages", getScanHeaders(organizationSlug))
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<Data<List<PageDto>>>()
        return result.data.mapIndexed { i, page ->
            Page(i, imageUrl = page.imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

    companion object {
        private const val PAGE_LIMIT = 36
    }
}
