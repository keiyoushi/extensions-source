package eu.kanade.tachiyomi.extension.all.xasiatalbums

import eu.kanade.tachiyomi.network.GET
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document

class XAsiatAlbums : HttpSource() {

    override val baseUrl = "https://www.xasiat.com"
    override val lang = "all"
    override val name = "XAsiat Albums"
    override val supportsLatest = true

    private val categories = initialCategories.toMutableMap()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")

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
            if (blockId.contains("search")) {
                addQueryParameter("from_albums", offset.toString())
            }
            params.forEach { (key, value) -> addQueryParameter(key, value) }
            addQueryParameter("_", System.currentTimeMillis().toString())
        }.build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".list-albums .item a[href]").mapNotNull { link ->
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
        }.distinctBy { it.url }
        val hasNextPage = document.select(".pagination a[href], .pages a[href], .pager a[href]")
            .any { it.text().contains("Next", ignoreCase = true) }
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

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

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst(".entry-title")?.text().orEmpty()
        description = document.selectFirst("meta[property=og:description]")?.attr("content").orEmpty()
        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
        genre = getTags(document).joinToString(", ")
        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
    }

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

    override fun chapterListParse(response: Response): List<SChapter> = listOf(
        SChapter.create().apply {
            url = response.request.url.encodedPath
            name = "Photobook"
            date_upload = System.currentTimeMillis()
        },
    )

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val pages = mutableListOf<Page>()

        // Add current page as page 0
        pages.add(Page(0, "", response.request.url.toString()))

        // Select only valid image viewer pages
        document.select(".pagination a[href*=get_image], .pager a[href*=get_image]")
            .distinctBy { it.attr("abs:href") }
            .forEachIndexed { index, element ->
                pages.add(Page(index + 1, "", element.attr("abs:href")))
            }
        return pages
    }

    override fun imageUrlParse(response: Response): String {
        val document = response.asJsoup()

        // Optimization: Priority selectors based on source code analysis
        val selectors = arrayOf(
            ".image-holder img",
            ".main-image img",
            ".content img",
            "img#main-image",
        )

        for (selector in selectors) {
            val element = document.selectFirst(selector) ?: continue

            // Check original attributes first to bypass lazy-loading
            val url = element.attr("abs:data-original")
                .ifBlank { element.attr("abs:data-src") }
                .ifBlank { element.attr("abs:src") }

            if (isValidImage(url)) return url
        }

        // Final sanity check: if it's not an image URL, throw to retry
        throw Exception("Failed to resolve image. Server might have returned HTML instead.")
    }

    private fun isValidImage(url: String): Boolean {
        val u = url.lowercase()
        return u.isNotBlank() &&
            !u.contains("data:image/") &&
            !u.contains("logo") &&
            (u.endsWith(".jpg") || u.endsWith(".jpeg") || u.endsWith(".webp") || u.contains(".jpg?"))
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, headersBuilder().set("Referer", page.url).build())

    override fun getFilterList(): FilterList {
        val pairList = categories.map { Pair(it.key, it.value) }
            .distinctBy { it.first }
            .sortedBy { it.first.lowercase() }
            .toTypedArray()
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
