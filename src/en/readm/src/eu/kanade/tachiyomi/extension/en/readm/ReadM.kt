package eu.kanade.tachiyomi.extension.en.readm

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.util.Calendar
import java.util.concurrent.TimeUnit

class ReadM : ParsedHttpSource() {

    // Info
    override val name: String = "ReadM"
    override val baseUrl: String = "https://readm.today"
    override val lang: String = "en"
    override val supportsLatest: Boolean = true
    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    private val json: Json by injectLazy()
    // Popular

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/popular-manga/$page", headers)
    override fun popularMangaNextPageSelector(): String = "div.pagination a:contains(»)"
    override fun popularMangaSelector(): String = "div#discover-response li"
    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
        element.select("div.subject-title a").first()!!.apply {
            title = this.text().trim()
            url = this.attr("href")
        }
    }

    // Latest

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-releases/$page", headers)
    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()
    override fun latestUpdatesSelector(): String = "ul.latest-updates > li"
    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        thumbnail_url = element.selectFirst("img")!!.imgAttr()
        element.select("h2 a").first()!!.apply {
            title = this.text().trim()
            url = this.attr("href")
        }
    }

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val formBody = FormBody.Builder()
            .add("dataType", "json")
            .add("phrase", query)

        val searchHeaders = headers.newBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("content-type", "application/x-www-form-urlencoded; charset=UTF-8")
            .build()
        return POST("$baseUrl/service/search", searchHeaders, formBody.build())
    }

    override fun searchMangaNextPageSelector(): String = throw UnsupportedOperationException()
    override fun searchMangaSelector(): String = throw UnsupportedOperationException()
    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()

    override fun searchMangaParse(response: Response) = json.parseToJsonElement(response.body.string()).jsonObject["manga"]?.jsonArray?.map {
        val obj = it.jsonObject
        SManga.create().apply {
            title = obj["title"]!!.jsonPrimitive.content
            url = obj["url"]!!.jsonPrimitive.content
            thumbnail_url = "$baseUrl${obj["image"]!!.jsonPrimitive.content}"
        }
    }.let { MangasPage(it ?: emptyList(), false) }

    // Details

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        thumbnail_url = document.selectFirst("img.series-profile-thumb")!!.imgAttr()
        title = document.select("h1.page-title").text().trim()
        author = document.select("span#first_episode a").text().trim()
        artist = document.select("span#last_episode a").text().trim()
        description = document.select("div.series-summary-wrapper p").text().trim()
        genre = document.select("div.series-summary-wrapper div.item a").joinToString(", ") { it.text().trim() }
        status = parseStatus(document.select("div.series-genres .series-status").firstOrNull()?.ownText())
    }

    private fun parseStatus(element: String?): Int = when {
        element == null -> SManga.UNKNOWN
        listOf("ongoing").any { it.contains(element, ignoreCase = true) } -> SManga.ONGOING
        listOf("completed").any { it.contains(element, ignoreCase = true) } -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector(): String = "div.season_start"
    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        name = element.select("a").text()
        url = element.select("a").attr("href")
        date_upload = parseChapterDate(element.select("td.episode-date").text().trim())
    }

    private fun parseChapterDate(date: String): Long {
        val dateWords: List<String> = date.split(" ")

        if (dateWords.size == 2) {
            val timeAgo = Integer.parseInt(dateWords[0])
            val calendar = Calendar.getInstance()

            when {
                dateWords[1].contains("Minute") -> {
                    calendar.add(Calendar.MINUTE, -timeAgo)
                }
                dateWords[1].contains("Hour") -> {
                    calendar.add(Calendar.HOUR_OF_DAY, -timeAgo)
                }
                dateWords[1].contains("Day") -> {
                    calendar.add(Calendar.DAY_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("Week") -> {
                    calendar.add(Calendar.WEEK_OF_YEAR, -timeAgo)
                }
                dateWords[1].contains("Month") -> {
                    calendar.add(Calendar.MONTH, -timeAgo)
                }
                dateWords[1].contains("Year") -> {
                    calendar.add(Calendar.YEAR, -timeAgo)
                }
            }

            return calendar.timeInMillis
        }

        return 0L
    }

    // Pages

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()
    override fun pageListParse(document: Document): List<Page> = document.select("div.ch-images img").mapIndexed { index, element ->
        Page(index, "", element.imgAttr())
    }

    private fun Element.imgAttr(): String {
        return when {
            this.hasAttr("data-src") -> this.attr("abs:data-src")
            else -> this.attr("abs:src")
        }
    }
}
