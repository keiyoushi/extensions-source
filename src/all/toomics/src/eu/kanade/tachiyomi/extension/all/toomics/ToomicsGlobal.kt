package eu.kanade.tachiyomi.extension.all.toomics

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

abstract class ToomicsGlobal(
    private val siteLang: String,
    private val dateFormat: SimpleDateFormat,
    override val lang: String = siteLang,
    displayName: String = "",
) : HttpSource() {

    override val name = "Toomics (Only free chapters)" + (if (displayName.isNotEmpty()) " ($displayName)" else "")

    override val baseUrl = "https://global.toomics.com"

    override val supportsLatest = true

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .connectTimeout(1, TimeUnit.MINUTES)
        .readTimeout(1, TimeUnit.MINUTES)
        .writeTimeout(1, TimeUnit.MINUTES)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "$baseUrl/$siteLang")
        // Required: Prevents Toomics from returning the mobile layout, which breaks all CSS selectors.
        .set("User-Agent", USER_AGENT)

    // ================================== Popular =======================================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/$siteLang/webtoon/ranking", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    // ================================== Latest =======================================

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/$siteLang/webtoon/new_comics", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response.asJsoup())

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("li > div.visual a:has(img)").mapNotNull { element ->
            val title = element.selectFirst("h4[class$=title]")?.ownText() ?: return@mapNotNull null

            SManga.create().apply {
                this.title = title
                thumbnail_url = element.selectFirst("img")?.let { img ->
                    if (img.hasAttr("data-original")) {
                        img.attr("data-original")
                    } else {
                        img.attr("src")
                    }
                }
                // The path segment '/search/Y' bypasses the age check and prevents redirection to the chapter
                setUrlWithoutDomain("${element.absUrl("href")}/search/Y")
            }
        }
        return MangasPage(mangas, false)
    }

    // ================================== Search =======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val formBody = FormBody.Builder()
            .add("toonData", query)
            .build()
        return POST("$baseUrl/$siteLang/webtoon/ajax_search", headers, formBody)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchDto = response.parseAs<SearchDto>()
        val document = Jsoup.parseBodyFragment(searchDto.content.clearHtml(), baseUrl)
        val mangas = document.select("#search-list-items li").mapNotNull { element ->
            val title = element.selectFirst("strong")?.text() ?: return@mapNotNull null
            val anchor = element.selectFirst("a.relative") ?: return@mapNotNull null

            SManga.create().apply {
                this.title = title
                thumbnail_url = element.selectFirst("img")?.absUrl("src")

                val href = anchor.attr("href").substringAfter("Base.setFamilyMode('N', '").substringBefore("'")
                val url = when {
                    href.contains(baseUrl, true) -> href.toHttpUrl()
                    else -> "$baseUrl${URLDecoder.decode(href, "UTF-8")}".toHttpUrl()
                }
                // The path segment '/search/Y' bypasses the age check and prevents redirection to the chapter
                setUrlWithoutDomain("$baseUrl/$siteLang/webtoon/episode/toon/${url.queryParameter("toon")}/search/Y")
            }
        }
        return MangasPage(mangas, false)
    }

    // ================================== Manga Details ================================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            val header = document.selectFirst("#glo_contents section.relative:has(img[src*=thumb])")
                ?: throw Exception("Could not find manga details header")

            title = header.selectFirst("h2")?.text() ?: throw Exception("Could not find manga title")

            header.selectFirst(".mb-0.text-xs.font-normal")?.let {
                val info = it.text().split("|")
                artist = info.first()
                author = info.last()
            }

            genre = header.selectFirst("dt:contains(genres) + dd")?.text()?.replace("/", ",")
            description = header.selectFirst(".break-noraml.text-xs")?.text()
            thumbnail_url = document.selectFirst("head meta[property='og:image']")?.attr("content")
        }
    }

    // ================================== Chapters =====================================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        // coin-type1 - free chapter, coin-type6 - already read chapter
        return document.select("li.normal_ep:has(.coin-type1, .coin-type6)").mapNotNull { element ->
            val num = element.selectFirst("div.cell-num")?.text().orEmpty()
            val numText = if (num.isNotEmpty()) "$num - " else ""
            val title = element.selectFirst("div.cell-title strong")?.ownText().orEmpty()
            val link = element.selectFirst("a")?.attr("onclick") ?: return@mapNotNull null

            SChapter.create().apply {
                name = "$numText$title"
                chapter_number = num.toFloatOrNull() ?: -1f
                date_upload = dateFormat.tryParse(element.selectFirst("div.cell-time time")?.text())
                scanlator = "Toomics"
                url = link.substringAfter("href='").substringBefore("'")
            }
        }.reversed()
    }

    // ================================== Pages ========================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        if (document.selectFirst("div.section_age_verif") != null) {
            throw Exception("Verify age via WebView")
        }

        val url = document.selectFirst("head meta[property='og:url']")?.attr("content").orEmpty()

        return document.select("div[id^=load_image_] img")
            .mapIndexed { i, el -> Page(i, url, imageUrl = el.attr("data-src")) }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    // ================================== Utilities ====================================

    private fun String.clearHtml(): String = this.unicode().replace(ESCAPE_CHAR_REGEX, "")

    private fun String.unicode(): String = UNICODE_REGEX.replace(this) { match ->
        val hex = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val value = hex.toInt(16)
        value.toChar().toString()
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/81.0.4044.122 Safari/537.36"
        private val UNICODE_REGEX = "\\\\u([0-9A-Fa-f]{4})|\\\\U([0-9A-Fa-f]{8})".toRegex()
        private val ESCAPE_CHAR_REGEX = """(\\n)|(\\r)|(\\{1})""".toRegex()
    }
}
