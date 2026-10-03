package eu.kanade.tachiyomi.extension.all.everiaclub

import eu.kanade.tachiyomi.source.model.Filter
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class EveriaClub : KeiSource() {

    private val Element.imgSrc: String
        get() = attr("data-lazy-src")
            .ifEmpty { attr("data-src") }
            .ifEmpty { attr("src") }

    // ========================= Popular =========================
    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select(".wli_popular_posts-class li").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.imgSrc
                title = element.select("h3").text()
                setUrlWithoutDomain(element.select("h3 > a").attr("abs:href"))
            }
        }
        return MangasPage(mangas, false)
    }

    // ========================= Latest =========================
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", "20")
            .addQueryParameter("_embed", "wp:featuredmedia")
            .build()
        return fetchPosts(url)
    }

    private suspend fun fetchPosts(url: HttpUrl): MangasPage {
        val response = client.get(url)
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 0
        val currentPage = url.queryParameter("page")?.toIntOrNull() ?: 0
        val posts = response.parseAs<List<WPPostDto>>()
        val mangas = posts.map { post ->
            SManga.create().apply {
                title = Parser.unescapeEntities(post.title.rendered, false)
                setUrlWithoutDomain(post.link)
                thumbnail_url = post.thumbnail
            }
        }
        return MangasPage(mangas, currentPage < totalPages)
    }

    // ========================= Search =========================
    override suspend fun getMangasByUrl(url: HttpUrl, page: Int): MangasPage {
        val pathSegments = url.pathSegments.filter { it.isNotEmpty() }
        if (url.host != baseUrl.toHttpUrl().host || pathSegments.isEmpty()) {
            return MangasPage(emptyList(), false)
        }

        if (pathSegments[0] == "category" || pathSegments[0] == "tag") {
            val newUrl = url.newBuilder().apply {
                val pageIdx = url.pathSegments.indexOf("page")
                if (pageIdx != -1) {
                    setPathSegment(pageIdx + 1, page.toString())
                } else {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
            }.build()
            return parseHtmlMangasPage(client.get(newUrl).asJsoup())
        }

        // Post link
        val document = client.get(url).asJsoup()
        val manga = SManga.create().apply {
            this.url = url.encodedPath
            title = document.selectFirst(".entry-title")?.text()
                ?: throw Exception("Title is mandatory")
            thumbnail_url = document.selectFirst(".entry-content img")?.imgSrc
        }
        return MangasPage(listOf(manga), false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val categoryFilter = filters.firstInstanceOrNull<CategoryFilter>()
        val tagGroup = filters.firstInstanceOrNull<TagGroup>()

        val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", "20")
            .addQueryParameter("_embed", "wp:featuredmedia")

        if (query.isNotEmpty()) {
            url.addQueryParameter("search", query)
        }

        if (categoryFilter != null && categoryFilter.state != 0) {
            url.addQueryParameter("categories", categoryFilter.toUriPart())
        }

        if (tagGroup != null) {
            val includedTags = tagGroup.state.filter { it.state == Filter.TriState.STATE_INCLUDE }.map { it.id }
            val excludedTags = tagGroup.state.filter { it.state == Filter.TriState.STATE_EXCLUDE }.map { it.id }

            if (includedTags.isNotEmpty()) {
                url.addQueryParameter("tags", includedTags.joinToString(","))
            }
            if (excludedTags.isNotEmpty()) {
                url.addQueryParameter("tags_exclude", excludedTags.joinToString(","))
            }
        }

        return fetchPosts(url.build())
    }

    // ========================= Details =========================
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(getMangaUrl(manga))
        val responseUrl = response.request.url.toString()
        val document = response.asJsoup()

        manga.apply {
            title = document.select(".entry-title").text()
            description = document.select(".entry-title").text()
            genre = document.select(".post-tags > a").joinToString { it.text() }
            status = SManga.COMPLETED
        }

        val chapter = SChapter.create().apply {
            val canonicalUrl = document.selectFirst("link[rel=\"canonical\"]")?.attr("href")
                ?: responseUrl
            setUrlWithoutDomain(canonicalUrl)
            chapter_number = -2f
            name = "Gallery"
            date_upload = getDate(canonicalUrl)
        }

        return SMangaUpdate(manga, listOf(chapter))
    }

    override val supportsRelatedMangas get() = true

    // TODO: After converting the whole extension to use API, we can request list of tags' ID directly then use them to build queries.
    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val genres = manga.genre?.split(",")?.map { it.trim() } ?: return emptyList()
        val tags = getFilterList().firstInstanceOrNull<TagGroup>()?.state.orEmpty()
        return genres.parallelCatchingFlatMap { genre ->
            val tag = tags.firstOrNull { it.name.equals(genre, ignoreCase = true) }
            if (tag != null) {
                getSearchMangaList(
                    1,
                    "",
                    FilterList(
                        TagGroup(
                            listOf(
                                TagFilter(tag.name, tag.id).apply { state = Filter.TriState.STATE_INCLUDE },
                            ),
                        ),
                    ),
                )
            } else {
                getSearchMangaList(1, genre, FilterList())
            }.mangas
        }
    }

    // ========================= Pages =========================
    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()
        val pageLinks = document.select(".page-links a.post-page-numbers")
            .map { it.attr("abs:href") }
            .distinct()

        val urls = if (pageLinks.isEmpty()) {
            parseImages(document)
        } else {
            val semaphore = Semaphore(3)
            val docs = coroutineScope {
                pageLinks.map { url ->
                    async {
                        semaphore.withPermit {
                            runCatching { client.get(url).asJsoup() }.getOrNull()
                        }
                    }
                }.awaitAll()
            }
            (listOf(document) + docs.filterNotNull()).flatMap { parseImages(it) }
        }

        return urls.distinct()
            .filter { it.isNotEmpty() && !it.startsWith("data:image") }
            .mapIndexed { i, url -> Page(i, imageUrl = url) }
    }

    private fun parseImages(document: Element): List<String> {
        document.select("noscript").remove()
        return document.select(".entry-content img").map { it.imgSrc }
    }

    // ========================= Filters =========================
    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val categories = client.get("$baseUrl/wp-json/wp/v2/categories?per_page=100&hide_empty=true")
            .parseAs<List<WPCategoryDto>>()
        val tags = client.get("$baseUrl/wp-json/wp/v2/tags?per_page=100&hide_empty=true&orderby=count&order=desc")
            .parseAs<List<WPTagDto>>()
        return FilterDataDto(categories, tags).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterDataDto>()
        val categories = filterData?.categories
            ?.map { it.name to it.id.toString() }
            ?.let { arrayOf("Any" to "") + it }
            ?: DEFAULT_CATEGORIES

        val filters = mutableListOf<Filter<*>>(
            Filter.Header("NOTE: Category filter can be combined with search."),
            Filter.Separator(),
            CategoryFilter(categories),
        )

        filterData?.tags?.takeIf { it.isNotEmpty() }?.let { tags ->
            filters.add(TagGroup(tags.map { TagFilter(it.name, it.id) }))
        }

        return FilterList(filters)
    }

    // ========================= Helpers =========================
    private fun parseHtmlMangasPage(document: Document): MangasPage {
        val mangas = document.select("#blog-entries > article, #content > article").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.imgSrc
                title = element.select(".entry-title").text()
                setUrlWithoutDomain(element.select(".entry-title > a").attr("abs:href"))
            }
        }
        val hasNextPage = document.selectFirst(".next") != null
        return MangasPage(mangas, hasNextPage)
    }

    /**
     * Parallel implementation of [Iterable.flatMap], but running
     * the transformation function inside a try-catch block.
     */
    private suspend inline fun <A, B> Iterable<A>.parallelCatchingFlatMap(crossinline f: suspend (A) -> Iterable<B>): List<B> = withContext(Dispatchers.IO) {
        map {
            async {
                try {
                    f(it)
                } catch (e: Throwable) {
                    e.printStackTrace()
                    emptyList()
                }
            }
        }.awaitAll().flatten()
    }

    private fun getDate(str: String): Long = DATE_FORMAT.tryParseDate(DATE_REGEX.find(str)?.value)

    companion object {
        private val DATE_REGEX = """[0-9]{4}/[0-9]{2}/[0-9]{2}""".toRegex()
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.US)
        private val DEFAULT_CATEGORIES = arrayOf(
            Pair("Any", ""),
            Pair("China", "42"),
            Pair("Cosplay", "7"),
            Pair("Japan", "2"),
            Pair("Korea", "11"),
            Pair("Thailand", "1984"),
        )
    }
}
