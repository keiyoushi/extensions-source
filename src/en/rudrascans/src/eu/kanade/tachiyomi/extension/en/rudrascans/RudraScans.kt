package eu.kanade.tachiyomi.extension.en.rudrascans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class RudraScans : ParsedHttpSource() {

    override val name = "Rudra Scans"

    override val baseUrl = "https://rudrascans.com"

    override val lang = "en"

    override val supportsLatest = true

    override val client = super.client.newBuilder()
        .rateLimit(1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // Popular

    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaSelector(): String = "div.flex-col div.grid > div.group.border"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.getImageUrl("*[style*=background-image]")
        element.selectFirst("a[href]")!!.run {
            title = attr("title")
            setUrlWithoutDomain(attr("href"))
        }
    }

    override fun popularMangaNextPageSelector(): String? = null

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest/", headers)

    override fun latestUpdatesSelector(): String = "div.grid > div.group"

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("series")
            addPathSegment("")
            addQueryParameter("q", query)
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "#searched_series_page > a"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.getImageUrl("*[style*=background-image]")
        title = element.attr("title")
        setUrlWithoutDomain(element.attr("href"))
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.use { it.asJsoup() }
        val query = response.request.url.queryParameter("q")!!

        val mangaList = document.select(searchMangaSelector())
            .map(::searchMangaFromElement)
            .filter { it.title.contains(query, true) }

        return MangasPage(mangaList, false)
    }

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("div.grid > h1")!!.text()
        thumbnail_url = document.getImageUrl("div[class*=photoURL]")
        description = document.selectFirst("div.grid > div.overflow-hidden > p")?.text()
        status = document.selectFirst("div[alt=Status]").parseStatus()
        genre = document.select("div.grid:has(>h1) > div.flex > div.leading-none:not([alt])").joinToString(", ") {
            it.text().trim()
        }
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.trim()) {
        "ongoing" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    // Chapter list

    override fun chapterListSelector(): String = "#chapters > a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a[href]")!!.attr("href"))
        name = element.selectFirst(".text-sm")!!.text()
        element.selectFirst(".text-xs")?.run {
            date_upload = text().trim().parseDate()
        }
    }

    // Image list

    override fun pageListParse(document: Document): List<Page> {
        return document.select("#pages > img").map {
            val index = it.attr("count").toInt()
            Page(index, document.location(), it.imgAttr("150"))
        }
    }

    override fun imageUrlParse(document: Document) = ""

    // Utilities

    // From mangathemesia
    private fun Element.imgAttr(width: String): String {
        val url = when {
            hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
            hasAttr("data-src") -> attr("abs:data-src")
            else -> attr("abs:src")
        }
        return url.toHttpUrl()
            .newBuilder()
            .addQueryParameter("w", width)
            .build()
            .toString()
    }

    private fun Element.getImageUrl(selector: String): String? {
        return this.selectFirst(selector)?.let {
            it.attr("style")
                .substringAfter(":url(", "")
                .substringBefore(")", "")
                .takeIf { it.isNotEmpty() }
                ?.toHttpUrlOrNull()?.let {
                    it.newBuilder()
                        .setQueryParameter("w", "480")
                        .build()
                        .toString()
                }
        }
    }

    private fun String.parseDate(): Long {
        return runCatching { DATE_FORMATTER.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    companion object {
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("MMM d, yyyy", Locale.ENGLISH)
        }
    }
}
