package eu.kanade.tachiyomi.extension.ar.mangatek

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class MangaTek : ParsedHttpSource() {

    override val name = "MangaTek"
    override val baseUrl = "https://mangatek.com"
    override val lang = "ar"

    override val supportsLatest = true
    override val client = network.cloudflareClient
    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale("ar"))

    // Popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list?sort=views&page=$page", headers)

    override fun popularMangaSelector() = ".flex-grow .grid a"
    override fun popularMangaNextPageSelector() = "nav a[aria-disabled=false] .fa-chevron-left"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h3").attr("title")
        setUrlWithoutDomain(element.attr("href"))
        thumbnail_url = element.selectFirst("img")?.imgAttr()
    }

    // Latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/manga-list?page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/manga-list".toHttpUrl().newBuilder()
        url.addQueryParameter("search", query)
        url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)

    // Details
    private fun String?.toStatus() = when (this) {
        "مستمر" -> SManga.ONGOING
        "مكتمل" -> SManga.COMPLETED
        "متوقف" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1")!!.text()
        description = document.selectFirst("p.text-base")?.text()
        genre = document
            .selectFirst("p > span:contains(التصنيفات:) + span")
            ?.text()?.replace("،", ",")
        status = document.selectFirst(".flex span.border.rounded")?.text().toStatus()
        thumbnail_url = document.selectFirst("img#mangaCover")?.imgAttr()
        author = document
            .selectFirst("p > span:contains(المؤلف:) + span")
            ?.ownText()
            ?.takeIf { it != "Unknown" }
    }

    // Chapters

    override fun chapterListSelector() = "astro-island[component-url*=MangaChaptersLoader]"

    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesSlug = response.request.url.toString().substringAfterLast("/")
        val mangaChapters = response.asJsoup().select(chapterListSelector()).attr("props")
            .let { Json.parseToJsonElement(it).jsonObject["manga"] }
            ?.jsonArray?.getOrNull(1)?.jsonObject?.get("MangaChapters")
            ?.jsonArray?.getOrNull(1)?.jsonArray ?: return emptyList()

        return mangaChapters.map { item ->
            fun get(k: String) = item.jsonArray[1].jsonObject[k]?.jsonArray?.getOrNull(1)?.jsonPrimitive?.content

            val num = get("chapter_number")!!
            val title = get("title")

            SChapter.create().apply {
                name = title?.takeIf { it.isNotBlank() } ?: "Chapter $num"
                url = "/reader/$seriesSlug/$num"
                date_upload = dateFormat.tryParse(get("created_at"))
            }
        }
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    // Page

    override fun pageListParse(document: Document): List<Page> = document.select(".manga-page img")
        .mapIndexed { i, element ->
            Page(i, "", element.imgAttr())
        }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String = when {
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("'data-url") -> attr("abs:data-url")
        hasAttr("data-zoom-src") -> attr("abs:data-zoom-src")
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }
}
