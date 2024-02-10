package eu.kanade.tachiyomi.multisrc.colorlibanime

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
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

abstract class ColorlibAnime(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    private fun Element.toThumbnail(): String {
        return this.select(".set-bg").attr("abs:data-setbg").substringBeforeLast("?")
    }

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", filters.findInstance<OrderFilter>()!!.toUriPart())
            addQueryParameter("search", query)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector(): String = ".product__page__content > [style]:has(.col-6) .product__item"

    override fun searchMangaFromElement(element: Element): SManga {
        return SManga.create().apply {
            setUrlWithoutDomain(element.select("a.img-link").attr("abs:href"))
            title = element.select("h5").text()
            thumbnail_url = element.toThumbnail()
        }
    }

    override fun searchMangaNextPageSelector(): String? = ".fa-angle-right"

    // Popular

    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(page, "", FilterList(OrderFilter(0)))
    }

    override fun popularMangaSelector(): String = searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? = searchMangaNextPageSelector()

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return searchMangaRequest(page, "", FilterList(OrderFilter(1)))
    }

    override fun latestUpdatesSelector(): String = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = searchMangaNextPageSelector()

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        document.select(".anime__details__content").let { element ->
            return SManga.create().apply {
                title = element.select("h3").text()
                author = element.select("h3 + span").text()
                description = element.select("p").text()
                thumbnail_url = element.first()?.toThumbnail()
                status = when (element.select("li:contains(status)").text().substringAfter(" ")) {
                    "Ongoing" -> SManga.ONGOING
                    "Complete" -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    // Chapters

    private val timeRegex = Regex("""Date\((\d+)\)""")

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()

        val time = timeRegex.find(doc.select("script:containsData(lastUpdated)").html())
            ?.let { it.groupValues[1].toLong() } ?: 0

        return doc.select(chapterListSelector())
            .map { chapterFromElement(it) }
            .apply { this.first().date_upload = time }
    }

    override fun chapterListSelector(): String = ".anime__details__episodes a"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.text()
            date_upload = 0L
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".container .read-img > img").mapIndexed { i, element ->
            Page(i, "", element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    // Filters
    override fun getFilterList() = FilterList(
        OrderFilter(),
    )

    class OrderFilter(state: Int = 0) : UriPartFilter(
        "Order By",
        arrayOf(
            Pair("Views", "view"),
            Pair("Updated", "updated"),
        ),
        state,
    )

    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        state: Int = 0,
    ) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }

    private inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T
}
