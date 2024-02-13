package eu.kanade.tachiyomi.extension.en.manhwaz

import eu.kanade.tachiyomi.multisrc.madara.Madara
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

class ManhwaZ : Madara(
    "ManhwaZ",
    "https://manhwaz.com",
    "en",
) {

    override val client: OkHttpClient = super.client.newBuilder()
        .rateLimit(2)
        .build()

    override val fetchGenres = false

    override val useNewChapterEndpoint = true

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/", headers)

    override fun popularMangaSelector(): String = "div#slide-top > div.item"

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst(".img-item img")?.let(::imageFromElement) ?: ""
        element.selectFirst(".info-item a")!!.run {
            title = text().trim()
            setUrlWithoutDomain(attr("href"))
        }
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/?page=$page", headers)

    override fun latestUpdatesSelector(): String = ".manga-content > div.row > div"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst(".item-thumb img")?.let(::imageFromElement) ?: ""
        element.selectFirst(".item-summary a")!!.run {
            title = text().trim()
            setUrlWithoutDomain(attr("href"))
        }
    }

    override fun latestUpdatesNextPageSelector(): String = "ul.pager > li.active + li:not(.disabled)"

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotBlank()) {
                addPathSegment("search")
                addQueryParameter("s", query)
            } else {
                filters.forEach { filter ->
                    when (filter) {
                        is GenreFilter -> {
                            if (filter.selected == null) throw Exception("Must select a genre")
                            addPathSegment("genre")
                            addPathSegment(filter.selected!!)
                        }
                        is OrderFilter -> {
                            addQueryParameter("m_orderby", filter.selected)
                        }
                        else -> {}
                    }
                }
            }
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return if (response.request.url.encodedPath.startsWith("/search")) {
            searchParse(response)
        } else {
            super.searchMangaParse(response)
        }
    }

    override fun searchMangaSelector(): String = "div.listing > div"

    override fun searchMangaFromElement(element: Element): SManga = latestUpdatesFromElement(element)

    override fun searchMangaNextPageSelector(): String = latestUpdatesNextPageSelector()

    private fun searchParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select(".page-search > .container > .row > div")
            .map(::searchMangaFromElement)

        val hasNextPage = searchMangaNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangaList, hasNextPage)
    }

    // Filter

    abstract class SelectFilter(
        name: String,
        private val options: List<Pair<String, String>>,
        defaultValue: String? = null,
    ) : Filter.Select<String>(
        name,
        options.map { it.first }.toTypedArray(),
        options.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
    ) {
        val selected get() = options[state].second.takeUnless { it.isEmpty() }
    }

    class OrderFilter : SelectFilter(
        "Order By",
        listOf(
            Pair("Latest", "latest"),
            Pair("Rating", "rating"),
            Pair("Most Views", "views"),
            Pair("New", "new"),
        ),
    )

    class GenreFilter : SelectFilter(
        "Genre",
        listOf(
            Pair("<select>", ""),
            Pair("Action", "action"),
            Pair("Adult", "adult"),
            Pair("Adventure", "adventure"),
            Pair("Comedy", "comedy"),
            Pair("Cooking", "cooking"),
            Pair("Detective", "detective"),
            Pair("Doujinshi", "doujinshi"),
            Pair("Drama", "drama"),
            Pair("Ecchi", "ecchi"),
            Pair("Fantasy", "fantasy"),
            Pair("Gender Bender", "gender-bender"),
            Pair("Harem", "harem"),
            Pair("Historical", "historical"),
            Pair("Horror", "horror"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Manga", "manga"),
            Pair("Manhua", "manhua"),
            Pair("Manhwa", "manhwa"),
            Pair("Martial Arts", "martial-arts"),
            Pair("Mature", "mature"),
            Pair("Mecha", "mecha"),
            Pair("Mystery", "mystery"),
            Pair("One shot", "one-shot"),
            Pair("Psychological", "psychological"),
            Pair("Romance", "romance"),
            Pair("School Life", "school-life"),
            Pair("Sci-fi", "sci-fi"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shoujo Ai", "shoujo-ai"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Smut", "smut"),
            Pair("Sports", "sports"),
            Pair("Supernatural", "supernatural"),
            Pair("Tragedy", "tragedy"),
            Pair("Webtoon", "webtoon"),
            Pair("Yaoi", "yaoi"),
            Pair("Yuri", "yuri"),
        ),
    )

    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        GenreFilter(),
        OrderFilter(),
    )

    // Details

    override val mangaDetailsSelectorStatus = ".post-content_item:contains(status) .summary-content"

    override val mangaDetailsSelectorAuthor = ".post-content_item:contains(Author) .summary-content"
}
