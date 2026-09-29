package eu.kanade.tachiyomi.extension.all.xasiatalbums

import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document

@Source
abstract class XAsiatAlbums : KeiSource() {

    // Mutable map seeded from initialCategories; new tags discovered while
    // browsing album detail pages are added here at runtime.
    private val categories = initialCategories.toMutableMap()

    // --- Headers ----------------------------------------------------------

    // Used for HTML / API requests only. Images are fetched without it
    // so we don't send XMLHttpRequest to the CDN (which can cause 403s).
    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("X-Requested-With", "XMLHttpRequest")

    // --- Popular / Latest -------------------------------------------------

    override suspend fun getPopularManga(page: Int): MangasPage = searchQuery(
        path = "albums/",
        blockId = "list_albums_common_albums_list",
        page = page,
        params = mapOf("sort_by" to "album_viewed_week"),
    )

    override suspend fun getLatestUpdates(page: Int): MangasPage = searchQuery(
        path = "albums/",
        blockId = "list_albums_common_albums_list",
        page = page,
        params = mapOf("sort_by" to "post_date"),
    )

    // --- Search -----------------------------------------------------------

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
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

            else -> getLatestUpdates(page)
        }
    }

    // Shared async-block request used by popular / latest / search.
    private suspend fun searchQuery(
        path: String,
        blockId: String,
        page: Int,
        params: Map<String, String>,
    ): MangasPage {
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

        val document = client.get(url).asJsoup()

        val mangas = document.select(".list-albums .item a[href]")
            .mapNotNull { link ->
                val mangaUrl = link.attr("abs:href")
                if (mangaUrl.isBlank() || !mangaUrl.contains("/albums/")) return@mapNotNull null

                SManga.create().apply {
                    setUrlWithoutDomain(mangaUrl)
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

    // --- Manga details ----------------------------------------------------

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(resolveUrl(manga.url))
        val requestUrl = response.request.url.toString()
        val document = response.asJsoup()

        manga.apply {
            document.selectFirst(".entry-title")?.text()?.let { title = it }
            description = document.selectFirst("meta[property=og:description]")
                ?.attr("content").orEmpty()
            thumbnail_url = document.selectFirst("meta[property=og:image]")
                ?.attr("content")
            genre = getTags(document).joinToString()
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        }

        val chapter = SChapter.create().apply {
            url = if (requestUrl.startsWith(baseUrl)) {
                requestUrl.removePrefix(baseUrl)
            } else {
                requestUrl
            }
            name = "Photobook"
            date_upload = System.currentTimeMillis()
        }

        return SMangaUpdate(manga, listOf(chapter))
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

    // --- Page list --------------------------------------------------------

    // Album detail pages deliver ALL images on a single page (confirmed from live site:
    // even 98-image albums show every image at once with no internal pagination).
    override suspend fun getPageList(chapter: SChapter): List<Page> = parseImagePages(client.get(resolveUrl(chapter.url)).asJsoup())
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

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .removeHeader("X-Requested-With")
        .build()

    // --- Filters ----------------------------------------------------------

    override fun getFilterList(data: JsonElement?): FilterList {
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
