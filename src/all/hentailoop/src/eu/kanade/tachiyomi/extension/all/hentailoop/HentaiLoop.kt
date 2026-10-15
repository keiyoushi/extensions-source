package eu.kanade.tachiyomi.extension.all.hentailoop

import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonElement
import okhttp3.Call
import okhttp3.Callback
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import kotlin.time.Instant

@Source
abstract class HentaiLoop : KeiSource() {

    private val ajaxHeaders: Headers
        get() = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

    override suspend fun getPopularManga(page: Int): MangasPage = getMangaList("manga", null, "views", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getMangaList("manga", null, "date", page)

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("div.manga-card a").map { element ->
            SManga.create().apply {
                url = element.absUrl("href").toHttpUrl().pathSegments[1]
                title = element.selectFirst(".title")!!.ownText().trim()
                thumbnail_url = element.selectFirst("img.attachment-manga_thumb")?.imgAttr()
            }
        }
        val hasNextPage = document.selectFirst("nav.navigation a.next") != null

        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(data: JsonElement?): FilterList {
        val sourceFilters = this::class.java.getResourceAsStream("/assets/filters.json")!!
            .parseAs<SourceFilters>()

        return FilterList(
            SortFilter(),
            ReleaseFilter(sourceFilters.releases),
            GenreFilter(sourceFilters.genres),
            TagFilter(sourceFilters.tags),
            ParodyFilter(sourceFilters.parodies),
            ArtistFilter(sourceFilters.artists),
            CharacterFilter(sourceFilters.characters),
            CircleFilter(sourceFilters.circles),
            ConventionFilter(sourceFilters.conventions),
            LanguageFilter(sourceFilters.languages),
            MinPageCount(),
            MaxPageCount(),
            UncensoredFilter(),
        )
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments[0] != "manga" || url.pathSegments.size < 2) {
            return null
        }

        val manga = SManga.create().apply { this.url = url.pathSegments[1] }

        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val activeFilter = filters.findActiveFilter()

        // multiple active filters
        if (activeFilter == null) {
            return advancedSearch(page, query, filters)
        }

        val (directory, slug) = activeFilter

        // query search is active
        if (query.isNotBlank()) {
            // no other filter active
            return if (slug == null) {
                quickSearch(query)
            } else {
                // filters + query search
                advancedSearch(page, query, filters)
            }
        }

        // one filter (or default list) active with no query search
        return getMangaList(directory, slug, filters.firstInstance<SortFilter>().sort, page)
    }

    private suspend fun quickSearch(query: String): MangasPage {
        val body = FormBody.Builder()
            .add("action", "nativeSearch")
            .add("subAction", "search")
            .add("query", query.trim())
            .build()

        val data = client.post("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, body)
            .parseAs<Data<QuerySearchResponse>>()
        val mangas = data.data.posts.map { manga ->
            SManga.create().apply {
                url = manga.link.toHttpUrl().pathSegments[1]
                title = manga.title
                thumbnail_url = manga.thumb
            }
        }

        return MangasPage(mangas, hasNextPage = false)
    }

    private suspend fun getMangaList(directory: String, slug: String?, sort: String, page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment(directory)
            if (slug != null) {
                addPathSegment(slug)
            }
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addPathSegment("")
            addQueryParameter("sortmanga", sort)
        }.build()

        return parseMangaList(client.get(url))
    }

    private suspend fun advancedSearch(page: Int, query: String, filters: FilterList): MangasPage {
        val data = SearchRequest(
            query = query.trim(),
            filters = listOf(
                FilterValue(
                    name = "manga-genres",
                    filterValues = filters.firstInstance<GenreFilter>().checked.map { it.id.toString() },
                    operator = "in",
                ),
                FilterValue(
                    name = "post_tag",
                    filterValues = filters.firstInstance<TagFilter>().included.map { it.id.toString() },
                    operator = "in",
                ),
                FilterValue(
                    name = "post_tag",
                    filterValues = filters.firstInstance<TagFilter>().excluded.map { it.id.toString() },
                    operator = "ex",
                ),
                FilterValue(
                    name = "manga-parodies",
                    filterValues = filters.firstInstance<ParodyFilter>().included.map { it.id.toString() },
                    operator = "in",
                ),
                FilterValue(
                    name = "manga-parodies",
                    filterValues = filters.firstInstance<ParodyFilter>().excluded.map { it.id.toString() },
                    operator = "ex",
                ),
                FilterValue(
                    name = "manga-artists",
                    filterValues = filters.firstInstance<ArtistFilter>().included.map { it.id.toString() },
                    operator = "in",
                ),
                FilterValue(
                    name = "manga-artists",
                    filterValues = filters.firstInstance<ArtistFilter>().excluded.map { it.id.toString() },
                    operator = "ex",
                ),
                FilterValue(
                    name = "manga-characters",
                    filterValues = filters.firstInstance<CharacterFilter>().included.map { it.id.toString() },
                    operator = "in",
                ),
                FilterValue(
                    name = "manga-characters",
                    filterValues = filters.firstInstance<CharacterFilter>().excluded.map { it.id.toString() },
                    operator = "ex",
                ),
                FilterValue(
                    name = "manga-circles",
                    filterValues = filters.firstInstance<CircleFilter>().included.map { it.id.toString() },
                    operator = "in",
                ),
                FilterValue(
                    name = "manga-circles",
                    filterValues = filters.firstInstance<CircleFilter>().excluded.map { it.id.toString() },
                    operator = "ex",
                ),
                FilterValue(
                    name = "manga-collections",
                    filterValues = emptyList(),
                    operator = "in",
                ),
                FilterValue(
                    name = "manga-collections",
                    filterValues = emptyList(),
                    operator = "ex",
                ),
                FilterValue(
                    name = "manga-conventions",
                    filterValues = filters.firstInstance<ConventionFilter>().included.map { it.id.toString() },
                    operator = "in",
                ),
                FilterValue(
                    name = "manga-conventions",
                    filterValues = filters.firstInstance<ConventionFilter>().excluded.map { it.id.toString() },
                    operator = "ex",
                ),
                FilterValue(
                    name = "manga-languages",
                    filterValues = filters.firstInstance<LanguageFilter>().included.map { it.id.toString() },
                    operator = "in",
                ),
                FilterValue(
                    name = "manga-languages",
                    filterValues = filters.firstInstance<LanguageFilter>().excluded.map { it.id.toString() },
                    operator = "ex",
                ),
            ),
            specialFilters = listOf(
                YearFilter(
                    yearOperator = "in",
                    yearValue = (filters.firstInstance<ReleaseFilter>().release?.slug ?: ""),
                ),
                PagesFilter(
                    values = PagesValues(
                        min = filters.firstInstance<MinPageCount>().count,
                        max = filters.firstInstance<MaxPageCount>().count,
                    ),
                ),
                CheckboxSpecialFilter(
                    values = CheckboxValues(
                        purpose = "uncensored-filter",
                        checked = filters.firstInstance<UncensoredFilter>().state,
                    ),
                ),
                CheckboxSpecialFilter(
                    values = CheckboxValues(
                        purpose = "unread-filter",
                        checked = false,
                    ),
                ),
            ),
            sorting = filters.firstInstance<SortFilter>().sort,
        ).toJsonString()

        val body = FormBody.Builder()
            .add("action", "advanced_search")
            .add("subAction", "search_query")
            .add("request", data)
            .add("offset", ((page - 1) * 10).toString())
            .build()

        val response = client.post("$baseUrl/wp-admin/admin-ajax.php", ajaxHeaders, body)
            .parseAs<Data<AdvancedSearchResponse>>()

        if (!response.success && response.data.message?.contains("captcha", ignoreCase = true) == true) {
            throw Exception("Captcha Required! Open advanced search in WebView and solve the captcha")
        }

        val mangas = response.data.posts.map {
            val element = Jsoup.parseBodyFragment(it, baseUrl)

            SManga.create().apply {
                url = element.selectFirst("a[href*=/manga/]")!!.absUrl("href")
                    .toHttpUrl().pathSegments[1]
                title = element.selectFirst(".title")!!.ownText().trim()
                thumbnail_url = element.selectFirst(".thumb img")?.imgAttr()
            }
        }

        return MangasPage(mangas, response.data.more)
    }

    override fun getMangaUrl(manga: SManga): String {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addPathSegment(manga.url)
            addPathSegment("")
        }.build().toString()

        return url
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        val updatedManga = manga.apply {
            title = document.selectFirst(".manga-title")!!.text()
            author = document.select(".manga-term-content a[href*=/artists/]").eachText().joinToString()
            artist = author
            status = SManga.COMPLETED
            update_strategy = UpdateStrategy.ONLY_FETCH_ONCE
            thumbnail_url = document.selectFirst(".manga-thumb img")?.imgAttr()
            description = buildString {
                document.selectFirst(".manga-subtitle")?.text()?.also {
                    append("Alternative Name: ")
                    append(it)
                    append("\n")
                }
                document.selectFirst(".pre-meta .counter")?.text()?.also(::appendLine)
                document.selectFirst(".pre-meta .manga-views")?.text()?.also(::appendLine)
                document.selectFirst(".pre-meta .manga-updated")?.text()?.also(::appendLine)
                document.selectFirst(".rating-buttons span#likes")?.text()?.also {
                    append("Dislikes: ")
                    append(it)
                    append("\n")
                }
                document.selectFirst(".rating-buttons span#dislikes")?.text()?.also {
                    append("Likes: ")
                    append(it)
                    append("\n")
                }
                document.select(".manga-term-content:not(:has(> a[href*=/tag]))").forEach { div ->
                    val name = div.previousElementSibling()?.takeIf { it.hasClass("manga-term-name") }
                        ?: return@forEach
                    val content = div.selectFirst("a")?.text()
                        ?: return@forEach
                    append(name.text())
                    append(": ")
                    append(content)
                    append("\n")
                }
                genre = buildList {
                    document.select(".manga-term-content a[href*=/genres/]").mapTo(this) { it.text() }
                    document.select(".manga-term-content a[href*=/languages/]").mapTo(this) { it.text() }
                    document.select(".manga-term-content a[href*=/tag/]").mapTo(this) { it.text() }
                }.joinToString()
            }
        }

        val date = document.selectFirst(".yoast-schema-graph[type=application/ld+json]")
            ?.data()?.parseAs<SchemaGraph>()?.graph?.firstOrNull { it.datePublished != null }?.datePublished
        val updatedChapters = listOf(
            SChapter.create().apply {
                url = manga.url
                name = "Chapter"
                date_upload = Instant.tryParse(date)
            },
        )

        return SMangaUpdate(updatedManga, updatedChapters)
    }

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()

        return document.select(".related-entry a").map { element ->
            SManga.create().apply {
                url = element.absUrl("href").toHttpUrl().pathSegments[1]
                title = element.selectFirst(".related-title")!!.ownText().trim()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("manga")
            addPathSegment(chapter.url)
            addPathSegment("read")
            addPathSegment("")
        }.build().toString()

        return url
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.get(getChapterUrl(chapter)).asJsoup()

        countViews(document)

        return document.select(".gallery-item > dt > img").mapIndexed { index, img ->
            Page(index, imageUrl = img.imgAttr())
        }
    }

    private fun countViews(document: Document) {
        val postId = document.selectFirst("body[class*=postid-]")
            ?.classNames()
            ?.firstOrNull { it.startsWith("postid-") }
            ?.substringAfter("postid-")
            ?: return

        val body = FormBody.Builder()
            .add("action", "addview")
            .add("postID", postId)
            .build()

        val request = Request.Builder()
            .url("$baseUrl/wp-admin/admin-ajax.php")
            .headers(ajaxHeaders)
            .post(body)
            .build()

        // fire and forget so page loading isn't delayed
        client.newCall(request)
            .enqueue(
                object : Callback {
                    override fun onFailure(call: Call, e: IOException) {}
                    override fun onResponse(call: Call, response: Response) = response.close()
                },
            )
    }

    private fun Element.imgAttr(): String? = when {
        hasAttr("data-src") && attr("data-src").isNotBlank() -> absUrl("data-src")
        hasAttr("src") && attr("src").isNotBlank() -> absUrl("src")
        else -> null
    }
}
