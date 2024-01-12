package eu.kanade.tachiyomi.extension.zh.colamanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Colamanga : ParsedHttpSource() {

    override val name = "Cola漫画"

    override val baseUrl = "https://www.colamanga.com"

    override val lang = "zh"

    override val supportsLatest = false

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    private val json: Json by injectLazy()

    override fun popularMangaSelector() = "li.fed-list-item"

    override fun latestUpdatesSelector() = ""

    override fun searchMangaSelector() = ""

    override fun chapterListSelector() = "div.all_data_list li.fed-padding"

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaNextPageSelector(): String? = null

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/show", headers)

    override fun latestUpdatesRequest(page: Int) = throw Exception("Not Used")

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = throw Exception("No search")

    override fun chapterListRequest(manga: SManga) = GET(baseUrl + manga.url, headers)

    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    override fun latestUpdatesFromElement(element: Element) = mangaFromElement(element)

    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    private fun mangaFromElement(element: Element): SManga {
        println("mangaFromElement")
        println(element)
        val manga = SManga.create()
        manga.url = element.select("a.fed-list-title").attr("href")
        manga.title = element.select("a.fed-list-title").text().trim()
        manga.thumbnail_url = element.select("a.fed-list-pics").attr("data-original")
        return manga
    }

    override fun chapterFromElement(element: Element): SChapter {
        println("chapterFromElement")
        println(element)
        return SChapter.create().apply {
            name = element.select("a.fed-btns-info").text().trim()
            url = element.select("a.fed-btns-info").attr("href")
        }
    }

    private fun parseDate(date: String): Long {
        return SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).parse(date)?.time ?: 0L
    }

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        println("mangaDetailsParse")
        println(document)
        thumbnail_url = document.select("dt.fed-deta-images a.fed-list-pics").attr("data-original")
        description = document.select("p.fed-part-both").text().trim()
        title = document.select("h1").text().trim()
    }

    override fun pageListParse(document: Document): List<Page> {
        println("pageListParse")
        println(document)
        return document.select("img.lazy.comic_img").mapIndexed { i, el ->
            Page(i, "", el.attr("data-original"))
        }
    }

    override fun imageUrlParse(document: Document) = ""
}
