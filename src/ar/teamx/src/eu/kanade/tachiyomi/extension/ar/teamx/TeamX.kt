package eu.kanade.tachiyomi.extension.ar.teamx

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
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import rx.Observable
import rx.schedulers.Schedulers
import kotlin.time.Duration.Companion.seconds

@Source
abstract class TeamX : HttpSource() {

    override val supportsLatest = true

    override val client = network.client
        .newBuilder()
        .connectTimeout(15.seconds)
        .readTimeout(30.seconds)
        .rateLimit(10, 1.seconds)
        .build()

    /**
     * Whether filters have been fetched
     */
    private var filtersFetched: Boolean = false

    private val nextPageSelector = "a[rel=next]"

    private val thumbnailSuffix = "thumbnail_"

    // ============================== Popular ==============================

    private val popularMangaSelector = "div.listupd div.bsx"

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series/" + if (page > 1) "?page=$page" else "", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val entries = document.select(popularMangaSelector).map { element ->
            SManga.create().apply {
                title = element.selectFirst("a")!!.attr("title")
                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.let {
                    it.absUrl("data-src").ifEmpty { it.absUrl("src") }
                }
            }
        }

        val hasNextPage = document.selectFirst(nextPageSelector) != null

        fetchFiltersIfNeeded(document)

        return MangasPage(entries, hasNextPage)
    }

    // ============================== Latest ===============================

    private val titlesAdded = mutableSetOf<String>()

    override fun latestUpdatesRequest(page: Int): Request {
        if (page == 1) titlesAdded.clear()

        return GET(baseUrl + if (page > 1) "?page=$page" else "", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val unfilteredManga = document.select("div.last-chapter div.box")

        val mangaList = unfilteredManga
            .map { element ->
                SManga.create().apply {
                    val linkElement = element.selectFirst("div.info a")!!
                    title = linkElement.selectFirst("h3")!!.text()
                    setUrlWithoutDomain(linkElement.absUrl("href"))
                    thumbnail_url = element.selectFirst("div.imgu img")
                        ?.absUrl("src")
                        ?.replace(thumbnailSuffix, "")
                }
            }.distinctBy {
                it.title
            }.filter {
                !titlesAdded.contains(it.title)
            }

        titlesAdded.addAll(mangaList.map { it.title })

        return MangasPage(mangaList, document.selectFirst(nextPageSelector) != null)
    }

    // ============================== Search ===============================

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (!query.startsWith("http")) {
            return super.fetchSearchManga(page, query, filters)
        }

        val baseHost = baseUrl.toHttpUrl().host
        val seriesUrl = query.toHttpUrl()

        if (seriesUrl.host != baseHost) throw Exception("Unsupported URL")
        val segment = seriesUrl.pathSegments
            .getOrNull(1)
            ?.takeIf { it.isNotEmpty() }
            ?: throw Exception("Invalid URL format")

        val manga = SManga.create().apply { url = "/series/$segment" }

        return fetchMangaDetails(manga).map {
            MangasPage(
                listOf(
                    it.apply {
                        url = manga.url
                        initialized = true
                    },
                ),
                false,
            )
        }
    }

    override fun searchMangaRequest(
        page: Int,
        query: String,
        filters: FilterList,
    ): Request {
        if (query.isNotEmpty()) {
            val url = "$baseUrl/ajax/search".toHttpUrl().newBuilder()
                .addQueryParameter("keyword", query)
                .build()
            return GET(url, headers)
        }

        val url = "$baseUrl/series".toHttpUrl().newBuilder()
        url.addQueryParameter("page", page.toString())

        filters.firstInstanceOrNull<TypeFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
            url.addQueryParameter("type", it)
        }
        filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
            url.addQueryParameter("status", it)
        }
        filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()?.takeIf { it.isNotEmpty() }?.let {
            url.addQueryParameter("genre", it)
        }

        return GET(url.build(), headers)
    }

    private val searchMangaSelector = "a.items-center, $popularMangaSelector"

    override fun searchMangaParse(response: Response): MangasPage {
        if ("series" in response.request.url.pathSegments) {
            return popularMangaParse(response)
        }
        val document = response.asJsoup()
        val mangas = document.select(searchMangaSelector).map { element ->
            SManga.create().apply {
                title = element.selectFirst("h4")!!.text()
                thumbnail_url = element.selectFirst("img")?.absUrl("src")
                    ?.replace(thumbnailSuffix, "")
                setUrlWithoutDomain(element.absUrl("href"))
            }
        }
        return MangasPage(mangas, false)
    }

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("div.author-info-title h1")!!.text()

        var desc = document.select("div.review-content").text()
        if (desc.isEmpty()) {
            desc = document.select("div.review-content p").text()
        }
        description = desc

        genre = document.select("div.review-author-info a").joinToString { it.text() }
        thumbnail_url = document.selectFirst("div.text-right img")?.absUrl("src")
        status = document.selectFirst(".full-list-info > small:first-child:contains(الحالة) + small")
            ?.text()
            .toStatus()
        author = document.selectFirst(".full-list-info > small:first-child:contains(الرسام) + small")
            ?.text()
            ?.takeIf { it != "غير معروف" }
    }

    // ============================= Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = client.newCall(chapterListRequest(manga))
        .asObservableSuccess()
        .flatMap { response ->
            val url = response.request.url
            val document = response.asJsoup()
            val firstPageChapters = parseChapterElements(document)

            val lastPage = document.select("ul.pagination a.page-link")
                .mapNotNull { it.text().toIntOrNull() }
                .maxOrNull() ?: 1

            if (lastPage <= 1) {
                Observable.just(firstPageChapters)
            } else {
                val remainingPages = (2..lastPage).map { page ->
                    client.newCall(GET("$url?page=$page", headers))
                        .asObservableSuccess()
                        .subscribeOn(Schedulers.io())
                        .map { parseChapterElements(it.asJsoup()) }
                }

                Observable.zip(remainingPages) { results ->
                    val allChapters = mutableListOf<SChapter>()
                    allChapters += firstPageChapters
                    results.forEach {
                        @Suppress("UNCHECKED_CAST")
                        allChapters += it as List<SChapter>
                    }
                    allChapters
                }
            }
        }

    private fun parseChapterElements(document: Document): List<SChapter> {
        return document.select("div.chapter-card").mapNotNull { element ->
            if (element.selectFirst("span.locked") != null) return@mapNotNull null
            SChapter.create().apply {
                val chpNum = element.attr("data-number")
                val chpTitle = element.selectFirst("div.chapter-info div.chapter-title")?.text()

                name = buildString {
                    append("الفصل $chpNum")
                    chpTitle?.takeIf {
                        it.isNotEmpty() &&
                            it != chpNum &&
                            it != "الفصل $chpNum" &&
                            it != "الفصل رقم $chpNum"
                    }?.let { append(" - $it") }
                } + "\u200F"

                // data-date is Unix timestamp (seconds)
                date_upload = element.attr("data-date")
                    .toLongOrNull()
                    ?.times(1000)
                    ?: 0L

                setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document
            .select("div.image_list canvas[data-src], div.image_list img[src]")
            .mapIndexed { i, element ->
                val url = when {
                    element.hasAttr("src") -> element.absUrl("src")
                    else -> element.absUrl("data-src")
                }
                Page(i, imageUrl = url)
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ==============================

    private fun fetchFiltersIfNeeded(document: Document) {
        if (filtersFetched) return

        fun load(selector: String, target: MutableList<Pair<String?, String>>) {
            document.select(selector).forEach {
                target.add(it.attr("value") to it.text())
            }
        }

        load("select#select_genre option", genreFilters)
        load("select#select_type option", typeFilters)
        load("select#select_state option", statusFilters)

        filtersFetched = true
    }

    override fun getFilterList() = FilterList(
        if (filtersFetched) {
            listOf(
                Filter.Header("NOTE: Filters are ignored when using text search."),
                Filter.Separator(),
                TypeFilter(typeFilters),
                StatusFilter(statusFilters),
                GenreFilter(genreFilters),
            )
        } else {
            listOf(
                Filter.Header(
                    "Filters are not loaded yet.\n" +
                        "Open Popular Manga and press 'Reset' to load filters.",
                ),
            )
        },
    )

    private class TypeFilter(
        vals: List<Pair<String?, String>>,
    ) : UriPartFilter("Type", vals)

    private class StatusFilter(
        vals: List<Pair<String?, String>>,
    ) : UriPartFilter("Status", vals)

    private class GenreFilter(
        vals: List<Pair<String?, String>>,
    ) : UriPartFilter("Category", vals)

    private val typeFilters: MutableList<Pair<String?, String>> = mutableListOf()
    private val statusFilters: MutableList<Pair<String?, String>> = mutableListOf()
    private val genreFilters: MutableList<Pair<String?, String>> = mutableListOf()

    open class UriPartFilter(
        displayName: String,
        private val vals: List<Pair<String?, String>>,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.second }.toTypedArray(),
    ) {
        fun toUriPart() = vals[state].first
    }

    // ============================= Utilities =============================

    private fun String?.toStatus() = when (this) {
        "مستمرة", "قادم قريبًا" -> SManga.ONGOING // "coming soon"
        "مكتمل" -> SManga.COMPLETED
        "متوقف" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }
}
