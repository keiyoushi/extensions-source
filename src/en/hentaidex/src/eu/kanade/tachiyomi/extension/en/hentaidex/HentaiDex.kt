package eu.kanade.tachiyomi.extension.en.hentaidex

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

class HentaiDex : ParsedHttpSource() {

    override val name = "HentaiDex"

    override val baseUrl = "https://dexhentai.com"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.US)

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1, 3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/manga?order=popular&page=$page", headers)
    override fun popularMangaSelector() = searchMangaSelector()
    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)
    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/manga?order=update&page=$page", headers)
    override fun latestUpdatesSelector() = searchMangaSelector()
    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)
    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga".toHttpUrl().newBuilder().apply {
            filters.forEach { filter ->
                when (filter) {
                    is GenreFilter -> {
                        filter.state.forEach {
                            if (it.isIncluded()) addQueryParameter("genre[]", it.value)
                            if (it.isExcluded()) addQueryParameter("genre[]", "-${it.value}")
                        }
                    }
                    is SortFilter -> {
                        addQueryParameter("order", filter.getValue())
                    }
                    is TypeFilter -> {
                        addQueryParameter("type", filter.getValue())
                    }
                    is StatusFilter -> {
                        addQueryParameter("status", filter.getValue())
                    }
                    else -> {}
                }
            }
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) addQueryParameter("s", query)
        }
        return GET(url.build(), headers)
    }
    override fun searchMangaSelector() = ".bsx"
    override fun searchMangaNextPageSelector() = ".r"
    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href"))
        with(element.selectFirst("img")!!) {
            title = this.attr("title")
            thumbnail_url = this.absUrl("src")
        }
    }

    // Filters
    override fun getFilterList(): FilterList {
        fetchFilters()

        val filters = buildList {
            addAll(
                listOf(
                    Filter.Header("Filter is ignored when using text search"),
                    SortFilter(
                        "Order by",
                        listOf(
                            Pair("Popular", "popular"),
                            Pair("Updated", "update"),
                            Pair("Added", "latest"),
                            Pair("A-Z", "title"),
                            Pair("Z-A", "titlereverse"),
                        ),
                    ),
                ),
            )
            if (filtersState == FiltersState.FETCHED) {
                add(StatusFilter("Status", statusesList))
                add(TypeFilter("Types", typesList))
                add(GenreFilter("Genres", genresList))
            } else {
                add(Filter.Header("Press 'Reset' to attempt to fetch the filters"))
            }
        }
        return FilterList(filters)
    }

    private var genresList: List<Pair<String, String>> = emptyList()
    private var statusesList: List<Pair<String, String>> = emptyList()
    private var typesList: List<Pair<String, String>> = emptyList()

    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters(document: Document? = null) {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        thread {
            try {
                val doc =
                    document
                        ?: client.newCall(GET("$baseUrl/manga", headers)).execute().asJsoup()
                val genres = doc.select(".genrez > li")
                val status = doc.select("input[name=status]")
                val types = doc.select("input[name=type]")
                genresList = genres.map { it.text() to it.selectFirst(".genre-item ")!!.attr("value") }.distinct()
                statusesList = status.map { it.parent()!!.text() to it.attr("value") }.distinct()
                typesList = types.map { it.parent()!!.text() to it.attr("value") }.distinct()

                filtersState = FiltersState.FETCHED
            } catch (e: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    // Details
    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("[id=titlemove] > .entry-title")!!.ownText()
        description = document.selectFirst(".alternative")?.let {
            "Alternative Names: " + "\n" +
                it.ownText().split("; ").joinToString("\n") { "- $it" }
        }
        author = document.selectFirst(".imptdt:contains(Author) > i")?.ownText()
        artist = document.selectFirst(".imptdt:contains(Artist) > i")?.ownText()
        genre = document.select("[rel=tag]").eachText().joinToString()
        status = when ((document.selectFirst(".imptdt:contains(Status) > i")?.ownText() ?: "")) {
            "Ongoing" -> SManga.ONGOING
            "Hiatus" -> SManga.ON_HIATUS
            "Completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        thumbnail_url = document.selectFirst(".thumb > img")?.absUrl("src")
    }

    // Chapters
    override fun chapterListSelector() = "[data-num]:not(:has([onclick]))"
    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        name = element.selectFirst(".chapternum")!!.ownText()
        date_upload = try {
            val text = element.selectFirst(".chapterdate")!!.ownText()
            dateFormat.parse(text)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
    override fun chapterListParse(response: Response): List<SChapter> = super.chapterListParse(response).reversed()

    // Pages
    private val imageUrlRegex = Regex("src=\"(.+?)\"")
    override fun pageListParse(document: Document): List<Page> {
        val imageUrls = document.select("[title=JSON]").attr("href")
        val raw = client.newCall(GET(imageUrls, headers)).execute().body.string().replace("\\", "")
        return imageUrlRegex.findAll(raw).map { it.groupValues[1] }.toList().mapIndexed { i, imageUrl ->
            Page(i, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }
}
