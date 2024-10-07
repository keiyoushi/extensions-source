package eu.kanade.tachiyomi.multisrc.hotcomics

import eu.kanade.tachiyomi.lib.cookieinterceptor.CookieInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

abstract class HotComics(
    final override val name: String,
    final override val lang: String,
    final override val baseUrl: String,
) : HttpSource() {

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .addNetworkInterceptor(
            CookieInterceptor(baseUrl.removePrefix("https://"), "hc_vfs" to "Y"),
        )
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/en", headers)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/en/new", headers)
    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addEncodedPathSegments("en/search")
                addQueryParameter("keyword", query.trim())
            } else {
                val filter = filters.filterIsInstance<BrowseFilter>().first()
                addEncodedPathSegments(filter.selected)
                addQueryParameter("page", page.toString())
            }
        }.build()

        return GET(url, headers)
    }

    abstract class SelectFilter(
        name: String,
        private val options: List<Pair<String, String>>,
    ) : Filter.Select<String>(
        name,
        options.map { it.first }.toTypedArray(),
    ) {
        val selected get() = options[state].second
    }

    abstract val browseList: List<Pair<String, String>>

    class BrowseFilter(browseList: List<Pair<String, String>>) : SelectFilter("Browse", browseList)

    override fun getFilterList() = FilterList(
        Filter.Header("Doesn't work with Text search"),
        Filter.Separator(),
        BrowseFilter(browseList),
    )

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select("li[itemtype*=ComicSeries]:not(.no-comic) > a").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                thumbnail_url = element.selectFirst("div.visual img")?.imgAttr()
                title = element.selectFirst("div.main-text > h4.title")!!.text()
            }
        }.distinctBy { it.url }
        val hasNextPage = document.selectFirst("div.pagination a.vnext:not(.disabled)") != null

        return MangasPage(entries, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()

        title = document.selectFirst("h2.episode-title")!!.text()
        with(document.selectFirst("p.type_box")!!) {
            author = selectFirst("span.writer")?.text()
                ?.substringAfter("ⓒ")?.trim()
            genre = selectFirst("span.type")?.text()
                ?.split("/")?.joinToString { it.trim() }
            status = when (selectFirst("span.date")?.text()) {
                "End", "Ende" -> SManga.COMPLETED
                null -> SManga.UNKNOWN
                else -> SManga.ONGOING
            }
        }
        description = buildString {
            document.selectFirst("div.episode-contents header")
                ?.text()?.let {
                    append(it)
                    append("\n\n")
                }
            document.selectFirst("div.title_content > h2:not(.episode-title)")
                ?.text()?.let { append(it) }
        }.trim()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.asJsoup().select("#tab-chapter a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("onclick").substringAfter("popupLogin('").substringBefore("'"))
                name = element.selectFirst(".cell-num")!!.text()
                date_upload = parseDate(element.selectFirst(".cell-time")?.text())
            }
        }.reversed()
    }

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)

    private fun parseDate(date: String?): Long {
        date ?: return 0L

        return try {
            dateFormat.parse(date)!!.time
        } catch (_: ParseException) {
            0L
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.asJsoup().select("#viewer-img img").mapIndexed { idx, img ->
            Page(idx, imageUrl = img.imgAttr())
        }
    }

    private fun Element.imgAttr(): String {
        return when {
            hasAttr("data-src") -> absUrl("data-src")
            else -> absUrl("src")
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }
}
