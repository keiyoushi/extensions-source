package eu.kanade.tachiyomi.extension.tr.mangadenizi

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class MangaDenizi : ParsedHttpSource() {
    override val name = "MangaDenizi"

    override val baseUrl = "https://mangadenizi.com"

    override val lang = "tr"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun popularMangaSelector() = "div.media-left"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga-list?page=$page", headers)

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        title = element.select("img").attr("alt")
        thumbnail_url = element.select("img").attr("abs:src")
    }

    override fun popularMangaNextPageSelector() = "[rel=next]"

    override fun latestUpdatesSelector() = "h3 > a"

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest-release?page=$page", headers)

    // No thumbnail on latest releases page
    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.text()
    }

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector())
            .distinctBy { it.text().trim() }
            .map { latestUpdatesFromElement(it) }
        val hasNextPage = latestUpdatesNextPageSelector().let { selector ->
            document.select(selector).first()
        } != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun searchMangaSelector() = "Unused"

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = GET("$baseUrl/search?query=$query", headers)

    override fun searchMangaNextPageSelector() = "Unused"
    override fun searchMangaFromElement(element: Element) = throw UnsupportedOperationException("Unused")

    override fun searchMangaParse(response: Response): MangasPage {
        val mangaListJson = Json.decodeFromString<SearchMangaJson>(response.body.string())
        val mangaList = mangaListJson.suggestions.map {
            SManga.create().apply {
                title = it.value
                url = "/manga/${it.data}"
            }
        }
        return MangasPage(mangaList, false)
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        description = document.select(".well > p").text()
        genre = document.select("dd > a[href*=category]").joinToString { it.text() }
        status = parseStatus(document.select(".label.label-success").text())
        thumbnail_url = document.select("img.img-responsive").attr("abs:src")
    }

    private fun parseStatus(status: String) = when {
        status.contains("Devam Ediyor") -> SManga.ONGOING
        status.contains("Tamamlandı") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListSelector() = "ul.chapters li"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.select("a").attr("href"))
        name = "${element.select("a").text()}: ${element.select("em").text()}"
        date_upload = try {
            dateFormat.parse(element.select("div.date-chapter-title-rtl").text().trim())?.time ?: 0
        } catch (_: Exception) {
            0
        }
    }

    companion object {
        val dateFormat by lazy {
            SimpleDateFormat("dd MMM. yyyy", Locale.US)
        }
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("img.img-responsive").mapIndexed { i, element ->
            val url = if (element.hasAttr("data-src")) element.attr("abs:data-src") else element.attr("abs:src")
            Page(i, "", url)
        }
    }

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException("Not Used")

    override fun getFilterList() = FilterList()

    @Serializable
    data class SearchMangaJson(
        val suggestions: List<MangaJson>,
    )

    @Serializable
    data class MangaJson(
        val value: String,
        val data: String,
    )
}
