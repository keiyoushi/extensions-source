package eu.kanade.tachiyomi.extension.en.mangarok

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaRok : ParsedHttpSource() {
    override val name = "MangaRok"

    override val baseUrl = "https://mangarok.mobi"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("yyy/MM/dd", Locale.US)

    private val floatPattern = Regex("""\d+(?:\.\d+)?""")

    // Popular
    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/category/all", headers)

    override fun popularMangaSelector(): String =
        searchMangaSelector()

    override fun popularMangaFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun popularMangaNextPageSelector(): String? =
        searchMangaNextPageSelector()

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())

        return GET(url.toString(), headers)
    }
    override fun searchMangaSelector(): String =
        ".is-half > a.box"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        title = element.selectFirst(".mtitle")!!.ownText()
        thumbnail_url = element.selectFirst("img")!!.attr("abs:data-src")

        genre = element.select(".tag.is-small").joinToString { it.text() }
        status = element.selectFirst(".msub")!!.text().substringBefore(" ").toStatus()
    }

    override fun searchMangaNextPageSelector(): String =
        ".buttons > a[rel=next]:not([disabled])"

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/category/latest", headers)

    override fun latestUpdatesSelector(): String =
        searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String =
        searchMangaNextPageSelector()

    // Details
    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.title")!!.text()
        thumbnail_url = document.selectFirst("img.athumbnail")!!.attr("abs:data-src")

        val table = document.selectFirst(".table:not(.is-hoverable)")!!
        artist = table.selectFirst("tr > td:first-child:contains(Artist:) + td > a")?.text()
        author = table.selectFirst("tr > td:first-child:contains(Author:) + td > a")?.text()

        val altNames = table.select("tr > td:first-child:contains(Alt names:) + td > span")
            .map { it.text().trimEnd(',') }

        description = (document.select("div.content")[1].selectFirst("p")?.text() ?: "") +
            (altNames.takeIf { it.isNotEmpty() }?.let { "\n\nAlt name(s): ${it.joinToString()}" } ?: "")

        // Includes "Genre", "Demographic", and "Content"
        genre = table.select("tr > td:first-child:contains(Genre:) + td > span")
            .joinToString { it.text() }
    }

    // Chapters
    override fun chapterListSelector(): String =
        "table.is-hoverable > tbody > tr"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val a = element.selectFirst("a")!!
        setUrlWithoutDomain(a.attr("abs:href"))
        name = a.text()

        val dateString = element.selectFirst("td:nth-child(2)")!!.text()
        date_upload = dateFormat
            .parse(dateString)
            ?.time ?: 0L

        chapter_number = floatPattern.find(name)?.value?.toFloatOrNull() ?: -1f
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        return document.select("p > img.lzl").mapIndexed { i, img ->
            Page(i, "", img.attr("abs:data-src"))
        }
    }

    override fun imageUrlParse(document: Document): String =
        throw UnsupportedOperationException("Not used.")

    // Filters
    override fun getFilterList(): FilterList = FilterList()

    // Other
    private fun String.toStatus(): Int = when (this) {
        "Completed" -> SManga.COMPLETED
        "Ongoing" -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }
}
