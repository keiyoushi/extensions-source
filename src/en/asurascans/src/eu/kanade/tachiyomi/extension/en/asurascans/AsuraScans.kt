package eu.kanade.tachiyomi.extension.en.asurascans

import android.app.Application
import android.content.SharedPreferences
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.Locale

class AsuraScans : ParsedHttpSource() {

    override val name = "Asura Scans"

    override val baseUrl = "https://asuracomic.net"

    private val apiUrl = "https://gg.asuracomic.net/api"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMMM d yyyy", Locale.US)

    private val preferences: SharedPreferences =
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)

    init {
        // remove legacy preferences
        preferences.run {
            if (contains("pref_url_map")) {
                edit().remove("pref_url_map").apply()
            }
            if (contains("pref_base_url_host")) {
                edit().remove("pref_base_url_host").apply()
            }
            if (contains("pref_permanent_manga_url_2_en")) {
                edit().remove("pref_permanent_manga_url_2_en").apply()
            }
        }
    }

    override val client = super.client.newBuilder()
        .rateLimit(1, 3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/series?genres=&status=-1&types=-1&order=rating&page=$page", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/series?genres=&status=-1&types=-1&order=update&page=$page", headers)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()

        if (query.isNotBlank()) {
            url.addQueryParameter("name", query)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = "div.grid > a[href]"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        title = element.selectFirst("div.block > span.block")!!.ownText()
        thumbnail_url = element.selectFirst("img")!!.attr("abs:src")
    }

    override fun searchMangaNextPageSelector() = "div.flex > a.flex.bg-themecolor:contains(Next)"

    override fun getFilterList(): FilterList {
        return super.getFilterList()
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("span.text-xl.font-bold")!!.ownText()
        thumbnail_url = document.selectFirst("img[alt=poster]")!!.attr("abs:src")
        description = document.selectFirst("span.font-medium.text-sm")!!.text()
        author = document.selectFirst("div.grid > div:has(h3:eq(0):containsOwn(Author)) > h3:eq(1)")!!.ownText()
        artist = document.selectFirst("div.grid > div:has(h3:eq(0):containsOwn(Artist)) > h3:eq(1)")!!.ownText()
        genre = document.select("div[class^=space] > div.flex > button.text-white").joinToString { it.ownText() }
        status = parseStatus(document.selectFirst("div.flex:has(h3:eq(0):containsOwn(Status)) > h3:eq(1)")!!.ownText())
    }

    private fun parseStatus(status: String) = when (status) {
        "Ongoing", "Season End" -> SManga.ONGOING
        "Hiatus" -> SManga.ON_HIATUS
        "Completed" -> SManga.COMPLETED
        "Dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "div.scrollbar-thumb-themecolor > a.block"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        name = element.selectFirst("h3:eq(0)")!!.ownText()
        date_upload = try {
            val text = element.selectFirst("h3:eq(1)")!!.ownText()
            val cleanText = text.replace(CLEAN_DATE_REGEX, "$1")
            dateFormat.parse(cleanText)?.time ?: 0
        } catch (_: Exception) {
            0L
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div > img[alt=chapter]").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    companion object {
        val CLEAN_DATE_REGEX = """(\d+)(st|nd|rd|th)""".toRegex()
    }
}
