package eu.kanade.tachiyomi.extension.de.mangatube

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class MangaTube : ParsedHttpSource() {

    override val name = "Manga Tube"

    override val baseUrl = "https://manga-tube.me"

    override val lang = "de"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    private val xhrHeaders: Headers = headersBuilder().add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8").build()

    private val json: Json by injectLazy()

    // Popular

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return client.newCall(popularMangaRequest(page))
            .asObservableSuccess()
            .map { response ->
                parseMangaFromJson(response, page < 96)
            }
    }

    override fun popularMangaRequest(page: Int): Request {
        val rbodyContent = "action=load_series_list_entries&parameter%5Bpage%5D=$page&parameter%5Bletter%5D=&parameter%5Bsortby%5D=popularity&parameter%5Border%5D=asc"
        return POST("$baseUrl/ajax", xhrHeaders, rbodyContent.toRequestBody(null))
    }

    // popular uses "success" as a key, search uses "suggestions"
    // for future reference: if adding filters, advanced search might use a different key
    private fun parseMangaFromJson(response: Response, hasNextPage: Boolean): MangasPage {
        var titleKey = "manga_title"
        val mangas = json.decodeFromString<JsonObject>(response.body.string())
            .let { it["success"] ?: it["suggestions"].also { titleKey = "value" } }!!
            .jsonArray
            .map { json ->
                SManga.create().apply {
                    title = json.jsonObject[titleKey]!!.jsonPrimitive.content
                    url = "/series/${json.jsonObject["manga_slug"]!!.jsonPrimitive.content}"
                    thumbnail_url = json.jsonObject["covers"]!!.jsonArray[0].jsonObject["img_name"]!!.jsonPrimitive.content
                }
            }
        return MangasPage(mangas, hasNextPage)
    }

    override fun popularMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun popularMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Latest

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/?page=$page", headers)
    }

    override fun latestUpdatesSelector() = "div#series-updates div.series-update:not([style\$=none])"

    override fun latestUpdatesFromElement(element: Element): SManga {
        return SManga.create().apply {
            element.select("a.series-name").let {
                title = it.text()
                setUrlWithoutDomain(it.attr("href"))
            }
            thumbnail_url = element.select("div.cover img").attr("abs:data-original")
        }
    }

    override fun latestUpdatesNextPageSelector() = "button#load-more-updates"

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val rbodyContent = "action=search_query&parameter%5Bquery%5D=$query"
        return POST("$baseUrl/ajax", xhrHeaders, rbodyContent.toRequestBody(null))
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return parseMangaFromJson(response, false)
    }

    override fun searchMangaSelector() = throw UnsupportedOperationException("Not used")

    override fun searchMangaFromElement(element: Element): SManga = throw UnsupportedOperationException("Not used")

    override fun searchMangaNextPageSelector() = throw UnsupportedOperationException("Not used")

    // Details

    override fun mangaDetailsParse(document: Document): SManga {
        return SManga.create().apply {
            document.select("div.series-detailed div.row").first()!!.let { info ->
                author = info.select("li:contains(Autor:) a").joinToString { it.text() }
                artist = info.select("li:contains(Artist:) a").joinToString { it.text() }
                status = info.select("li:contains(Offiziel)").firstOrNull()?.ownText().toStatus()
                genre = info.select(".genre-list a").joinToString { it.text() }
                thumbnail_url = info.select("img").attr("abs:data-original")
            }
            description = document.select("div.series-footer h4 ~ p").joinToString("\n\n") { it.text() }
        }
    }

    private fun String?.toStatus() = when {
        this == null -> SManga.UNKNOWN
        this.contains("laufend", ignoreCase = true) -> SManga.ONGOING
        this.contains("abgeschlossen", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // Chapters

    override fun chapterListSelector() = "ul.chapter-list li"

    override fun chapterFromElement(element: Element): SChapter {
        return SChapter.create().apply {
            element.select("a[title]").let {
                name = "${it.select("b").text()} ${it.select("span:not(.btn)").joinToString(" ") { span -> span.text() }}"
                setUrlWithoutDomain(it.attr("href"))
            }
            date_upload = element.select("p.chapter-date").text().let {
                try {
                    SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).parse(it.substringAfter(" "))?.time ?: 0L
                } catch (_: ParseException) {
                    0L
                }
            }
        }
    }

    // Pages

    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script:containsData(current_chapter:)").first()!!.data()
        val imagePath = Regex("""img_path: '(.*)'""").find(script)?.groupValues?.get(1)
            ?: throw Exception("Couldn't find image path")
        val jsonArray = Regex("""pages: (\[.*]),""").find(script)?.groupValues?.get(1)
            ?: throw Exception("Couldn't find JSON array")

        return json.decodeFromString<JsonArray>(jsonArray).mapIndexed { i, json ->
            Page(i, "", imagePath + json.jsonObject["file_name"]!!.jsonPrimitive.content)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not used")
}
