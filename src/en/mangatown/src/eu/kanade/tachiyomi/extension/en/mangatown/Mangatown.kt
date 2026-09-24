package eu.kanade.tachiyomi.extension.en.mangatown

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.textOrNull
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Source
abstract class Mangatown : KeiSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("MMM dd,yyyy", Locale.US)

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/directory/0-0-0-0-0-0/$page.htm").asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/latest/$page.htm").asJsoup()
        return parseMangasPage(document)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("name", query)
            .build()
        val document = client.get(url).asJsoup()
        return parseMangasPage(document)
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select("li:has(a.manga_cover)").map { mangaFromElement(it) }
        val hasNextPage = document.selectFirst(
            "div.next-page a.next:not([href^=javascript]), .page-nav a:contains(next):not([href^=javascript])",
        ) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        element.selectFirst("p.title a")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            title = it.text()
        }
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(parseMangaDetails(document), parseChapterList(document))
    }

    private fun parseMangaDetails(document: Document): SManga {
        val infoElement = document.selectFirst("div.article_content")!!

        return SManga.create().apply {
            title = infoElement.selectFirst("h1")!!.text()
            author = infoElement.select("li:has(b:containsOwn(author)) a").joinToString { it.text() }.ifEmpty { null }
            artist = infoElement.select("li:has(b:containsOwn(artist)) a").joinToString { it.text() }.ifEmpty { null }
            status = if (infoElement.selectFirst("div.chapter_content:contains(has been licensed)") != null) {
                SManga.LICENSED
            } else {
                parseStatus(infoElement.selectFirst("li:has(b:containsOwn(status))")?.textOrNull())
            }
            genre = infoElement.select("li:has(b:containsOwn(genre)) a").joinToString { it.text() }.ifEmpty { null }
            description = document.selectFirst("span#show")?.textOrNull()?.removeSuffix("HIDE")
            thumbnail_url = document.selectFirst("div.detail_info img")?.absUrl("src")
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Ongoing", ignoreCase = true) -> SManga.ONGOING
        status.contains("Completed", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("ul.chapter_list li").map { chapterFromElement(it) }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        element.selectFirst("a")!!.let { urlElement ->
            setUrlWithoutDomain(urlElement.absUrl("href"))
            name = "${urlElement.text()} ${element.select("span:not(span.time,span.new)").joinToString(" ") { it.text() }}"
        }
        date_upload = parseDate(element.select("span.time").text())
    }

    private fun parseDate(date: String): Long = when {
        date.contains("Today") -> Calendar.getInstance().timeInMillis
        date.contains("Yesterday") -> Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
        else -> dateFormat.tryParseDate(date)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val elements = document.select("select#top_chapter_list ~ div.page_select option:not(:contains(featured))")
        return if (elements.isNotEmpty()) {
            elements.mapIndexed { i, e ->
                Page(i, url = e.absUrl("value"))
            }
        } else {
            document.select("div#viewer img").mapIndexed { i, e ->
                Page(i, imageUrl = e.absUrl("src"))
            }
        }
    }

    override suspend fun getImageUrl(page: Page): String {
        val document = client.get(page.url).asJsoup()
        return document.selectFirst("div#viewer img")!!.absUrl("src")
    }
}
