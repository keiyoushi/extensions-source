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
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class Honkaiimpact : ParsedHttpSource() {

    override val name = "Honkai Impact 3rd"
    override val baseUrl = "https://manga.honkaiimpact3.com"
    override val lang = "en"
    override val supportsLatest = false

    private var searchQuery = ""
    private val json: Json by injectLazy()

    // Interceptor para filtrar o HTML
    private val filteringInterceptor = Interceptor { chain ->
        val request = chain.request()
        val searchQuery = request.tag(String::class.java) ?: ""
        val response = chain.proceed(request)
        val contentType = response.header("Content-Type")
        if (contentType?.contains("text/html") == true && searchQuery.isNotEmpty()) {
            val originalBody = response.body
            if (originalBody != null) {
                val html = originalBody.string()
                val filteredHtml = filterHtml(html, searchQuery)
                val newBody = ResponseBody.create(contentType.toMediaTypeOrNull(), filteredHtml)
                return@Interceptor response.newBuilder()
                    .body(newBody)
                    .build()
            }
        }
        response
    }

    // Função para filtrar o HTML
    private fun filterHtml(html: String, searchQuery: String): String {
        val document = Jsoup.parse(html)
        document.select("a[href*=book]").forEach { aTag ->
            val title = aTag.selectFirst(".container-title")?.text()?.trim() ?: ""
            if (!title.contains(searchQuery, ignoreCase = true)) {
                aTag.remove()
            }
        }
        return document.outerHtml()
    }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .addInterceptor(filteringInterceptor)
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .retryOnConnectionFailure(true)
        .followRedirects(true)
        .build()

    // Popular
    override fun popularMangaSelector() = "a[href*=book]"
    override fun popularMangaNextPageSelector(): String? = null
    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/book", headers).newBuilder().tag(String::class.java, searchQuery).build()
    override fun popularMangaFromElement(element: Element) = mangaFromElement(element)

    // Latest
    override fun latestUpdatesSelector() = throw UnsupportedOperationException("Latest updates not supported")
    override fun latestUpdatesNextPageSelector(): String? = null
    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException("Latest updates not supported")
    override fun latestUpdatesFromElement(element: Element): SManga {
        throw UnsupportedOperationException("Latest updates not supported")
    }

    // Search
    override fun searchMangaSelector() = "a[href*=book]"
    override fun searchMangaNextPageSelector(): String? = null
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        searchQuery = query.trim()
        return GET("$baseUrl/book", headers).newBuilder().tag(String::class.java, searchQuery).build()
    }
    override fun searchMangaFromElement(element: Element) = mangaFromElement(element)

    private fun mangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        manga.setUrlWithoutDomain(element.attr("href"))
        manga.title = element.select(".container-title").text().trim()
        manga.thumbnail_url = element.select(".container-cover img").attr("abs:src")
        return manga
    }

    // Outros métodos
    override fun mangaDetailsParse(document: Document): SManga {
        val manga = SManga.create()
        manga.thumbnail_url = document.select("img.cover").attr("abs:src")
        manga.description = document.select("div.detail_info1").text().trim()
        manga.title = document.select("div.title").text().trim()
        return manga
    }

    override fun chapterListSelector() = throw UnsupportedOperationException("Chapters not supported")
    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException("Chapters not supported")
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

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.lazy.comic_img").mapIndexed { i, el ->
            Page(i, "", el.attr("data-original"))
        }
    }

    override fun imageUrlParse(document: Document) = ""
}
