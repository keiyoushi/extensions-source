package eu.kanade.tachiyomi.extension.all.toomics

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParseDate
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.net.URLDecoder
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.util.Locale
import kotlin.time.Duration.Companion.minutes

@Source
abstract class ToomicsGlobal : KeiSource() {

    private val siteLang: String
        get() = when (lang) {
            "zh-Hans" -> "sc"
            "zh-Hant" -> "tc"
            "es-419" -> "mx"
            "pt-BR" -> "por"
            else -> lang
        }

    private val dateFormat: DateTimeFormatter get() = when (lang) {
        "zh-Hans" -> dateFormatter("yyyy.M.d", Locale.SIMPLIFIED_CHINESE)
        "zh-Hant" -> dateFormatter("yyyy.M.d", Locale.TRADITIONAL_CHINESE)
        "es-419" -> dateFormatter("d MMM, yyyy", Locale.forLanguageTag("es-419"))
        "es" -> dateFormatter("d MMM, yyyy", Locale.forLanguageTag("es-419"))
        "it" -> dateFormatter("d MMM, yyyy", Locale.ITALIAN)
        "de" -> dateFormatter("d. MMM yyyy", Locale.GERMAN)
        "fr" -> dateFormatter("d MMM. yyyy", Locale.ENGLISH)
        "pt-BR" -> dateFormatter("d 'de' MMM 'de' yyyy", Locale.forLanguageTag("pt-BR"))
        else -> dateFormatter("MMM d, yyyy", Locale.ENGLISH)
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = connectTimeout(1.minutes)
        .readTimeout(1.minutes)
        .writeTimeout(1.minutes)

    override fun Headers.Builder.configureHeaders(): Headers.Builder = set("Referer", "$baseUrl/$siteLang")
        // Required: Prevents Toomics from returning the mobile layout, which breaks all CSS selectors.
        .set("User-Agent", USER_AGENT)

    // ================================== Popular =======================================

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/$siteLang/webtoon/ranking").asJsoup())

    // ================================== Latest =======================================

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/$siteLang/webtoon/new_comics").asJsoup())

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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val formBody = FormBody.Builder()
            .add("toonData", query)
            .build()
        val searchDto = client.post("$baseUrl/$siteLang/webtoon/ajax_search", body = formBody).parseAs<SearchDto>()
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

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val header = document.selectFirst("#glo_contents section.relative:has(img[src*=thumb])")
            ?: throw Exception("Could not find manga details header")

        manga.apply {
            title = header.selectFirst("h2")?.text() ?: throw Exception("Could not find manga title")

            val creators = header.select("a[href*='searchtype=author']").map { it.text() }
            if (creators.isNotEmpty()) {
                artist = creators.first()
                author = creators.last()
            }

            val genres = header.selectFirst("h2 + div li:nth-child(2)")?.text()?.split("·").orEmpty()
            val tags = header.select("a[data-tag-name]").map { it.attr("data-tag-name") }
            genre = (genres + tags).map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString()
            description = header.selectFirst(".break-noraml.text-xs")?.text()
            thumbnail_url = document.selectFirst("head meta[property='og:image']")?.attr("content")
        }

        // coin-type1 - free chapter, coin-type6 - already read chapter
        val chapterList = document.select("li.normal_ep:has(.coin-type1, .coin-type6)").mapNotNull { element ->
            val num = element.selectFirst("div.cell-num")?.text().orEmpty()
            val numText = if (num.isNotEmpty()) "$num - " else ""
            val title = element.selectFirst("div.cell-title strong")?.ownText().orEmpty()
            val link = element.selectFirst("a")?.attr("onclick") ?: return@mapNotNull null

            SChapter.create().apply {
                name = "$numText$title"
                chapter_number = num.toFloatOrNull() ?: -1f
                date_upload = dateFormat.tryParseDate(element.selectFirst("div.cell-time time")?.text())
                scanlator = "Toomics"
                url = link.substringAfter("href='").substringBefore("'")
            }
        }.reversed()

        return SMangaUpdate(manga, chapterList)
    }

    // ================================== Pages ========================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        var document = client.get(getChapterUrl(chapter)).asJsoup()
        if (document.selectFirst("div.section_age_verif") != null) {
            // Same request the site's "Yes, I'm over 18" button makes; stores adult display mode in the session
            client.get("$baseUrl/$siteLang/index/set_display/?display=A&return=%2F$siteLang").close()
            document = client.get(getChapterUrl(chapter)).asJsoup()
            if (document.selectFirst("div.section_age_verif") != null) {
                throw Exception("Verify age via WebView")
            }
        }

        val url = document.selectFirst("head meta[property='og:url']")?.attr("content").orEmpty()

        return document.select("div[id^=load_image_] img")
            .mapIndexed { i, el -> Page(i, url, imageUrl = el.attr("data-src")) }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Referer", page.url)
        .build()

    // ================================== Utilities ====================================

    private fun dateFormatter(pattern: String, locale: Locale): DateTimeFormatter = DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern(pattern)
        .toFormatter(locale)

    private fun String.clearHtml(): String = this.unicode().replace(ESCAPE_CHAR_REGEX, "")

    private fun String.unicode(): String = UNICODE_REGEX.replace(this) { match ->
        val hex = match.groupValues[1].ifEmpty { match.groupValues[2] }
        val value = hex.toInt(16)
        value.toChar().toString()
    }

    companion object {
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Safari/537.36"
        private val UNICODE_REGEX = "\\\\u([0-9A-Fa-f]{4})|\\\\U([0-9A-Fa-f]{8})".toRegex()
        private val ESCAPE_CHAR_REGEX = """(\\n)|(\\r)|(\\{1})""".toRegex()
    }
}
