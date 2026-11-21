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
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * ToonDex (https://toondex.xyz) — Django + server-rendered HTML + htmx fragments.
 *
 * Route map, measured from the live site:
 *   /comics/{slug}/                    series details (title, authors, genres, status, summary, full chapter list)
 *   /comics/{slug}/{chapter-slug}/     reader; all page images in one document, no page-per-image pagination
 *   /latest-updates/{page}/            paginated listing (page 2+ is "/latest-updates/2/")
 *   /most-view/                        single-page listing, no pagination
 *   /search/?q={term}                  plain GET, no POST, no tokens
 *
 * No JSON API. No Cloudflare challenge observed on plain requests with a browser UA —
 * only the Cloudflare *Analytics* beacon runs client-side, which is not bot protection.
 * The Django CSRF token that appears in forms is only required for POST actions
 * (bookmarks, reports, ratings); every read path is tokenless GET.
 */
@Source
class Toondex(
    override val name: String = "ToonDex",
    override val lang: String = "en",
    override val baseUrl: String = "https://toondex.xyz",
    override val id: Long = 1L,
) : KeiSource() {

    override val supportsLatest = true

    override fun Headers.Builder.configureHeaders(): Headers.Builder = this
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36")
        .add("Accept-Language", "en-US,en;q=0.9")

    // ---------------------------------------------------------------------
    // Selectors — cut from live HTML, see comments at each site.
    // ---------------------------------------------------------------------

    /**
     * Listing card, from /latest-updates/ live HTML:
     *
     * <div class="overflow-hidden relative pt-[140%] item-thumb c-image-hover col-span-2">
     *     <a href="/comics/my-lewd-college-friends-akhkpt/" class="">
     *         <img class="..." src="/media/uploads/images/730945270806/thumbnail.webp" ...>
     *     </a>
     * </div>
     *
     * The thumbnail <a> is the only anchor that contains an <img>, so
     * a[href*="/comics/"]:has(img) selects exactly one element per card — the thumbnail
     * anchor. The sibling title anchor has no img, so it never double-counts.
     */
    private val listingCardSelector = "a[href*=\"/comics/\"]:has(img)"

    /**
     * Pagination pager ends with:
     *     <li><a ... href="/latest-updates/2/"><i class="fa-solid fa-angle-right"></i></a></li>
     * so the "Next" arrow is uniquely identified by the fa-angle-right icon inside the <a>.
     */
    private val nextPageSelector = "a:has(i.fa-angle-right)"

    private fun listingFromElement(element: org.jsoup.nodes.Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))

        // Title resolution, two layouts, measured live:
        //  - home/search carousel: the anchor wraps the whole card, h2.box-title sits inside it
        //  - /latest-updates/ grid: the title anchor is a SIBLING of the thumb container, so it
        //    hangs off element.parent().nextElementSibling()
        // The slug (minus the site's 6-char id suffix, e.g. "-cu14u1") stays as the last resort.
        val slug = element.attr("href").substringAfterLast('/').trim('/')
        title = element.selectFirst(".box-title")?.text()
            ?: element.parent()?.nextElementSibling()?.selectFirst(".box-title")?.text()
            ?: slug.replace(Regex("-[a-z0-9]{6}$"), "").replace('-', ' ')
                .let { cleaned -> if (cleaned.isBlank()) slug.replace('-', ' ') else cleaned }

        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    private fun listingParse(document: Document, page: Int): MangasPage {
        val mangas = document.select(listingCardSelector).map { listingFromElement(it) }
        val hasNextPage = page > 0 && document.select(nextPageSelector).isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    // ---------------------------------------------------------------------
    // Browse
    // ---------------------------------------------------------------------

    // /most-view/ and /best-rated/ are single pages with no pagination control in the HTML.
    // /latest-updates/ is the only listing with a pager ("/latest-updates/2/"), so it feeds
    // the popular browse tab so users can page through the whole catalogue.
    override suspend fun getPopularManga(page: Int): MangasPage {
        val suffix = if (page > 1) "$page/" else ""
        val document = client.get("$baseUrl/latest-updates/$suffix", Fetch.DOCUMENT).asJsoup()
        return listingParse(document, page)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val document = client.get("$baseUrl/most-view/", Fetch.DOCUMENT).asJsoup()
        return listingParse(document, 0) // single page, no pager
    }

    // ---------------------------------------------------------------------
    // Search
    // ---------------------------------------------------------------------

    // Header search form, live HTML:
    //     <form id="search-form" class="flex items-center" method="GET" action="/search/">
    //         <input id="search-input" name="q" ...>
    // Plain GET with q=. No CSRF, no POST body.
    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())
            .build()
        val document = client.get(url, Fetch.DOCUMENT).asJsoup()
        return listingParse(document, page)
    }

    // Deep link / webview landing: /comics/{slug}/
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments
        if (segments.size >= 2 && segments[0] == "comics" && segments[1].isNotEmpty()) {
            return fetchMangaUpdate("/comics/${segments[1]}/").manga
        }
        return null
    }

    // ---------------------------------------------------------------------
    // Details + chapters
    // ---------------------------------------------------------------------

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

    private suspend fun fetchMangaUpdate(mangaUrl: String): SMangaUpdate = fetchMangaUpdate(SManga.create().apply { url = mangaUrl }, emptyList(), true, true)

    private fun parseDetails(document: Document): SManga = SManga.create().apply {
        // <h1 class="text-2xl font-semibold">Capitalist Harem</h1>
        title = document.selectFirst("h1.text-2xl.font-semibold")!!.text()

        // Alternative Names row, live HTML:
        //     <tr><td class="w-40 whitespace-nowrap align-top">Alternative Names:</td>
        //         <td><span>아파트 관리인이 되었다</span></td></tr>
        // Mihon's model has no alt-names field — the house convention is to prepend them
        // to the description so they stay visible in the details sheet.
        val altNames = document.selectFirst("td:contains(Alternative Names:) ~ td span")?.text()?.trim()
            .takeUnless { it.isNullOrBlank() }

        // Authors row:
        //     <tr><td class="w-40 align-top">Authors:</td><td class="flex gap-2 flex-wrap">
        //         <a href="/authors/bibik/"><span ...>Bibik</span></a>
        //     </td></tr>
        val authors = document.select("td:contains(Authors:) ~ td a[href^=/authors/] span").joinToString(", ") { it.text() }

        // Artists row, same shape as authors but linking to /artists/{name}/:
        //     <tr><td class="w-40 align-top ">Artists:</td><td ...>
        //         <a href="/artists/beluga/"><span ...>Beluga</span></a>
        //     </td></tr>
        // When authors and artists differ, Mihon's single author field carries both,
        // joined by a semicolon — the house convention for split credits.
        val artists = document.select("td:contains(Artists:) ~ td a[href^=/artists/] span").joinToString(", ") { it.text() }
        author = listOf(authors, artists)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString("; ")
            .ifBlank { null }

        // Summary block — the text nests inside a wrapping div, NOT a direct <p> sibling:
        //     <h2 class="text-2xl font-semibold">Summary</h2>
        //     <div class="text-lg"><p>Lee Han’s life was going nowhere...</p></div>
        description = buildString {
            if (!altNames.isNullOrBlank()) {
                append("Alternative names: ").append(altNames).append('\n')
            }
            document.selectFirst("h2:contains(Summary) ~ div p")?.text()?.trim()?.let { append(it) }
        }.trim().ifBlank { null }

        // Categories row (Manhwa / Manga / Webtoon):
        //     <tr><td class="w-40 align-top ">Categories:</td><td ...>
        //         <a href="/categories/manhwa/"><span ...>Manhwa</span></a>
        //     </td></tr>
        // Folded into the genre list ahead of the real genres.
        val categories = document.select("td:contains(Categories:) ~ td a[href^=/categories/] span").map { it.text() }

        // Genres row: every entry links to /genres/{name}/.
        val genres = document.select("td:contains(Genres:) ~ td a[href^=/genres/] span").map { it.text() }
        genre = (categories + genres).joinToString()

        // Status row links to /ongoing/ or /completed/.
        status = when (document.selectFirst("td:contains(Status:) ~ td a[href^=/ongoing/]") != null) {
            true -> SManga.ONGOING
            false -> SManga.COMPLETED
        }

        // Cover: <img class="w-96 m-auto" src="/media/uploads/images/970760645573/thumbnail.webp" ...>
        thumbnail_url = document.selectFirst("div.item-thumb img, img[src*=/media/uploads/]")?.absUrl("src")
        initialized = true
    }

    // Full chapter list on the details page, newest first:
    //     <a class="block p-3 visited:text-primary hover:bg-gray-100" href="/comics/.../chapter-316-5/">
    //         <div class="flex flex-row justify-between">
    //             <div class="font-medium">Chapter 316.5</div>
    //             <div>September 21, 2026</div>
    //         </div>
    //     </a>
    // TWO foreign-row sources exist on the details page, both measured live:
    //  1. the "Latest Updates" sidebar carries OTHER comics' chapter rows (class "block py-1 px-2")
    //  2. the sidebar ALSO repeats THIS comic's newest rows — with a different inner layout:
    //     no font-medium label div, and a relative timestamp ("6 days") fused into the text.
    // Both are killed by demanding, in Kotlin code (not just CSS):
    //  - href starts with this manga's own path (/comics/{slug}/)
    //  - the row's class carries the real-list token "p-3" (sidebar rows use "py-1 px-2")
    //  - the row contains /chapter- (drops the separate "Read Now" button link)
    //  - the row has a div.font-medium label (the poster/hero anchor "block p-3" wraps only
    //    an <img> — without this test it duplicates chapter-1 with a blank name and no date)
    private val dateFormat = SimpleDateFormat("MMMM d, yyyy", Locale.ENGLISH)

    // Sidebar timestamps like "6 days" / "31 months" glued onto labels are stripped defensively.
    private val relativeSuffix = Regex(
        """\s+(?:(?:\d+\s+)?(?:hours?|days?|weeks?|months?|years?)\s*(?:ago)?|yesterday)\s*$""",
        RegexOption.IGNORE_CASE,
    )

    private fun parseChapters(document: Document, mangaUrl: String): List<SChapter> = document.select("a[href]")
        .filter { row ->
            val href = row.attr("href")
            val cls = row.attr("class")
            href.startsWith(mangaUrl) && href.contains("/chapter-") &&
                cls.contains("p-3") && !cls.contains("py-1") &&
                row.selectFirst("div.font-medium") != null
        }
        .map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))

                // Chapter number from the URL slug ("chapter-316-5" -> 316.5) — more
                // reliable than the display text, which can be localized or decorated.
                val slugNumber = url.substringAfterLast("chapter-").substringBefore('/')
                    .replace('-', '.')
                chapter_number = slugNumber.toFloatOrNull() ?: -1f

                // First inner <div> holds the label, e.g. "Chapter 316.5". A blank or
                // timestamp-glued label falls back to "Chapter {slug number}".
                name = (element.selectFirst("div.font-medium")?.text()?.trim() ?: "")
                    .replace(relativeSuffix, "")
                    .ifBlank { "Chapter ${slugNumber.replace('.', '.')}" }

                // Second inner <div> holds the date. Absolute dates parse; anything else
                // (relative strings like "6 days") leaves date_upload at 0.
                val dateText = element.selectFirst("div.font-medium")?.nextElementSibling()?.text()?.trim()
                date_upload = try {
                    dateFormat.parse(dateText ?: "")?.time ?: 0L
                } catch (e: ParseException) {
                    0L
                }
            }
        }
        .distinctBy { it.url } // same chapter can appear twice
        .sortedBy { it.chapter_number } // ascending by number
        .asReversed() // ...stored newest-first per Mihon convention, so chapter 1 leads the list

    // ---------------------------------------------------------------------
    // Pages
    // ---------------------------------------------------------------------

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // Reader markup — every page image is a row in one document:
        //     <img src="/media/uploads/.../e596cb4c....webp" id="row-1" ...>
        //     <img data-src="/media/uploads/.../64e0066170eb....webp" id="row-2" class="... lazy-img">
        // First image carries plain src; the rest are lazy-loaded via data-src. Read both.
        // Scoping to img[id^=row-] excludes logo, ad banners, and nav icons.
        val document = client.get(baseUrl + chapter.url, Fetch.DOCUMENT).asJsoup()
        return document.select("img[id^=row-]").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src").ifEmpty { element.absUrl("data-src") })
        }
    }
}
