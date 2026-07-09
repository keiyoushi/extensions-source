package eu.kanade.tachiyomi.extension.en.infinityscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.extractNextJs
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
@Source
abstract class InfinityScans : HttpSource() {

    private val cdnHost = "cv.infinityscans.org"
    private val pageCdnHost = "ch.infinityscans.org"

    private val slugHash = "cf675243bcc3"

    override val supportsLatest = true

    override val client: OkHttpClient = network.client.newBuilder()
        .addInterceptor(WebviewInterceptor(baseUrl))
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder().apply {
        add("Referer", "$baseUrl/")
    }

    private val rscHeaders = headersBuilder()
        .set("rsc", "1")
        .build()

    private val apiHeaders = headersBuilder().apply {
        add("Accept", "application/json, text/javascript, */*; q=0.01")
        add("Sec-Fetch-Dest", "empty")
        add("Sec-Fetch-Mode", "cors")
        add("Sec-Fetch-Site", "same-origin")
        add("X-requested-with", "XMLHttpRequest")
    }.build()

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int): Request = fetchJson("api/comics", page, SortType.Popularity.value)

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<SearchResultDto>()
        val entries = data.titles.map { it.toSManga(cdnHost) }

        return MangasPage(entries, !entries.isEmpty())
    }

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = fetchJson("api/comics", page, SortType.Latest.value)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ===============================

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        // load genre and author filters
        runCatching {
            if (cachedFilters == null) {
                client.newCall(fetchJson("api/comics/options", 0, "")).execute().use { res ->
                    if (res.isSuccessful) {
                        val dto = res.parseAs<FiltersDto>()
                        cachedFilters = dto.genres to dto.authors
                    }
                }
            }
        }

        val request = if (query.isBlank()) {
            val req = fetchJson("api/comics", page - 1, "")
            val urlBuilder = req.url.newBuilder().apply {
                filters.forEach { filter ->
                    when (filter) {
                        is SortFilter -> filter.selected?.let { addQueryParameter("sort", it) }

                        is GenreFilter -> filter.checked?.let { genres ->
                            genres.forEach { addQueryParameter("genre", it) }
                        }
                        is AuthorFilter -> filter.checked?.let { authors ->
                            authors.forEach { addQueryParameter("author", it) }
                        }
                        is StatusFilter -> filter.checked.firstOrNull()?.let { addQueryParameter("status", it) }

                        else -> {}
                    }
                }
            }
            GET(urlBuilder.build(), req.headers)
        } else {
            fetchJson("api/search", 0, "", query)
        }

        return client.newCall(request).asObservableSuccess().map { res ->
            val list = if (query.isNotEmpty()) {
                res.parseAs<ResponseDto<List<SearchEntryDto>>>().result
            } else {
                res.parseAs<SearchResultDto>().titles
            }
            val mangas = list.map { it.toSManga(cdnHost) }
            MangasPage(mangas, query.isEmpty() && mangas.isNotEmpty())
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Details ==============================

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/comic/${manga.url}-$slugHash", rscHeaders)

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.extractNextJs<MangaDetailsDto>()!!

        return SManga.create().apply {
            description = buildString {
                dto.description.content
                    .firstOrNull()
                    ?.content
                    ?.joinToString(" ") { it.text }
                    .orEmpty()
                    .let { append(it) }

                dto.altNames?.takeIf { it.isNotEmpty() }?.let {
                    append("\n\nAlternative Title: ")
                    append(it.joinToString(" · "))
                }
            }

            author = dto.authors.joinToString { it.name }
            genre = dto.genres.joinToString { it.name }
            status = dto.status.parseStatus()
        }
    }

    // ============================= Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        // url: id/slug, ignore slug for api calls
        val mangaId = manga.url.substringBefore("/")

        var allChapters = mutableListOf<ChapterEntryDto>()
        var total = 0
        var page = 0

        while (page == 0 || allChapters.size < total) {
            val response = client.newCall(GET("$baseUrl/api/comic/$mangaId/chapters?page=$page", headers)).execute()
            val pageDto = response.parseAs<ChapterListDto>()
            total = pageDto.total
            allChapters.addAll(pageDto.chapters)
            response.close()
            page++
        }

        allChapters.map { it.toSChapter("comic/$mangaId") }
    }

    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()
    // =============================== Pages ===============================

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl/api/${chapter.url}")

    override fun pageListParse(response: Response): List<Page> = response.parseAs<List<PageEntryDto>>().mapIndexed { index, p ->
        Page(index, imageUrl = "https://$pageCdnHost/${p.path}")
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val pageHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, pageHeaders)
    }

    // ============================== Filters ==============================

    private var cachedFilters: Pair<List<FilterDto>, List<FilterDto>>? = null

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Filtering is ignored when searching by text."),
            SortFilter(),
            StatusFilter(),
        )
        if (cachedFilters == null) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Press 'reset' after search to show additional filters."),
            )
        } else {
            cachedFilters?.let { (genres, authors) ->
                if (genres.isNotEmpty()) filters += GenreFilter("Genres", genres.map { it.name to it.id })
                if (authors.isNotEmpty()) filters += AuthorFilter("Authors", authors.map { it.name to it.id })
            }
        }

        return FilterList(filters)
    }

    // ============================= Utilities =============================

    private fun fetchJson(api: String, page: Int, sort: String, query: String? = null): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(api)
            .apply {
                if (page != 0) addQueryParameter("page", page.toString())
                if (sort.isNotEmpty()) addQueryParameter("sort", sort)
            }
            .build()
        val refHeaders = apiHeaders.newBuilder().apply {
            set("Referer", url.newBuilder().removePathSegment(0).build().toString())
        }.build()

        return if (query == null) {
            GET(url, refHeaders)
        } else {
            val body = SearchRequestBody(search = query).toJsonRequestBody()
            POST(url.toString(), refHeaders, body)
        }
    }

    private fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("ongoing", "publishing").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        this.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        this.contains("completed", ignoreCase = true) -> SManga.COMPLETED
        listOf("dropped", "cancelled").any { this.contains(it, ignoreCase = true) } -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun Element.getInfo(name: String): String? = selectFirst("div:has(>span:matches($name:))")?.ownText()

    private fun Element.getLinks(name: String): String? = select("div:has(>span:matches($name:)) a")
        .joinToString(transform = Element::text)
        .takeIf { it.isNotEmpty() }
}
