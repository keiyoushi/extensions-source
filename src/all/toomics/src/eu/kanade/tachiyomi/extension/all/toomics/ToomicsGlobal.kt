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
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
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
        .addInterceptor(::ageVerificationWarning)
        .addInterceptor(::preventMangaDetailsRedirectToChapterPage)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/$siteLang")
        .add("User-Agent", USER_AGENT)

    // ================================== Popular =======================================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$siteLang/webtoon/ranking", headers)

    override fun popularMangaSelector(): String = "li > div.visual a:has(img)"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.select("h4[class$=title]").first()!!.ownText()
        setUrlWithoutDomain(element.absUrl("href"))
        thumbnail_url = element.selectFirst("img")?.let { img ->
            when {
                img.hasAttr("data-original") -> img.attr("data-original")
                else -> img.attr("src")
            }
        }
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

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val response = client.newCall(searchMangaRequest(page, query, filters)).execute()
        val searchDto = json.decodeFromStream<SearchDto>(response.body.byteStream())
        val document = Jsoup.parseBodyFragment(searchDto.content.clearHtml(), baseUrl)
        val mangas = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        return Observable.just(MangasPage(mangas, false))
    }

    override fun searchMangaSelector(): String = "#search-list-items li"

    override fun searchMangaFromElement(element: Element): SManga = SManga.create().apply {
        title = element.selectFirst("strong")!!.text().trim()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")

        element.selectFirst("a.relative")!!.attr("href").let { href ->
            val toonId = href
                .substringAfter("Base.setFamilyMode('N', '")
                .substringBefore("'")
                .let { url -> URLDecoder.decode(url, "UTF-8") }
                .substringAfter("?toon=")
                .substringBefore("&")

            setUrlWithoutDomain("$baseUrl/$siteLang/webtoon/episode/toon/$toonId")
        }
    }

    override fun searchMangaNextPageSelector(): String? = null

    // ================================== Manga Details ================================

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val header = document.selectFirst("#glo_contents section.relative:has(img[src*=thumb])")!!

        title = header.select("h2").text()
        header.selectFirst(".mb-0.text-xs.font-normal")?.let {
            val info = it.text().trim().split("|")
            artist = info.first()
            author = info.last()
        }

        genre = header.select("dt:contains(genres) + dd").text().replace("/", ",")
        description = header.select(".break-noraml.text-xs").text()
        thumbnail_url = document.select("head meta[property='og:image']").attr("content")
    }

    // ================================== Chapters =====================================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        return super.fetchChapterList(manga)
            .map { it.reversed() }
    }

    // coin-type1 - free chapter, coin-type6 - already read chapter
    override fun chapterListSelector(): String = "li.normal_ep:has(.coin-type1, .coin-type6)"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val num = element.select("div.cell-num").text()
        val numText = if (num.isNotEmpty()) "$num - " else ""

        name = numText + element.select("div.cell-title strong").first()?.ownText()
        chapter_number = num.toFloatOrNull() ?: -1f
        date_upload = parseChapterDate(element.select("div.cell-time time").text())
        scanlator = "Toomics"
        url = element.select("a").attr("onclick")
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

    // ================================== Interceptors =================================

    private fun ageVerificationWarning(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.request.url.pathSegments.any { it.equals("age_verification", true) }) {
            throw IOException("Use WebView for age verification")
        }
        return response
    }

    private fun preventMangaDetailsRedirectToChapterPage(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.request.url != request.url) {
            val newRequest = request.newBuilder()
                .headers(response.request.headers)
                .build()
            response.close()

            return chain.proceed(newRequest)
        }
        return response
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
