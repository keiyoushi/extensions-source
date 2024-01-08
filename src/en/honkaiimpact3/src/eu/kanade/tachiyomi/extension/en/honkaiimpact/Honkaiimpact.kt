package eu.kanade.tachiyomi.extension.en.honkaiimpact

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

// Info - Based of BH3
// This is the english version of the site
class Honkaiimpact : ParsedHttpSource() {

    override val name = "Honkai Impact 3rd"

    override val baseUrl = "https://manga.honkaiimpact3.com"

    override val lang = "en"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    private val json: Json by injectLazy()

    // Popular
    override fun popularMangaSelector() = "a[href*=book]"

    override fun popularMangaNextPageSelector(): String? = null

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/book", headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    // Latest
    override fun latestUpdatesSelector() = throw Exception("Not Used")

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int) = throw Exception("Not Used")

    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    // Search
    override fun searchMangaSelector() = throw Exception("Not Used")

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw Exception("No search")

    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.select("a").attr("abs:href"))
        manga.title = element.select("div.container-title").text().trim()
        manga.thumbnail_url = element.select("img").attr("abs:src")
        return manga
    }

    // Manga Details
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select("img.cover").attr("abs:src")
        manga.description = document.select("div.detail_info1").text().trim()
        manga.title = document.select("div.title").text().trim()
        return manga
    }

    // Chapters
    override fun chapterListSelector() = throw Exception("Not Used")

    override fun chapterFromElement(element: Element) = throw Exception("Not Used")

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url + "/get_chapter", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val jsonResult = json.parseToJsonElement(response.body.string()).jsonArray

        return jsonResult.map { jsonEl -> createChapter(jsonEl.jsonObject) }
    }

    private fun createChapter(jsonObj: JsonObject) = SChapter.create().apply {
        name = jsonObj["title"]!!.jsonPrimitive.content
        url = "/book/${jsonObj["bookid"]!!.jsonPrimitive.int}/${jsonObj["chapterid"]!!.jsonPrimitive.int}"
        date_upload = parseDate(jsonObj["timestamp"]!!.jsonPrimitive.content)
        chapter_number = jsonObj["chapterid"]!!.jsonPrimitive.float
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(date)?.time ?: 0
    }

    // Manga Pages

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.lazy.comic_img").mapIndexed { i, el ->
            Page(i, "", el.attr("data-original"))
        }
    }

    override fun imageUrlParse(document: Document) = ""
}
