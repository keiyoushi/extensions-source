package eu.kanade.tachiyomi.extension.all.mitaku

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
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import kotlin.time.Instant

@Source
abstract class Mitaku : KeiSource() {

    override val supportsLatest = false

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/category/ero-cosplay/page/$page".toHttpUrl()
        return parseMangasPage(client.get(url).asJsoup())
    }

    // ========================= Latest =========================
    override suspend fun getLatestUpdates(page: Int) = throw UnsupportedOperationException()

    // ========================= Search =========================
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        if (isMangaOrChapterPath(url.pathSegments) != true) return null
        return mangaDetailsParse(client.get(url).asJsoup())
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val categoryFilter = filters.firstInstance<CategoryFilter>()
        val tagFilter = filters.firstInstance<TagFilter>()

        val url = when {
            query.isNotBlank() ->
                "$baseUrl/page/$page".toHttpUrl().newBuilder()
                    .addQueryParameter("s", query.trim())
                    .build()

            categoryFilter.selected != null ->
                "$baseUrl/category/${categoryFilter.selected}/page/$page".toHttpUrl()

            tagFilter.toUriPart().isNotEmpty() ->
                "$baseUrl/tag/${tagFilter.toUriPart()}/page/$page".toHttpUrl()

            else -> return getPopularManga(page)
        }

        return parseSearch(client.get(url))
    }

    private fun parseSearch(response: Response): MangasPage {
        val document = response.asJsoup()
        val requestPathSegments = response.request.url.pathSegments

        if (isMangaOrChapterPath(requestPathSegments)) {
            val manga = mangaDetailsParse(document)
            return MangasPage(listOf(manga), false)
        }

        return parseMangasPage(document)
    }

    // ========================= Filters =========================
    override fun getFilterList(data: JsonElement?): FilterList = FilterList(
        Filter.Header("NOTE: Only one tag search"),
        Filter.Separator(),
        CategoryFilter(),
        TagFilter(),
    )

    // ======================= MangaUpdate =======================

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val doc = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            mangaDetailsParse(doc),
            parseChapters(doc),
        )
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        val article = document.selectFirst("article") ?: throw Exception("Post details not found")

        title = article.selectFirst("h1")?.text()?.takeIf { it.isNotBlank() }
            ?: throw Exception("Title is mandatory")
        setUrlWithoutDomain(document.location())
        val categoryGenres = article.select("span.cat-links a").joinToString { it.text() }
        val tagGenres = article.select("span.tag-links a").joinToString { it.text() }
        genre = listOf(categoryGenres, tagGenres)
            .filter { it.isNotEmpty() }
            .joinToString()

        status = SManga.COMPLETED
        update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
        initialized = true
    }

    // ========================= Chapters =========================
    private fun parseChapters(document: Document): List<SChapter> {
        val title = document.selectFirst("article h1")?.text() ?: ""

        return listOf(
            SChapter.create().apply {
                setUrlWithoutDomain(document.location())
                chapter_number = 1F
                name = if (title.endsWith("(Video)")) {
                    "This post is video-only, watch it in WebView"
                } else {
                    "Gallery"
                }
                val date = document.selectFirst(".entry-date")?.attr("datetime")
                date_upload = Instant.tryParse(date)
            },
        )
    }

    // ========================= Pages =========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
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
        val segments = pathSegments.filter(String::isNotBlank)
        if (segments.isEmpty()) return false
        if (segments.first() in NON_POST_PATH_PREFIXES) return false

        return segments.size >= 2
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
