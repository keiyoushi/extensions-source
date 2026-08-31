package eu.kanade.tachiyomi.extension.ko.rawdex

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

// The site used to run a Madara theme but moved to custom markup (rdx-* classes),
// so this source cannot live on the madaralegacy theme anymore.
@Source
abstract class RawDEX : KeiSource() {

    override suspend fun getPopularManga(page: Int): MangasPage = fetchBrowsePage("$baseUrl/manga/page/$page/?m_orderby=views")

    override suspend fun getLatestUpdates(page: Int): MangasPage = fetchBrowsePage("$baseUrl/manga/page/$page/?m_orderby=latest")

    // The site ignores m_orderby=popular, views is its de facto most-read ordering.
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .apply { if (page > 1) addQueryParameter("spage", page.toString()) }
            .build()

        return fetchBrowsePage(url.toString())
    }

    private suspend fun fetchBrowsePage(url: String): MangasPage {
        val document = client.get(url, ensureSuccess = false).asJsoup()
        val mangas = document.select("article.rdx-library-card").mapNotNull(::browseMangaFromElement)

        // Pagination links claim more pages than actually exist near the end of the
        // archive; requesting them yields a 404 with zero cards, handled above.
        val hasNextPage = mangas.isNotEmpty() && document.selectFirst("a.next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun browseMangaFromElement(element: Element): SManga? {
        val link = element.selectFirst(".rdx-library-card__body h2 a") ?: return null
        val title = link.text().takeIf { it.isNotEmpty() } ?: return null

        return SManga.create().apply {
            setUrlWithoutDomain(link.absUrl("href"))
            this.title = title
            thumbnail_url = element.selectFirst(".rdx-library-card__cover img")?.attr("abs:src")
        }
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

    private fun parseMangaDetails(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(".rdx-manga-heading h1")?.text()
            ?: error("Failed to parse title")
        thumbnail_url = document.selectFirst("img.rdx-manga-cover")?.attr("abs:src")
        status = when (document.selectFirst(".rdx-manga-status")?.text()?.lowercase()) {
            "on-going" -> SManga.ONGOING
            "end" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        document.select("dl.rdx-manga-meta div").forEach { element ->
            val value = element.selectFirst("dd")?.text().orEmpty()
            when (element.selectFirst("dt")?.text()?.lowercase()) {
                "author" -> author = value
                "artist" -> artist = value
            }
        }

        genre = document.select(".rdx-manga-tags a")
            .joinToString { it.text() }
            .takeIf { it.isNotEmpty() }

        val altNames = document.selectFirst(".rdx-manga-alternative")?.text()
            ?.split('/', ';')
            ?.map(String::trim)
            ?.filter(String::isNotEmpty)
            .orEmpty()

        description = buildString {
            document.selectFirst(".rdx-manga-summary")?.text()
                ?.takeIf { it.isNotEmpty() }
                ?.let(::append)
            if (altNames.isNotEmpty()) {
                if (isNotEmpty()) append("\n\n")
                append(ALT_NAME_PREFIX)
                append("\n")
                altNames.joinTo(this, "\n") { "- $it" }
            }
        }.takeIf(String::isNotEmpty)
    }

    // Chapters render newest first, matching the descending order the app expects.
    private fun parseChapterList(document: Document): List<SChapter> = document.select(".rdx-chapter-list > a.rdx-chapter-row").mapNotNull { element ->
        val name = element.selectFirst(".rdx-chapter-row__label")?.text()
            ?.takeIf { it.isNotEmpty() }
            ?: return@mapNotNull null

        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            this.name = name
            date_upload = element.selectFirst(".rdx-chapter-row__date")?.text()
                ?.let(::parseRelativeDate)
                ?: 0L
            chapter_number = CHAPTER_NUMBER_REGEX.find(name)
                ?.groupValues
                ?.get(1)
                ?.toFloatOrNull()
                ?: -1f
        }
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        return document.select(".rdx-reader-page img").mapIndexed { index, img ->
            Page(index, imageUrl = img.attr("abs:src"))
        }.ifEmpty { error("No pages found") }
    }

    private fun parseRelativeDate(date: String): Long {
        val match = RELATIVE_DATE_REGEX.find(date.trim()) ?: return 0L
        val amount = match.groupValues[1].toLong()
        val unitMillis = when (match.groupValues[2]) {
            "second" -> 1_000L
            "minute" -> 60_000L
            "hour" -> 3_600_000L
            "day" -> 86_400_000L
            "week" -> 604_800_000L
            "month" -> 2_592_000_000L
            "year" -> 31_536_000_000L
            else -> return 0L
        }
        return System.currentTimeMillis() - amount * unitMillis
    }
}

private const val ALT_NAME_PREFIX = "Alternative Names:"

private val CHAPTER_NUMBER_REGEX = Regex("""(\d+(?:\.\d+)?)""")
private val RELATIVE_DATE_REGEX = Regex("""(\d+)\s+(second|minute|hour|day|week|month|year)s?\s+ago""")
