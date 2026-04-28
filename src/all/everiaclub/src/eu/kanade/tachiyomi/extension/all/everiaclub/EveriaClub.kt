package eu.kanade.tachiyomi.extension.all.everiaclub

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class EveriaClub : HttpSource() {
    override val baseUrl = "https://everia.club"
    override val lang = "all"
    override val name = "Everia.club"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val Element.imgSrc: String
        get() = attr("data-lazy-src")
            .ifEmpty { attr("data-src") }
            .ifEmpty { attr("src") }

    // ========================= Popular =========================
    override fun popularMangaRequest(page: Int): Request = GET(baseUrl, headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
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
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrlOrNull()!!.newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("per_page", "20")
            .addQueryParameter("_embed", "wp:featuredmedia")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val posts = response.parseAs<List<WPPostDto>>()
        val mangas = posts.map { post ->
            SManga.create().apply {
                title = post.title.rendered
                setUrlWithoutDomain(post.link)
                thumbnail_url = post.thumbnail
            }
        }
        val totalPages = response.header("X-WP-TotalPages")?.toIntOrNull() ?: 0
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 0
        return MangasPage(mangas, currentPage < totalPages)
    }

    // ========================= Search =========================
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("http")) {
            val url = query.toHttpUrlOrNull()
            if (url != null && url.host == baseUrl.toHttpUrlOrNull()?.host) {
                val pathSegments = url.pathSegments.filter { it.isNotEmpty() }
                if (pathSegments.isEmpty()) return super.fetchSearchManga(page, query, filters)

                return if (pathSegments[0] == "category" || pathSegments[0] == "tag") {
                    val newUrl = url.newBuilder().apply {
                        val pageIdx = url.pathSegments.indexOf("page")
                        if (pageIdx != -1) {
                            setPathSegment(pageIdx + 1, page.toString())
                        } else {
                            addPathSegment("page")
                            addPathSegment(page.toString())
                        }
                    }.build()
                    client.newCall(GET(newUrl, headers)).asObservableSuccess().map { response ->
                        parseHtmlMangasPage(response)
                    }
                } else {
                    // Post link
                    client.newCall(GET(query, headers)).asObservableSuccess().map { response ->
                        val document = response.asJsoup()
                        val manga = SManga.create().apply {
                            this.url = url.encodedPath
                            title = document.selectFirst(".entry-title")?.text()
                                ?: throw Exception("Title is mandatory")
                            thumbnail_url = document.selectFirst(".entry-content img")?.imgSrc
                        }
                        MangasPage(listOf(manga), false)
                    }
                }
            }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }
        val categoryFilter = filterList.firstInstanceOrNull<CategoryFilter>()
        val tagGroup = filterList.firstInstanceOrNull<TagGroup>()

        val url = "$baseUrl/wp-json/wp/v2/posts".toHttpUrlOrNull()!!.newBuilder()
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

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = latestUpdatesParse(response)

    // ========================= Details =========================
    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.select(".entry-title").text()
        description = document.select(".entry-title").text()
        genre = document.select(".post-tags > a").joinToString(", ") { it.text() }
        status = SManga.COMPLETED
        initialized = true
    }

    // ========================= Chapters =========================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return listOf(
            SChapter.create().apply {
                val canonicalUrl = document.selectFirst("link[rel=\"canonical\"]")?.attr("href")
                    ?: response.request.url.toString()
                setUrlWithoutDomain(canonicalUrl)
                chapter_number = -2f
                name = "Gallery"
                date_upload = getDate(canonicalUrl)
            },
        )
    }

    // ========================= Pages =========================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        document.select("noscript").remove()
        return document.select(".entry-content img").mapIndexed { i, element ->
            val url = element.imgSrc
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ========================= Filters =========================
    private var categories = arrayOf(
        Pair("Any", ""),
        Pair("China", "42"),
        Pair("Cosplay", "7"),
        Pair("Japan", "2"),
        Pair("Korea", "11"),
        Pair("Thailand", "1984"),
    )
    private var tags = emptyList<TagFilter>()
    private var filtersState = FilterState.Unfetched
    private var filterAttempts = 0

    private enum class FilterState {
        Fetching,
        Fetched,
        Unfetched,
    }

    override fun getFilterList(): FilterList {
        launchFilters()
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("NOTE: Category filter can be combined with search."),
            Filter.Separator(),
            CategoryFilter(categories),
        )

        if (tags.isNotEmpty()) {
            filters.add(TagGroup(tags))
        }

        if (filtersState == FilterState.Fetching) {
            filters.add(Filter.Header("Fetching tags from API..."))
        }

        return FilterList(filters)
    }

    private fun launchFilters() {
        if (filtersState != FilterState.Unfetched || filterAttempts >= 3) return
        filtersState = FilterState.Fetching
        filterAttempts++

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val categoriesResponse = client.newCall(GET("$baseUrl/wp-json/wp/v2/categories?per_page=100&hide_empty=true", headers)).execute()
                val tagsResponse = client.newCall(GET("$baseUrl/wp-json/wp/v2/tags?per_page=100&hide_empty=true&orderby=count&order=desc", headers)).execute()

                if (categoriesResponse.isSuccessful) {
                    val catDtos = categoriesResponse.parseAs<List<WPCategoryDto>>()
                    if (catDtos.isNotEmpty()) {
                        categories = arrayOf("Any" to "") + catDtos.map { it.name to it.id.toString() }.toTypedArray()
                    }
                }

                if (tagsResponse.isSuccessful) {
                    val tagDtos = tagsResponse.parseAs<List<WPTagDto>>()
                    tags = tagDtos.map { TagFilter(it.name, it.id) }
                }

                filtersState = FilterState.Fetched
            } catch (e: Exception) {
                filtersState = FilterState.Unfetched
            }
        }
    }

    // ========================= Helpers =========================
    private fun parseHtmlMangasPage(response: Response): MangasPage {
        val document = response.asJsoup()
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

    private fun getDate(str: String): Long {
        val match = DATE_REGEX.find(str)
        return match?.let { DATE_FORMAT.tryParse(it.value) } ?: 0L
    }

    companion object {
        private val DATE_REGEX = """[0-9]{4}/[0-9]{2}/[0-9]{2}""".toRegex()
        private val DATE_FORMAT = SimpleDateFormat("yyyy/MM/dd", Locale.US)
    }
}
