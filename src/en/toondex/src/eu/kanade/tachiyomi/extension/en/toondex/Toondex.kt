package eu.kanade.tachiyomi.extension.en.toondex

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

@Source
class Toondex(
    override val name: String = "ToonDex",
    override val lang: String = "en",
    override val baseUrl: String = "https://toondex.xyz",
    override val id: Long = 1L,
) : KeiSource() {

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)
    private val relativeDateSuffix = Regex(
        """\s+(?:(?:\d+\s+)?(?:hours?|days?|weeks?|months?|years?)\s*(?:ago)?|yesterday)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get("$baseUrl/most-view/", Fetch.DOCUMENT).asJsoup()
        return MangasPage(document.select(listingCardSelector).map(::mangaFromElement), false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val path = if (page > 1) "$baseUrl/latest-updates/$page/" else "$baseUrl/latest-updates/"
        val document = client.get(path, Fetch.DOCUMENT).asJsoup()
        val hasNextPage = document.selectFirst(paginationNextSelector) != null
        return MangasPage(document.select(listingCardSelector).map(::mangaFromElement), hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        val hasNextPage = document.selectFirst(paginationNextSelector) != null
        return MangasPage(document.select(listingCardSelector).map(::mangaFromElement), hasNextPage)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val segments = url.pathSegments
        if (url.host != baseUrl.toHttpUrl().host || segments.size < 2 || segments[0] != "comics") return null
        return getManga("/comics/${segments[1]}/")
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate = coroutineScope {
        val details = async {
            if (fetchDetails) parseDetails(client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup()) else manga
        }
        val chapterList = async {
            if (fetchChapters) {
                parseChapters(client.get(baseUrl + manga.url, Fetch.DOCUMENT).asJsoup(), manga.url)
            } else {
                chapters
            }
        }
        SMangaUpdate(details.await(), chapterList.await())
    }

    private suspend fun getManga(mangaUrl: String): SManga = fetchMangaUpdate(SManga.create().apply { setUrlWithoutDomain(mangaUrl) }, emptyList(), true, true).manga

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("h1.text-2xl.font-semibold")?.text().orEmpty()

        val altNames = document.selectFirst("td:contains(Alternative Names:) ~ td span")?.text()?.trim()
        val authors = document.select("td:contains(Authors:) ~ td a[href^=/authors/] span").joinToString(", ") { it.text() }
        val artists = document.select("td:contains(Artists:) ~ td a[href^=/artists/] span").joinToString(", ") { it.text() }
        author = listOf(authors, artists)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")
            .ifBlank { null }

        description = buildString {
            if (!altNames.isNullOrBlank()) append("Alternative names: ").append(altNames).append('\n')
            document.selectFirst("h2:contains(Summary) ~ div p")?.text()?.trim()?.let { append(it) }
        }.trim().ifBlank { null }

        val categories = document.select("td:contains(Categories:) ~ td a[href^=/categories/] span").map { it.text() }
        val genres = document.select("td:contains(Genres:) ~ td a[href^=/genres/] span").map { it.text() }
        genre = (categories + genres).joinToString()

        status = if (document.selectFirst("td:contains(Status:) ~ td a[href^=/ongoing/]") != null) {
            SManga.ONGOING
        } else {
            SManga.COMPLETED
        }

        thumbnail_url = document.selectFirst("div.item-thumb img, img[src*=/media/uploads/]")?.absUrl("src")
        initialized = true
    }

    private fun parseChapters(document: Document, mangaUrl: String): List<SChapter> = document.select("a[href^=$mangaUrl]")
        .mapNotNull { row ->
            // Chapter rows carry a font-medium label; sidebar rows for other comics do not.
            val label = row.selectFirst("div.font-medium") ?: return@mapNotNull null
            if (!row.hasClass("p-3") || row.hasClass("py-1")) return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(row.attr("href"))
                val number = url.substringAfter("chapter-").substringBefore('/').replace('-', '.')
                chapter_number = number.toFloatOrNull() ?: -1f
                name = label.text().trim().replace(relativeDateSuffix, "").ifBlank { "Chapter $number" }
                date_upload = label.nextElementSibling()?.text()?.let {
                    try {
                        dateFormat.parse(it.trim())?.time
                    } catch (e: ParseException) {
                        null
                    }
                } ?: 0L
            }
        }
        .distinctBy { it.url }
        .sortedByDescending { it.chapter_number }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(baseUrl + chapter.url, Fetch.DOCUMENT).asJsoup()
        return document.select("img[id^=row-]").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src").ifEmpty { element.absUrl("data-src") })
        }
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))

        // On the latest-updates grid the title anchor is a sibling of the thumbnail anchor.
        val slug = element.attr("href").substringAfterLast('/').trim('/')
        title = element.selectFirst(".box-title")?.text()
            ?: element.parent()?.nextElementSibling()?.selectFirst(".box-title")?.text()
            ?: slug.replace(Regex("-[a-z0-9]{6}$"), "").replace('-', ' ')

        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    private val listingCardSelector = "a[href*=\"/comics/\"]:has(img)"
    private val paginationNextSelector = "a:has(i.fa-angle-right)"
}
