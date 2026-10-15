package eu.kanade.tachiyomi.extension.all.xiutaku

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.randomua.UserAgentType
import keiyoushi.lib.randomua.setRandomUserAgent
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDateTime
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Xiutaku : KeiSource() {
    private val baseUrlHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient() = rateLimit(10, 1.seconds) { it.host == baseUrlHost }

    override fun Headers.Builder.configureHeaders() = setRandomUserAgent(UserAgentType.MOBILE)

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangasPage(client.get("$baseUrl/hot?start=${20 * (page - 1)}").asJsoup())

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".blog > div").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".pagination-next:not([disabled])") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ========================= Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangasPage(client.get("$baseUrl/?start=${20 * (page - 1)}").asJsoup())

    // ========================= Search =========================
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addQueryParameter("search", query)
            addQueryParameter("start", (20 * (page - 1)).toString())
        }.build()

        return parseMangasPage(client.get(url).asJsoup())
    }

    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        if (url.host != "xiutaku.com") return MangasPage(emptyList(), false)

        val document = client.get(url).asJsoup()

        if (document.selectFirst(".article-header") != null) {
            val manga = mangaDetailsParse(document).apply {
                this.url = document.location().toHttpUrl().newBuilder().query(null).build().encodedPath
            }
            return MangasPage(listOf(manga), false)
        }

        return parseMangasPage(document)
    }

    // ========================= Details =========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(baseUrl + manga.url).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst(".article-header")?.text() ?: throw Exception("Title is mandatory")
        description = document.selectFirst(".article-info:not(:has(small))")?.text()
        genre = document.selectFirst(".article-tags")
            ?.select(".tags > .tag")?.joinToString { it.text().removePrefix("#") }
        status = SManga.COMPLETED
        initialized = true
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // ========================= Chapters =========================
    private fun chapterListParse(document: Document): List<SChapter> {
        val dateUploadStr = document.selectFirst(".article-info > small")?.text()?.removePrefix("🕒")
        val dateUpload = DATE_FORMAT.tryParseDateTime(dateUploadStr, ZoneOffset.UTC)
        val maxPage = document.selectFirst(".pagination-list > span:last-child > a")?.text()?.toIntOrNull() ?: 1
        val baseUrlString = document.location().substringBefore("?")

        return (maxPage downTo 1).map { page ->
            SChapter.create().apply {
                url = "$baseUrlString?page=$page".removePrefix(baseUrl)
                name = "Page $page"
                date_upload = dateUpload
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    // ========================= Pages =========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url).asJsoup()
        return document.select(".article-fulltext img").mapIndexed { i, imgEl ->
            Page(i, imageUrl = imgEl.attr("abs:src"))
        }
    }

    // ========================= Utilities =========================
    private fun mangaFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst(".item-content .item-link") ?: throw Exception("Link is mandatory")
        title = link.text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        url = link.attr("abs:href").removePrefix(baseUrl)
    }

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("HH:mm d-M-yyyy", Locale.US)
    }
}
