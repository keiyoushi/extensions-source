package eu.kanade.tachiyomi.extension.all.xasiatalbums

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable

class XAsiatAlbums : HttpSource() {

    override val baseUrl = "https://www.xasiat.com"
    override val lang = "all"
    override val name = "XAsiat Albums"
    override val supportsLatest = true

    // Mutable map seeded from initialCategories; new tags discovered while
    // browsing album detail pages are added here at runtime.
    private val categories = initialCategories.toMutableMap()

    // --- Headers ----------------------------------------------------------

    // Used for HTML / API requests only.  Images are fetched with imageHeaders
    // so we don't send XMLHttpRequest to the CDN (which can cause 403s).
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("X-Requested-With", "XMLHttpRequest")

    // Plain headers for image fetches – no XMLHttpRequest sentinel.
    private val imageHeaders: Headers by lazy {
        super.headersBuilder()
            .add("Referer", "$baseUrl/")
            .build()
    }

    // --- Popular / Latest -------------------------------------------------

    override fun popularMangaRequest(page: Int): Request = searchQuery(
        path = "albums/",
        blockId = "list_albums_common_albums_list",
        page = page,
        params = mapOf("sort_by" to "album_viewed_week"),
    )

    override fun latestUpdatesRequest(page: Int): Request = searchQuery(
        path = "albums/",
        blockId = "list_albums_common_albums_list",
        page = page,
        params = mapOf("sort_by" to "post_date"),
    )

    // --- Search -----------------------------------------------------------

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val categoryFilter = filters.firstInstanceOrNull<UriPartFilter>()

        return when {
            query.isNotBlank() -> searchQuery(
                path = "search/search/",
                blockId = "list_albums_albums_list_search_result",
                page = page,
                params = mapOf("q" to query),
            )

            categoryFilter != null && categoryFilter.state > 0 -> searchQuery(
                path = categoryFilter.toUriPart(),
                blockId = "list_albums_common_albums_list",
                page = page,
                params = emptyMap(),
            )

            else -> latestUpdatesRequest(page)
        }
    }

    // Shared async-block request builder used by popular / latest / search.
    private fun searchQuery(
        path: String,
        blockId: String,
        page: Int,
        params: Map<String, String>,
    ): Request {
        val offset = ((page - 1) * ITEMS_PER_PAGE) + 1

        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegments(path.removePrefix("/").removeSuffix("/"))
            addQueryParameter("mode", "async")
            addQueryParameter("function", "get_block")
            addQueryParameter("block_id", blockId)
            addQueryParameter("from", offset.toString())

            // Search endpoint requires a separate from_albums parameter.
            if (blockId.contains("search")) {
                addQueryParameter("from_albums", offset.toString())
            }

            params.forEach { (key, value) -> addQueryParameter(key, value) }

            // Cache-busting timestamp expected by the site.
            addQueryParameter("_", System.currentTimeMillis().toString())
        }.build()

        return GET(url, headers)
    }

    // --- Parse helpers ----------------------------------------------------

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select(".list-albums .item a[href]")
            .mapNotNull { link ->
                val url = link.attr("abs:href")
                if (url.isBlank() || !url.contains("/albums/")) return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(url)
                    title = link.attr("title").ifBlank {
                        link.selectFirst("img")?.attr("alt").orEmpty()
                    }
                    thumbnail_url = link.selectFirst("img")?.let { img ->
                        img.attr("abs:data-original").ifBlank { img.attr("abs:src") }
                    }
                    status = SManga.COMPLETED
                    update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
                }
            }
            .distinctBy { it.url }

        // Primary: look for a Next link.  Fallback: full page of results
        // implies there is a next page (avoids missing pages when the site
        // uses icon-only pagination buttons).
        val hasNextPage = document.select(".pagination a[href], .pages a[href], .pager a[href]")
            .any { it.text().contains("Next", ignoreCase = true) } ||
            mangas.size >= ITEMS_PER_PAGE

        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)
    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // --- Manga details ----------------------------------------------------

    override fun mangaDetailsRequest(manga: SManga): Request = GET(resolveUrl(manga.url), headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst(".entry-title")?.text().orEmpty()
            description = document.selectFirst("meta[property=og:description]")
                ?.attr("content").orEmpty()
            thumbnail_url = document.selectFirst("meta[property=og:image]")
                ?.attr("content")
            genre = getTags(document).joinToString(", ")
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }
    }

    // Extracts tags from the detail page and registers any new ones so they
    // appear in the category filter during the current session.
    private fun getTags(document: Document): List<String> = document.select(".info-content a").mapNotNull { a ->
        val tag = a.text().trim()
        val href = a.attr("abs:href")

        if (tag.isNotBlank() && href.contains("/albums/")) {
            val link = href.substringAfter(".com/").removeSuffix("/")
            if (link.isNotBlank()) categories[tag] = link
            tag
        } else {
            null
        }
    }

    // --- Chapter list -----------------------------------------------------

    override fun chapterListRequest(manga: SManga): Request = GET(resolveUrl(manga.url), headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val requestUrl = response.request.url.toString()
        return listOf(
            SChapter.create().apply {
                url = if (requestUrl.startsWith(baseUrl)) {
                    requestUrl.removePrefix(baseUrl)
                } else {
                    requestUrl
                }
                name = "Photobook"
                date_upload = System.currentTimeMillis()
            },
        )
    }

    // --- Page list --------------------------------------------------------

    // Album detail pages deliver ALL images on a single page (confirmed from live site:
    // even 98-image albums show every image at once with no internal pagination).
    // We override fetchPageList purely to use imageHeaders (no X-Requested-With) for
    // the initial page fetch; the real work is done by parseImagePages below.
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = client.newCall(GET(resolveUrl(chapter.url), headers))
        .asObservableSuccess()
        .map { response ->
            parseImagePages(response.asJsoup())
                .distinct()
                .mapIndexed { index, imageUrl -> Page(index = index, imageUrl = imageUrl) }
        }

    override fun pageListParse(response: Response): List<Page> = parseImagePages(response.asJsoup())
        .distinct()
        .mapIndexed { index, imageUrl -> Page(index = index, imageUrl = imageUrl) }

    // Extracts image URLs from a gallery document.
    //
    // Confirmed live site structure (May 2026):
    //   <a href="/get_image/2/{32-char-hash}/sources/{dir}/{albumId}/{imageId}.jpg/">
    //     <img src="data:image/gif;base64,..." />   ← JS lazy-load placeholder
    //   </a>
    //
    // Key points:
    //  • The href ends with ".jpg/" (trailing slash) so endsWith(".jpg") would FAIL.
    //    Only url.contains("/get_image/") reliably matches these URLs.
    //  • The <img> never has a data-original attribute; the real URL is on the <a>.
    //  • DO NOT use a[href*='/albums/'] — that would also match the "Related Albums"
    //    section at the bottom of the page.
    private fun parseImagePages(document: Document): List<String> = document
        .select("a.item[href], a[href*='/get_image/']")
        .mapNotNull { it.attr("abs:href").takeIf { u -> u.isNotBlank() } }
        .filter { it.contains("/get_image/") }
        .distinct()

    // Resolves a (possibly relative or protocol-relative) URL to an absolute one.
    private fun resolveUrl(url: String): String = when {
        url.startsWith("http") -> url
        url.startsWith("//") -> "https:$url"
        else -> baseUrl + url
    }

    // Not used – images are either direct or followed via OkHttp redirect.
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Use plain imageHeaders (no X-Requested-With) for image fetches.
    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, imageHeaders)

    // --- Filters ----------------------------------------------------------

    override fun getFilterList(): FilterList {
        // "None" is pinned at index 0 (maps to empty string); all other
        // entries are sorted alphabetically.  This guarantees that
        // `categoryFilter.state > 0` correctly identifies a real category.
        val sorted = categories
            .filterKeys { it != "None" }
            .map { Pair(it.key, it.value) }
            .distinctBy { it.first }
            .sortedBy { it.first.lowercase() }

        val pairList = (listOf(Pair("None", "")) + sorted).toTypedArray()

        return FilterList(
            Filter.Header("Tags update dynamically after opening albums"),
            Filter.Separator(),
            UriPartFilter("Category", pairList),
        )
    }

    companion object {
        private const val ITEMS_PER_PAGE = 12
    }
}

// Extension helper to avoid repeating the image-extension check.
private fun String.looksLikeImage(): Boolean = endsWith(".jpg", ignoreCase = true) ||
    endsWith(".jpeg", ignoreCase = true) ||
    endsWith(".png", ignoreCase = true) ||
    endsWith(".webp", ignoreCase = true) ||
    contains("/get_image/")
