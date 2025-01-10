package eu.kanade.tachiyomi.extension.all.toomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URLDecoder
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

abstract class ToomicsGlobal(
    private val siteLang: String,
    private val dateFormat: SimpleDateFormat,
    override val lang: String = siteLang,
    displayName: String = "",
) : ParsedHttpSource() {

    override val name = "Toomics (Only free chapters)" + (if (displayName.isNotEmpty()) " ($displayName)" else "")

    override val baseUrl = "https://global.toomics.com"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = super.client.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/$siteLang")
        .add("User-Agent", USER_AGENT)

    // ================================== Popular =======================================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$siteLang/webtoon/ranking", headers)

    override fun popularMangaSelector(): String = "li > div.visual a:has(img)"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h4[class$=title]").first()!!.ownText()

        thumbnail_url = element.selectFirst("img")?.let { img ->
            when {
                img.hasAttr("data-original") -> img.attr("data-original")
                else -> img.attr("src")
            }
        }
        // The path segment '/search/Y' bypasses the age check and prevents redirection to the chapter
        setUrlWithoutDomain("${element.absUrl("href")}/search/Y")
    }

    override fun popularMangaNextPageSelector(): String? = null

    // ================================== Latest =======================================

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/$siteLang/webtoon/new_comics", headers)

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String? = null

    // ================================== Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val formBody = FormBody.Builder()
            .add("toonData", query)
            .build()
        return POST("$baseUrl/$siteLang/webtoon/ajax_search", headers, formBody)
    }

    override fun searchMangaSelector(): String = "#search-list-items li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("strong")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")

        element.selectFirst("a.relative")!!.attr("href").let {
            val href = it.substringAfter("Base.setFamilyMode('N', '").substringBefore("'")
            val url = when {
                href.contains(baseUrl, true) -> href.toHttpUrl()
                else -> "$baseUrl${URLDecoder.decode(href, "UTF-8")}".toHttpUrl()
            }
            // The path segment '/search/Y' bypasses the age check and prevents redirection to the chapter
            setUrlWithoutDomain("$baseUrl/$siteLang/webtoon/episode/toon/${url.queryParameter("toon")}/search/Y")
        }
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun searchMangaParse(response: Response): MangasPage {
        val searchDto = json.decodeFromStream<SearchDto>(response.body.byteStream())
        val document = Jsoup.parseBodyFragment(searchDto.content.clearHtml(), baseUrl)
        val mangas = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        return MangasPage(mangas, false)
    }

    // ================================== Manga Details ================================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val header = document.selectFirst("#glo_contents section.relative:has(img[src*=thumb])")!!

        title = header.selectFirst("h2")!!.text()
        header.selectFirst(".mb-0.text-xs.font-normal")?.let {
            val info = it.text().split("|")
            artist = info.first()
            author = info.last()
        }

        genre = header.selectFirst("dt:contains(genres) + dd")?.text()?.replace("/", ",")
        description = header.selectFirst(".break-noraml.text-xs")?.text()
        thumbnail_url = document.selectFirst("head meta[property='og:image']")?.attr("content")
    }

    // ================================== Chapters =====================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return super.fetchChapterList(manga)
            .map { it.reversed() }
    }

    // coin-type1 - free chapter, coin-type6 - already read chapter
    override fun chapterListSelector(): String = "li.normal_ep:has(.coin-type1, .coin-type6)"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val num = element.selectFirst("div.cell-num")!!.text()
        val numText = if (num.isNotEmpty()) "$num - " else ""

        name = numText + (element.selectFirst("div.cell-title strong")?.ownText() ?: "")
        chapter_number = num.toFloatOrNull() ?: -1f
        date_upload = parseChapterDate(element.select("div.cell-time time").text())
        scanlator = "Toomics"
        url = element.selectFirst("a")!!.attr("onclick")
            .substringAfter("href='")
            .substringBefore("'")
    }

    // ================================== Pages ========================================

    override fun pageListParse(document: Document): List<Page> {
        if (document.select("div.section_age_verif").isNotEmpty()) {
            throw Exception("Verify age via WebView")
        }

        val url = document.select("head meta[property='og:url']").attr("content")

        return document.select("div[id^=load_image_] img")
            .mapIndexed { i, el -> Page(i, url, el.attr("data-src")) }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    // ================================== Utilities ====================================
    @Serializable
    class SearchDto(@SerialName("webtoon") private val html: Html) {
        val content: String get() = html.data

        @Serializable
        class Html(@SerialName("sHtml") val data: String)
    }

    private fun parseChapterDate(date: String): Long {
        return try {
            dateFormat.parse(date)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    fun String.clearHtml(): String {
        return this.unicode().replace(ESCAPE_CHAR_REGEX, "")
    }

    fun String.unicode(): String {
        return UNICODE_REGEX.replace(this) { match ->
            val hex = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val value = hex.toInt(16)
            value.toChar().toString()
        }
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36"
        val UNICODE_REGEX = "\\\\u([0-9A-Fa-f]{4})|\\\\U([0-9A-Fa-f]{8})".toRegex()
        val ESCAPE_CHAR_REGEX = """(\\n)|(\\r)|(\\{1})""".toRegex()
    }
}
