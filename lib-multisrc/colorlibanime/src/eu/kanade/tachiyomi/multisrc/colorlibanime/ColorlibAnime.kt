package eu.kanade.tachiyomi.multisrc.colorlibanime

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element

abstract class ColorlibAnime(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    private val timeRegex = Regex("""Date\((\d+)\)""")

    private fun Element.toThumbnail(): String = this.select(".set-bg").attr("abs:data-setbg").substringBeforeLast("?")

    // Popular
    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(OrderFilter(0)))

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(page, "", FilterList(OrderFilter(1)))

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addQueryParameter("page", page.toString())
            addQueryParameter("sort", filters.firstInstanceOrNull<OrderFilter>()?.toUriPart() ?: "view")
            addQueryParameter("search", query)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".product__page__content > [style]:has(.col-6) .product__item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.select("a.img-link").attr("abs:href"))
                title = element.select("h5").text()
                thumbnail_url = element.toThumbnail()
            }
        }

        val hasNextPage = document.selectFirst(".fa-angle-right") != null

        return MangasPage(mangas, hasNextPage)
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val element = document.selectFirst(".anime__details__content") ?: return SManga.create()

        return SManga.create().apply {
            title = element.select("h3").text()
            author = element.select("h3 + span").text()
            description = element.select("p").text()
            thumbnail_url = element.toThumbnail()
            status = when (element.select("li:contains(status)").text().substringAfter(" ")) {
                "Ongoing" -> SManga.ONGOING
                "Complete" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoup()

        val time = timeRegex.find(doc.select("script:containsData(lastUpdated)").html())
            ?.let { it.groupValues[1].toLong() } ?: 0L

        return doc.select(".anime__details__episodes a")
            .map { element ->
                SChapter.create().apply {
                    setUrlWithoutDomain(element.attr("abs:href"))
                    name = element.text()
                    date_upload = 0L
                }
            }
            .apply { firstOrNull()?.date_upload = time }
    }

    // Pages
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".container .read-img > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Filters
    override fun getFilterList() = FilterList(
        OrderFilter(),
    )
}
