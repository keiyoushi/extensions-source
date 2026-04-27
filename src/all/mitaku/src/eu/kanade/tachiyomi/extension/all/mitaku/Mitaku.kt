package eu.kanade.tachiyomi.extension.all.mitaku

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
import keiyoushi.utils.firstInstance
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable

class Mitaku : HttpSource() {
    override val name = "Mitaku"

    override val baseUrl = "https://mitaku.net"

    override val lang = "all"

    override val supportsLatest = false

    private val baseHttpUrl = baseUrl.toHttpUrl()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request {
        val url = baseHttpUrl.newBuilder()
            .addPathSegment("category")
            .addPathSegment("ero-cosplay")
            .addPathSegment("page")
            .addPathSegment(page.toString())
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangasPage(response.asJsoup())

    // ========================= Latest =========================
    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ========================= Search =========================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val deepLinkUrl = query.toHttpUrlOrNull()
        if (page == 1 && deepLinkUrl != null && deepLinkUrl.host == baseHttpUrl.host) {
            val pathSegments = deepLinkUrl.pathSegments.filter { it.isNotBlank() }
            if (isMangaOrChapterPath(pathSegments)) {
                val manga = SManga.create().apply {
                    url = deepLinkUrl.encodedPath
                }

                return fetchMangaDetails(manga)
                    .map { MangasPage(listOf(it), false) }
            }
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val deepLinkUrl = query.toHttpUrlOrNull()
        if (deepLinkUrl != null && deepLinkUrl.host == baseHttpUrl.host) {
            val sQuery = deepLinkUrl.queryParameter("s")
            if (sQuery != null) {
                val url = baseHttpUrl.newBuilder()
                    .addPathSegment("page")
                    .addPathSegment(page.toString())
                    .addQueryParameter("s", sQuery)
                    .build()
                return GET(url, headers)
            }

            if (deepLinkUrl.pathSegments.any { it.isNotBlank() }) {
                return GET(deepLinkUrl, headers)
            }
        }

        if (query.isNotBlank()) {
            val url = baseHttpUrl.newBuilder()
                .addPathSegment("page")
                .addPathSegment(page.toString())
                .addQueryParameter("s", query.trim())
                .build()
            return GET(url, headers)
        }

        val filterList = if (filters.isEmpty()) getFilterList() else filters
        val categoryFilter = filterList.firstInstance<CategoryFilter>()
        val tagFilter = filterList.firstInstance<TagFilter>()

        categoryFilter.selected?.let { category ->
            val url = baseHttpUrl.newBuilder()
                .addPathSegment("category")
                .addPathSegment(category)
                .addPathSegment("page")
                .addPathSegment(page.toString())
                .build()
            return GET(url, headers)
        }

        val tag = tagFilter.toUriPart()
        if (tag.isNotEmpty()) {
            val url = baseHttpUrl.newBuilder()
                .addPathSegment("tag")
                .addPathSegment(tag)
                .addPathSegment("page")
                .addPathSegment(page.toString())
                .build()
            return GET(url, headers)
        }

        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val requestPathSegments = response.request.url.pathSegments.filter { it.isNotBlank() }

        if (isMangaOrChapterPath(requestPathSegments)) {
            val manga = mangaDetailsParse(document).apply {
                url = response.request.url.encodedPath
            }
            return MangasPage(listOf(manga), false)
        }

        return parseMangasPage(document)
    }

    // ========================= Filters =========================
    override fun getFilterList(): FilterList = FilterList(
        Filter.Header("NOTE: Only one tag search"),
        Filter.Separator(),
        CategoryFilter(),
        TagFilter(),
    )

    // ========================= Details =========================
    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup()).apply {
        url = response.request.url.encodedPath
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val article = document.selectFirst("article") ?: throw Exception("Post details not found")

        title = article.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
            ?: throw Exception("Title is mandatory")

        val categoryGenres = article.select("span.cat-links a").joinToString { it.text() }
        val tagGenres = article.select("span.tag-links a").joinToString { it.text() }
        genre = listOf(categoryGenres, tagGenres)
            .filter { it.isNotEmpty() }
            .joinToString()

        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl${manga.url}"

    // ========================= Chapters =========================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val title = document.selectFirst("article h1")?.text() ?: ""

        return listOf(
            SChapter.create().apply {
                url = response.request.url.encodedPath
                chapter_number = 1F
                name = if (title.endsWith("(Video)")) {
                    "This post is video-only, watch it in WebView"
                } else {
                    "Gallery"
                }
            },
        )
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}"

    // ========================= Pages =========================
    override fun pageListParse(response: Response): List<Page> {
        val pages = response.asJsoup().select(PAGE_SELECTOR)
            .mapIndexedNotNull { index, element ->
                val imageUrl = element.absUrl("data-mfp-src").ifBlank { element.absUrl("href") }
                if (imageUrl.isBlank()) {
                    null
                } else {
                    Page(index, imageUrl = imageUrl)
                }
            }

        if (pages.isEmpty()) {
            throw Exception("Page list not found")
        }

        return pages
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(POST_SELECTOR).map(::mangaFromElement)
        val hasNextPage = document.selectFirst(NEXT_PAGE_SELECTOR) != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a")?.absUrl("href")
            ?: throw Exception("Post URL not found")

        val parsedUrl = link.toHttpUrlOrNull() ?: throw Exception("Invalid post URL: $link")
        url = parsedUrl.encodedPath

        title = element.selectFirst("a")?.attr("title")
            ?.takeIf { it.isNotBlank() }
            ?: element.selectFirst("h1, h2, h3")?.text()?.takeIf { it.isNotBlank() }
            ?: throw Exception("Title is mandatory")

        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    private fun isMangaOrChapterPath(pathSegments: List<String>): Boolean {
        if (pathSegments.isEmpty()) return false
        if (pathSegments.first() in NON_POST_PATH_PREFIXES) return false

        return pathSegments.size >= 2
    }

    companion object {
        private const val POST_SELECTOR = "div.article-container article"
        private const val NEXT_PAGE_SELECTOR = "div.wp-pagenavi a.page.larger"
        private const val PAGE_SELECTOR = "a.msacwl-img-link"
        private val NON_POST_PATH_PREFIXES = setOf(
            "category",
            "tag",
            "search",
            "page",
        )
    }
}
