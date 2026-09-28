package eu.kanade.tachiyomi.extension.th.niceoppai

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
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class Niceoppai : KeiSource() {
    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/manga_list/all/any/most-popular-monthly/$page").asJsoup())

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/manga_list/all/any/last-updated/$page").asJsoup())

    // Search
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val orderBy = ORDER_BY_FILTER_OPTIONS_VALUES[filters.firstInstanceOrNull<OrderByFilter>()?.state ?: 0]

        val document = if (query.isBlank()) {
            client.get("$baseUrl/manga_list/all/any/$orderBy/$page").asJsoup()
        } else {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addPathSegments("manga_list/search")
                .addPathSegment(query)
                .addPathSegment(orderBy)
                .addPathSegment(page.toString())
                .build()
            client.get(url).asJsoup()
        }

        return parseMangaList(document)
    }

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select("div.fcard").mapNotNull { element ->
            val link = element.selectFirst("a.fcard__title") ?: return@mapNotNull null
            SManga.create().apply {
                title = link.text()
                setUrlWithoutDomain(link.attr("abs:href"))
                thumbnail_url = element.selectFirst("img.cover__img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.select("ul.pgg li a").any { it.text() == "Next" }
        return MangasPage(mangas, hasNextPage)
    }

    // Deeplink
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host.removePrefix("www.") != baseUrl.toHttpUrl().host.removePrefix("www.")) return null

        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val slug = segments.firstOrNull()?.takeIf { segments.size <= 2 && it != "manga_list" } ?: return null

        return parseMangaDetails(client.get("$baseUrl/$slug/").asJsoup()).apply { initialized = true }
    }

    // Details + Chapters
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return SMangaUpdate(
            manga = if (fetchDetails) parseMangaDetails(document) else manga,
            chapters = if (fetchChapters) parseChapterList(document) else chapters,
        )
    }

    private fun parseMangaDetails(document: Document): SManga {
        val info = document.selectFirst("div.series__info") ?: throw Exception("Manga details not found")

        return SManga.create().apply {
            setUrlWithoutDomain(document.location())
            title = info.selectFirst("h1")?.text() ?: throw Exception("Manga title not found")
            author = info.fact("ผู้แต่ง")?.selectFirst("b a")?.text()
            artist = author
            status = when (info.fact("สถานะ")?.selectFirst("b")?.text()) {
                "ยังไม่จบ" -> SManga.ONGOING
                "จบแล้ว" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
            genre = info.select("div.series__genres a.chip--genre").joinToString { it.text() }
            description = info.selectFirst("p.series__syn")?.text()
            thumbnail_url = document.selectFirst("div.series__cover img.cover__img")?.attr("abs:src")
        }
    }

    private fun Element.fact(label: String): Element? = select("div.fact").firstOrNull { it.selectFirst("span")?.text() == label }

    private suspend fun parseChapterList(document: Document): List<SChapter> {
        val pageUrls = document.select("ul.pgg li a")
            .filter { it.text().toIntOrNull() != null }
            .map { it.attr("abs:href") }

        if (pageUrls.isEmpty()) return parseChapters(document)

        return coroutineScope {
            pageUrls.map { url ->
                async { parseChapters(client.get(url).asJsoup()) }
            }.awaitAll().flatten()
        }
    }

    private fun parseChapters(document: Document): List<SChapter> = document.select("a.chrow").map { element ->
        val id = element.attr("data-ch")
        val number = CHAPTER_NUMBER_REGEX.find(id)?.value ?: id
        val title = element.selectFirst("div.chrow__t")?.ownText().orEmpty()

        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = if (title.isEmpty() || title == number) "ตอนที่ $number" else "ตอนที่ $number - $title"
            chapter_number = number.toFloatOrNull() ?: -1f
            date_upload = DATE_FORMAT.tryParseDate(element.selectFirst("div.chrow__d")?.text(), ZoneId.of("Asia/Bangkok"))
        }
    }

    // Pages
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select("#image-container > center > img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }
    }

    // Filters
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        OrderByFilter(
            ORDER_BY_FILTER_TITLE,
            ORDER_BY_FILTER_OPTIONS.zip(ORDER_BY_FILTER_OPTIONS_VALUES).toList(),
            0,
        ),
    )

    companion object {
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("MMM dd, yyyy", Locale.US)
        private val CHAPTER_NUMBER_REGEX = Regex("""^\d+(?:\.\d+)?""")
    }
}
