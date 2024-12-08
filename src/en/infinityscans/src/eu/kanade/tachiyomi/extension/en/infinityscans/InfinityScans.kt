package eu.kanade.tachiyomi.extension.en.infinityscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy

class InfinityScans : HttpSource() {

    override val name = "InfinityScans"

    override val baseUrl = "https://infinityscans.net"
    private val cdnHost = "cdn.infinityscans.net"

    override val lang = "en"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(WebviewInterceptor(baseUrl))
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder().apply {
        add("Referer", "$baseUrl/")
    }

    private val apiHeaders = headersBuilder().apply {
        add("Accept", "*/*")
        add("Sec-Fetch-Dest", "empty")
        add("Sec-Fetch-Mode", "cors")
        add("Sec-Fetch-Site", "same-origin")
        add("X-requested-with", "XMLHttpRequest")
    }.build()

    private val json: Json by injectLazy()

    // Popular

    override fun popularMangaRequest(page: Int): Request = fetchJson("api/ranking")

    override fun popularMangaParse(response: Response): MangasPage {
        val data = response.parseAs<ResponseDto<RankingResultDto>>().result
        val entries = data.weekly
            .map { it.toSManga(cdnHost) }

        return MangasPage(entries, false)
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = fetchJson("api/comics")

    override fun latestUpdatesParse(response: Response): MangasPage {
        val data = response.parseAs<ResponseDto<SearchResultDto>>().result
        runCatching { updateFilters(data) }

        val entries = data.titles.sortedByDescending { it.updated }
            .map { it.toSManga(cdnHost) }

        return MangasPage(entries, false)
    }

    // Search

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return client.newCall(fetchJson("api/comics"))
            .asObservableSuccess()
            .map { response ->
                val data = response.parseAs<ResponseDto<SearchResultDto>>().result
                runCatching { updateFilters(data) }
                var titles = data.titles

                if (query.isNotBlank()) {
                    titles = titles.filter { it.title.contains(query, ignoreCase = true) }
                }

                filters.forEach { filter ->
                    when (filter) {
                        is SortFilter -> {
                            when (filter.selected) {
                                "title" -> {
                                    titles = titles.sortedBy { it.title }
                                }
                                "popularity" -> {
                                    titles = titles.sortedByDescending { it.all_views }
                                }
                                "latest" -> {
                                    titles = titles.sortedByDescending { it.updated }
                                }
                            }
                        }

                        is GenreFilter -> {
                            filter.checked?.also {
                                titles = titles.filter { it.genres?.split(",")?.any { genre -> genre in filter.checked!! } ?: true }
                            }
                        }

                        is AuthorFilter -> {
                            filter.checked?.also {
                                titles = titles.filter { it.authors?.split(",")?.any { author -> author in filter.checked!! } ?: true }
                            }
                        }

                        is StatusFilter -> {
                            filter.checked?.also {
                                titles = titles.filter { filter.checked!!.any { status -> status == it.status } }
                            }
                        }

                        else -> { /* Do Nothing */
                        }
                    }
                }

                val entries = titles.map { it.toSManga(cdnHost) }

                MangasPage(entries, false)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException()
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    private fun fetchJson(api: String): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegments(api)

        val searchHeaders = apiHeaders.newBuilder().apply {
            set("Referer", url.build().newBuilder().removePathSegment(0).build().toString())
        }.build()

        return GET(url.build(), searchHeaders)
    }

    // Filters

    private fun updateFilters(data: SearchResultDto) {
        data.genres?.also { genreDto ->
            genreList = genreDto.map { Pair(it.title, it.id.toString()) }
        }

        data.authors?.also { authorDto ->
            authorList = authorDto.map { Pair(it.title, it.id.toString()) }
        }

        data.statuses?.also { status ->
            statusList = status.map { Pair(it, it) }
        }
    }

    private var genreList: List<Pair<String, String>> = emptyList()
    private var authorList: List<Pair<String, String>> = emptyList()
    private var statusList: List<Pair<String, String>> = emptyList()

    override fun getFilterList(): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
        )

        if (genreList.isNotEmpty() || authorList.isNotEmpty() || statusList.isNotEmpty()) {
            if (genreList.isNotEmpty()) filters += listOf(GenreFilter("Genres", genreList))
            if (authorList.isNotEmpty()) filters += listOf(AuthorFilter("Authors", authorList))
            if (statusList.isNotEmpty()) filters += listOf(StatusFilter("Statuses", statusList))
        } else {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Press 'reset' to attempt to show additional filters"),
            )
        }

        return FilterList(filters)
    }

    // Details

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.use { it.asJsoup() }

        val desc = document.select("div:has(>h4:contains(Summary)) p")
            .text()
            .split("</br>")
            .joinToString("\n", transform = String::trim)
            .trim()

        return SManga.create().apply {
            document.selectFirst("div:has(>span:contains(Rank:))")!!.parent()!!.also { details ->
                description = desc
                author = details.getLinks("Authors")
                genre = details.getLinks("Genres")
                status = details.getInfo("Status").parseStatus()

                details.getInfo("Alternative Titles")?.let {
                    description = "$desc\n\nAlternative Title: $it"
                }
            }
        }
    }

    // From mangathemesia
    private fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("ongoing", "publishing").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        this.contains("hiatus", ignoreCase = true) -> SManga.ON_HIATUS
        this.contains("completed", ignoreCase = true) -> SManga.COMPLETED
        listOf("dropped", "cancelled").any { this.contains(it, ignoreCase = true) } -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun Element.getInfo(name: String): String? =
        selectFirst("div:has(>span:matches($name:))")?.ownText()

    private fun Element.getLinks(name: String): String? =
        select("div:has(>span:matches($name:)) a")
            .joinToString(", ", transform = Element::text).trim()
            .takeIf { it.isNotBlank() }

    // Chapters

    override fun chapterListParse(response: Response): List<SChapter> {
        val url = response.request.url
        val slug = url.pathSegments.take(3).joinToString("/", prefix = "/")

        // Create POST request
        val chapterHeaders = apiHeaders.newBuilder().apply {
            add("content-length", "0")
            add("Origin", baseUrl)
            set("Referer", url.toString())
        }.build()

        val chapterListData = client.newCall(
            POST(url.toString(), chapterHeaders),
        ).execute().parseAs<ResponseDto<List<ChapterEntryDto>>>()

        return chapterListData.result.map {
            it.toSChapter(slug)
        }
    }

    // Pages

    override fun pageListParse(response: Response): List<Page> {
        val url = response.request.url

        // Create POST request
        val pageListHeaders = apiHeaders.newBuilder().apply {
            add("content-length", "0")
            add("Origin", baseUrl)
            set("Referer", url.toString())
        }.build()

        val pageListData = client.newCall(
            POST(url.toString(), pageListHeaders),
        ).execute().parseAs<ResponseDto<List<PageEntryDto>>>()

        return pageListData.result.mapIndexed { index, p ->
            Page(index, url.toString(), p.link)
        }
    }

    // Image

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val pageHeaders = headersBuilder().apply {
            add("Accept", "image/avif,image/webp,image/apng,image/svg+xml,image/*,*/*")
            add("Host", page.imageUrl!!.toHttpUrl().host)
        }.build()

        return GET(page.imageUrl!!, pageHeaders)
    }

    // Utilities

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }
}
