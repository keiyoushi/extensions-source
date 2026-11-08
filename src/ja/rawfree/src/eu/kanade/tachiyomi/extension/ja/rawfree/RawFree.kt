package eu.kanade.tachiyomi.extension.ja.rawfree

import eu.kanade.tachiyomi.multisrc.zingtheme.GenreGroup
import eu.kanade.tachiyomi.multisrc.zingtheme.ZingTheme
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.utils.asJsoup
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class RawFree : ZingTheme() {

    override val mangaPathSegment = "manga-raw"
    override val chapterIdParam = "p"

    override val mangaListSelector = "div.entry-ma"
    override val mangaLinkSelector = "h2.ma-name a"
    override val nextPageSelector = "a.next.page-numbers"

    override val mangaTitleSelector = "h1.name"
    override val thumbnailSelector = "div.main-thumb img"
    override val descriptionSelector = "div.note-box"

    override val chapterListSelector = "div.entry-chapter"
    override val chapterLinkSelector = "a.stretched-link"
    override val chapterNameSelector = null
    override val chapterDateSelector = "div.date"

    override val supportRelatedMangasBySearch = true

    override suspend fun fetchBaseUrls(client: OkHttpClient): List<String> = listOf(discoveredOrigin(client.get("https://rawfree.me/", Fetch.DOCUMENT).asJsoup()))

    // Every genre in ZingTheme.GENRE_GROUPS resolves on this site, so nothing is excluded.

    override suspend fun getPopularManga(page: Int): MangasPage = parseMangaList(client.get("$baseUrl/ranking/", Fetch.DOCUMENT).asJsoup())

    override suspend fun getLatestUpdates(page: Int): MangasPage = parseMangaList(client.get(pagedUrl(baseUrl, page), Fetch.DOCUMENT).asJsoup())

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) return searchByText(query, page)

        val genres = filters.filterIsInstance<GenreGroup>().flatMap { it.selected }
        return when {
            genres.isEmpty() -> parseMangaList(client.get(pagedUrl(baseUrl, page), Fetch.DOCUMENT).asJsoup())
            genres.size > MAX_GENRES -> throw Exception("ジャンルは最大 $MAX_GENRES 個までです")
            else -> mergeGenrePages(genres, page) { genre, p -> genrePage(genre, p) }
        }
    }

    private suspend fun searchByText(query: String, page: Int): MangasPage {
        val url = "$baseUrl/".toHttpUrl().newBuilder()
            .addQueryParameter("s", query)
            .addQueryParameter("paged", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        // Running off the end of the results, or matching nothing at all, redirects to the home
        // page - which parses perfectly well and would otherwise be shown as if it were a result.
        if (document.selectFirst("body.search") == null) return MangasPage(emptyList(), false)
        val mangas = document.select(mangaListSelector).map(::mangaFromElement)
        // Search pages hold 20; a short one is the last. No `a.next.page-numbers` is rendered
        // here, so the count is the only signal available.
        return MangasPage(mangas, mangas.size >= SEARCH_PAGE_SIZE)
    }

    private suspend fun genrePage(genre: String, page: Int): MangasPage {
        val document = client.get(pagedUrl("$baseUrl/category/$genre", page), Fetch.DOCUMENT).asJsoup()
        // Same redirect-to-home trap as text search, and here the home page even renders a
        // `next` link, so hasNextPage cannot be trusted without this check either.
        if (document.selectFirst("body.archive.category") == null) return MangasPage(emptyList(), false)
        return parseMangaList(document)
    }

    /** WordPress serves the first page at the bare path and redirects `/page/1/` to it. */
    private fun pagedUrl(base: String, page: Int) = if (page == 1) "$base/" else "$base/page/$page/"

    override fun mangaDetailsParse(document: Document) = super.mangaDetailsParse(document).apply {
        status = when {
            // Not every entry carries a status badge, and the site only ever renders one word.
            document.selectFirst(STATUS_SELECTOR)?.text()?.lowercase()?.contains("ongoing") == true -> SManga.ONGOING
            document.selectFirst(STATUS_SELECTOR)?.text()?.lowercase()?.contains("completed") == true -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        // The site's rating and view count have no SManga equivalent, so they ride along in the
        // description rather than being dropped.
        val rating = document.selectFirst(RATING_SELECTOR)?.text()
        val views = document.selectFirst(VIEWS_SELECTOR)?.text()
        val stats = listOfNotNull(rating?.let { "★ $it" }, views).joinToString(" · ")
        if (stats.isNotEmpty()) {
            description = listOfNotNull(description?.takeIf { it.isNotEmpty() }, stats).joinToString("\n\n")
        }
    }

    override fun parseChapterDate(date: String): Long = DATE_FORMAT.tryParseDate(date, ZoneOffset.UTC)

    companion object {
        private const val STATUS_SELECTOR = "h1.name + span"
        private const val RATING_SELECTOR = "div.stars-group p"
        private const val VIEWS_SELECTOR = "p:contains(views)"

        private const val SEARCH_PAGE_SIZE = 20

        private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月 d, yyyy", Locale.JAPANESE)
    }
}
