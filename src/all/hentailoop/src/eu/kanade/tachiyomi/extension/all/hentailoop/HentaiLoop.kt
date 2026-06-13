package eu.kanade.tachiyomi.extension.all.hentailoop

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.UpdateStrategy
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import rx.Observable
import java.lang.UnsupportedOperationException
import java.text.SimpleDateFormat
import java.util.Locale

private const val DOMAIN = "hentailoop.com"

class HentaiLoop : HttpSource() {
    override val name = "HentaiLoop"
    override val lang = "all"
    override val supportsLatest = true

    @Volatile
    private var captchaRequired = false
    override val baseUrl get() = if (captchaRequired) {
        "https://$DOMAIN/manga-service/advanced-search/"
    } else {
        "https://$DOMAIN"
    }

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "https://$DOMAIN/")

    override fun popularMangaRequest(page: Int): Request {
        val url = HttpUrl.Builder().apply {
            scheme("https")
            host(DOMAIN)
            addPathSegment("manga")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addPathSegment("")
            addQueryParameter("sortmanga", "views")
        }.build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
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

    override fun latestUpdatesRequest(page: Int): Request {
        val url = HttpUrl.Builder().apply {
            scheme("https")
            host(DOMAIN)
            addPathSegment("manga")
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addPathSegment("")
            addQueryParameter("sortmanga", "date")
        }.build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList(): FilterList {
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

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            return deepLink(query)
        }

        val activeFilter = filters.findActiveFilter()

        // multiple active filters
        if (activeFilter == null) {
            return super.fetchSearchManga(page, query, filters)
        }

        val (directory, slug) = activeFilter

        // query search is active
        if (query.isNotBlank()) {
            // no other filter active
            return if (slug == null) {
                quickSearch(query)
            } else {
                // filters + query search
                super.fetchSearchManga(page, query, filters)
            }
        }

        // one filter (or default list) active with no query search
        return getMangaList(directory, slug, filters, page)
    }

    private fun deepLink(url: String): Observable<MangasPage> {
        val httpUrl = url.toHttpUrl()
        if (httpUrl.host == DOMAIN && httpUrl.pathSegments[0] == "manga" && httpUrl.pathSegments.size > 1) {
            val tmpManga = SManga.create().apply {
                this@apply.url = httpUrl.pathSegments[1]
            }

            return fetchMangaDetails(tmpManga)
                .map { MangasPage(listOf(it), hasNextPage = false) }
        }

        throw Exception("Unsupported Url")
    }

    private fun quickSearch(query: String): Observable<MangasPage> {
        val body = FormBody.Builder()
            .add("action", "nativeSearch")
            .add("subAction", "search")
            .add("query", query.trim())
            .build()
        val url = "https://$DOMAIN/wp-admin/admin-ajax.php"
        val headers = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        return client.newCall(POST(url, headers, body))
            .asObservableSuccess()
            .map {
                val data = it.parseAs<Data<QuerySearchResponse>>()
                val mangas = data.data.posts.map { manga ->
                    SManga.create().apply {
                        this@apply.url = manga.link.toHttpUrl().pathSegments[1]
                        title = manga.title
                        thumbnail_url = manga.thumb
                    }
                }

                MangasPage(mangas, hasNextPage = false)
            }
    }

    private fun getMangaList(directory: String, slug: String?, filters: FilterList, page: Int): Observable<MangasPage> {
        val url = HttpUrl.Builder().apply {
            scheme("https")
            host(DOMAIN)
            addPathSegment(directory)
            if (slug != null) {
                addPathSegment(slug)
            }
            if (page > 1) {
                addPathSegment("page")
                addPathSegment(page.toString())
            }
            addPathSegment("")
            addQueryParameter("sortmanga", filters.firstInstance<SortFilter>().sort)
        }.build()

        return client.newCall(GET(url, headers))
            .asObservableSuccess()
            .map(::popularMangaParse)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
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
                CheckboxFilter(
                    values = CheckboxValues(
                        purpose = "uncensored-filter",
                        checked = filters.firstInstance<UncensoredFilter>().state,
                    ),
                ),
                CheckboxFilter(
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

        val headers = headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        return POST("https://$DOMAIN/wp-admin/admin-ajax.php", headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val data = response.parseAs<Data<AdvancedSearchResponse>>()

        if (!data.success && data.data.message?.contains("captcha", ignoreCase = true) == true) {
            captchaRequired = true
            throw Exception("Captcha Required! Open WebView and click views button")
        } else {
            captchaRequired = false
        }

        val mangas = data.data.posts.map {
            val element = Jsoup.parseBodyFragment(it, baseUrl)

            SManga.create().apply {
                url = element.selectFirst("a[href*=/manga/]")!!.absUrl("href")
                    .toHttpUrl().pathSegments[1]
                title = element.selectFirst(".title")!!.ownText().trim()
                thumbnail_url = element.selectFirst(".thumb img")?.imgAttr()
            }
        }

        return MangasPage(mangas, data.data.more)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(getMangaUrl(manga), headers)

    override fun getMangaUrl(manga: SManga): String {
        val url = HttpUrl.Builder().apply {
            scheme("https")
            host(DOMAIN)
            addPathSegment("manga")
            addPathSegment(manga.url)
            addPathSegment("")
        }.build().toString()

        return url
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            url = response.request.url.pathSegments[1]
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
    }

    override fun relatedMangaListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun relatedMangaListParse(response: Response): List<SManga> {
        val document = response.asJsoup()

        return document.select(".related-entry a").map { element ->
            SManga.create().apply {
                url = element.absUrl("href").toHttpUrl().pathSegments[1]
                title = element.selectFirst(".related-title")!!.ownText().trim()
                thumbnail_url = element.selectFirst("img")?.imgAttr()
            }
        }
    }

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val date = document.selectFirst(".yoast-schema-graph[type=application/ld+json]")
            ?.data()?.parseAs<SchemaGraph>()?.graph?.firstOrNull { it.datePublished != null }?.date
        return listOf(
            SChapter.create().apply {
                url = response.request.url.pathSegments[1]
                name = "Chapter"
                date_upload = dateFormat.tryParse(date)
            },
        )
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.ROOT)

    override fun pageListRequest(chapter: SChapter): Request = GET(getChapterUrl(chapter), headers)

    override fun getChapterUrl(chapter: SChapter): String {
        val url = HttpUrl.Builder().apply {
            scheme("https")
            host(DOMAIN)
            addPathSegment("manga")
            addPathSegment(chapter.url)
            addPathSegment("read")
            addPathSegment("")
        }.build().toString()

        return url
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        return document.select(".gallery-item > dt > img").mapIndexed { index, img ->
            Page(index, imageUrl = img.imgAttr())
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String? = when {
        hasAttr("data-src") && attr("data-src").isNotBlank() -> absUrl("data-src")
        hasAttr("src") && attr("src").isNotBlank() -> absUrl("src")
        else -> null
    }
}
