package eu.kanade.tachiyomi.extension.tr.trmanga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.getValue

class trmanga : ParsedHttpSource() {

    override val name = "trmanga"

    override val baseUrl = "https://www.trmanga.com"

    override val lang = "tr"

    private val dateFormat = SimpleDateFormat("dd MMMM, yy", Locale.ENGLISH)

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient
//    override val client = network.cloudflareClient.newBuilder()
//        .addInterceptor(DataImageInterceptor())
//        .build()

    override fun headersBuilder() = super.headersBuilder()
//        .add("Referer", "$baseUrl/").add("X-Authcache", "1",s)

    // popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/webtoon-listesi?sort=views&short_type=DESC&page=$page", headers)

    override fun popularMangaSelector() = "div.row>div.col-xl-4"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a[class]")!!.absUrl("href"))
        title = element.selectFirst("a[class]")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
        url
    }

    // TODO: add page selectors & url parameters when site have enough series for pagination
    override fun popularMangaNextPageSelector() = "a.page-link:contains(Sonraki)"

    // latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/webtoon-listesi?sort=released&short_type=DESC&page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    // search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (!query.startsWith(SLUG_SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val url = "/series/${query.substringAfter(SLUG_SEARCH_PREFIX)}"
        return fetchMangaDetails(SManga.create().apply { this.url = url })
            .map {
                it.url = url
                MangasPage(listOf(it), false)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("search", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()


    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    // manga details
    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            title = document.selectFirst(".title")!!.text()
            author = document.select(".author > span:nth-child(2)").text()
            artist = author
            status = document.select(".status > span:nth-child(2)").text().parseStatus()
            description = document.select(".summary p").text()
            thumbnail_url = document.select("div.series-image img").attr("abs:src")
        }
    }

    // chapter list
    override fun chapterListSelector() = "tbody tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            name = it.text()
        }
        date_upload = parseDate(element.select("div > div > span:nth-child(2)").text())
    }

    private val json: Json by injectLazy()

    // page list
    override fun pageListParse(document: Document): List<Page> {
        val imgUrlArray = document.selectFirst("script:containsData(paths)")!!.data()
            .substringAfter("paths\":").substringBefore(",\"count_p")
        return json.parseToJsonElement(imgUrlArray).jsonArray
            .map { it.jsonPrimitive.content }
            .filter(URL_REGEX::matches)
            .mapIndexed { i, imageUrl ->
                Page(i, "", imageUrl)
            }
    }
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private val statusOngoing = listOf("ongoing", "devam ediyor")
    private val statusCompleted = listOf("complete", "tamamlandı", "bitti")

    private fun String.parseStatus(): Int {
        return when (this.lowercase()) {
            in statusOngoing -> SManga.ONGOING
            in statusCompleted -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

//    private fun Element.imgAttr(): String = when {
//        hasAttr("data-pagespeed-high-res-src") -> dataImageAsUrl("data-pagespeed-high-res-src")
//        hasAttr("data-pagespeed-lazy-src") -> dataImageAsUrl("data-pagespeed-lazy-src")
//        else -> dataImageAsUrl("src")
//    }

    private fun parseDate(dateStr: String) =
        runCatching { dateFormat.parse(dateStr)!!.time }
            .getOrDefault(0L)

    companion object {
        const val SLUG_SEARCH_PREFIX = "slug:"
        val dateFormat = SimpleDateFormat("d MMM yyyy", Locale.US)
        val URL_REGEX = """^https?://[^\s/$.?#].[^\s]*$""".toRegex()
    }
}
