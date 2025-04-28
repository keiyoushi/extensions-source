package eu.kanade.tachiyomi.extension.en.ohjoysextoy

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

private val MULTI_SPACE_REGEX = "\\s{6,}".toRegex()

class OhJoySexToy : ParsedHttpSource() {

    override val name = "Oh Joy Sex Toy"
    override val baseUrl = "https://www.ohjoysextoy.com"
    override val lang = "en"
    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MM/dd/yyyy", Locale.ENGLISH)

    // Browse

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/category/comic/page/$page/", headers)

    override fun popularMangaSelector(): String = ".comicthumbwrap"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst(".comicarchiveframe > a")!!.absUrl("href"))
        title = element.selectFirst(".comicthumbdate")!!.text().substringBefore(" by")
        thumbnail_url = element.selectFirst(".comicarchiveframe > a > img")?.absUrl("src")
    }

    override fun popularMangaNextPageSelector(): String = ".pagenav-left a"

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector(): String = "#MattsRecentComicsBar > ul > div"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst(".comicarchiveframe > a")!!.absUrl("href"))
        title = element.selectFirst(".comicthumbdate")!!.text().substringBefore(" by")
        thumbnail_url = element.selectFirst(".comicarchiveframe > a > img")?.absUrl("src")
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .build()
        return GET(url, headers)
    }

    override fun searchMangaSelector(): String = "h2.post-title"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("a")!!.text().substringBefore(" by")
    }

    override fun searchMangaNextPageSelector(): String? = null

    // etc

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.selectFirst("meta[property=\"og:image\"]")
            ?.absUrl("content")
        status = SManga.COMPLETED
        title = document.selectFirst("meta[property=\"og:title\"]")!!
            .attr("content")
            .substringBefore(" by")
        author = document.selectFirst("meta[property=\"og:title\"]")
            ?.attr("content")
            ?.substringAfter("by ", "")
        description = parseDescription(document)
        genre = document.select("meta[property=\"article:section\"]:not(:first-of-type)")
            .eachAttr("content")
            .joinToString()
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        setUrlWithoutDomain(
            document.selectFirst("meta[property=\"og:url\"]")!!.absUrl("content"),
        )
    }

    private fun parseDescription(document: Document): String {
        val desc = document.selectFirst("meta[property=\"og:description\"]")
            ?.attr("content")
            ?.split(MULTI_SPACE_REGEX)
            ?.get(0) + "..."

        val authorCredits = document.select(".entry div.ui-tabs div a")
            .joinToString("\n") { link ->
                "${link.text()}: ${link.absUrl("href")}"
            }

        return listOf(desc, authorCredits, "(Full description and credits in WebView)").joinToString("\n\n")
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$baseUrl${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val dateString = document.selectFirst(".post-date")?.text()

        return listOf(
            SChapter.create().apply {
                name = document.title()
                scanlator = document.selectFirst(".post-author a")?.text()
                date_upload = dateFormat.tryParse(dateString)
                setUrlWithoutDomain(response.request.url.toString())
            },
        )
    }

    override fun chapterListSelector(): String = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.comicpane img")
            .mapIndexed { index, img -> Page(index = index, imageUrl = img.absUrl("src")) }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()
}
