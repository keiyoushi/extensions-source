package eu.kanade.tachiyomi.multisrc.mmrcms

import android.annotation.SuppressLint
import android.util.Log
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.i18n.Intl
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min

/**
 * dateFormat The date format used for parsing chapter dates.
 * itemPath The path used in the URL for entries.
 * fetchFilterOptions Whether to fetch filtering options (categories, types, tags).
 * supportsAdvancedSearch Whether the source supports advanced search under /advanced-search.
 * detailsTitleSelector Selector for the entry's title in its details page.
 * chapterNamePrefix A word that always precedes the chapter title, e.g. "Scan "
 * chapterString The word for "Chapter" in the source's language.
 */
abstract class MMRCMS : HttpSource() {

    protected open val dateFormat: SimpleDateFormat = SimpleDateFormat("d MMM. yyyy", Locale.US)

    protected open val itemPath: String = "manga"

    protected open val fetchFilterOptions: Boolean = true

    protected open val supportsAdvancedSearch: Boolean = true

    protected open val detailsTitleSelector: String = ".listmanga-header, .widget-title"

    protected open val chapterNamePrefix: String = ""

    protected open val chapterString: String = when (lang) {
        "es" -> "Capítulo"
        "fr" -> "Chapitre"
        else -> "Chapter"
    }

    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    protected val intl = Intl(
        lang,
        setOf("en", "es"),
        "en",
        this::class.java.classLoader!!,
    )

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/filterList?page=$page&sortBy=views&asc=false")

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun popularMangaSelector(): String = searchMangaSelector()

    protected open fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    protected open fun popularMangaNextPageSelector(): String? = searchMangaNextPageSelector()

    /**
     * A cache of all titles that have already appeared in latest updates.
     */
    private val latestTitles = mutableSetOf<String>()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/latest-release?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (response.request.url.queryParameter("page") == "1") {
            latestTitles.clear()
        }

        val manga = document.select(latestUpdatesSelector()).mapNotNull {
            val item = latestUpdatesFromElement(it)

            if (latestTitles.contains(item.url)) {
                null
            } else {
                latestTitles.add(item.url)
                item
            }
        }
        val hasNextPage = latestUpdatesNextPageSelector()?.let {
            document.selectFirst(it) != null
        } ?: false

        return MangasPage(manga, hasNextPage)
    }

    protected open fun latestUpdatesSelector() = "div.mangalist div.manga-item"

    protected open fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    protected open fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    protected var searchDirectory = emptyList<SuggestionDto>()

    private val searchTokenRegex = Regex("""['"]_token['"]\s*:\s*['"]([0-9A-Za-z]+)['"]""")

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.isNotEmpty()) {
            if (page == 1) {
                return client.newCall(searchMangaRequest(page, query, filters))
                    .asObservableSuccess()
                    .map { searchMangaParse(it) }
            }
            return Observable.just(parseSearchDirectory(page))
        }

        if (supportsAdvancedSearch) {
            return client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { response ->
                    val document = response.asJsoup()
                    val fragment = response.request.url.fragment ?: return@map null
                    val body = FormBody.Builder().apply {
                        val fragmentPage = fragment.substringAfter("page=").substringBefore("&")

                        add("params", fragment.substringAfter("page=$fragmentPage&"))
                        add("page", fragmentPage)

                        document.selectFirst("script:containsData(_token)")?.data()?.let {
                            searchTokenRegex.find(it)?.groupValues?.get(1)?.let { token ->
                                add("_token", token)
                            }
                        }
                    }.build()
                    POST("$baseUrl/advSearchFilter", headers, body)
                }
                .flatMap { request ->
                    if (request == null) {
                        Observable.just(MangasPage(emptyList(), false))
                    } else {
                        client.newCall(request).asObservableSuccess().map { response ->
                            val resDoc = response.asJsoup()
                            val mangas = resDoc.select(searchMangaSelector()).map { searchMangaFromElement(it) }
                            val hasNextPage = searchMangaNextPageSelector()?.let { resDoc.selectFirst(it) != null } ?: false
                            MangasPage(mangas, hasNextPage)
                        }
                    }
                }
        }

        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { searchMangaParse(it) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            val filterList = filters.ifEmpty { getFilterList() }

            if (query.isNotEmpty()) {
                addPathSegment("search")
                addQueryParameter("query", query)
            } else {
                addPathSegment(if (supportsAdvancedSearch) "advanced-search" else "filterList")
                addQueryParameter("page", page.toString())
                filterList.filterIsInstance<UriFilter>().forEach { it.addToUri(this) }
            }
        }.build()

        return if (query.isEmpty() && supportsAdvancedSearch) {
            GET(url.toString().replaceFirst("?", "#"), headers)
        } else {
            GET(url, headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val searchType = response.request.url.pathSegments.last()

        if (searchType == "filterList") {
            val document = response.asJsoup()
            val mangas = document.select(searchMangaSelector()).map { searchMangaFromElement(it) }
            val hasNextPage = searchMangaNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
            return MangasPage(mangas, hasNextPage)
        }

        searchDirectory = response.parseAs<SearchResultDto>().suggestions
        return parseSearchDirectory(1)
    }

    protected open fun searchMangaSelector(): String = "div.media"

    protected open fun searchMangaFromElement(element: Element) = SManga.create().apply {
        val anchor = element.selectFirst(".media-heading a, .manga-heading a")!!

        setUrlWithoutDomain(anchor.absUrl("href"))
        title = anchor.text()
        thumbnail_url = guessCover(url, element.selectFirst("img")?.imgAttr())
    }

    protected open fun searchMangaNextPageSelector(): String? = ".pagination a[rel=next]"

    protected open fun parseSearchDirectory(page: Int): MangasPage {
        val manga = searchDirectory.subList((page - 1) * 24, min(page * 24, searchDirectory.size))
            .map {
                SManga.create().apply {
                    url = "/$itemPath/${it.data}"
                    title = it.value
                    thumbnail_url = guessCover(url, null)
                }
            }
        val hasNextPage = (page + 1) * 24 <= searchDirectory.size

        return MangasPage(manga, hasNextPage)
    }

    protected val detailAuthor = hashSetOf("author(s)", "autor(es)", "auteur(s)", "著作", "yazar(lar)", "mangaka(lar)", "pengarang/penulis", "pengarang", "penulis", "autor", "المؤلف", "перевод", "autor/autorzy")
    protected val detailArtist = hashSetOf("artist(s)", "artiste(s)", "sanatçi(lar)", "artista(s)", "artist(s)/ilustrator", "الرسام", "seniman", "rysownik/rysownicy", "artista")
    protected val detailGenre = hashSetOf("categories", "categorías", "catégories", "ジャンル", "kategoriler", "categorias", "kategorie", "التصنيفات", "жанр", "kategori", "tagi", "género")
    protected val detailStatus = hashSetOf("status", "statut", "estado", "状態", "durum", "الحالة", "статус")
    protected val detailStatusComplete = hashSetOf("complete", "مكتملة", "complet", "completo", "zakończone", "concluído", "finalizado")
    protected val detailStatusOngoing = hashSetOf("ongoing", "مستمرة", "en cours", "em lançamento", "prace w toku", "ativo", "em andamento", "activo")
    protected val detailStatusDropped = hashSetOf("dropped")

    override fun mangaDetailsParse(response: Response): SManga = mangaDetailsParse(response.asJsoup())

    @SuppressLint("DefaultLocale")
    protected open fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst(detailsTitleSelector)!!.text()
        thumbnail_url = guessCover(
            document.location(),
            document.selectFirst(".row img.img-responsive")?.imgAttr(),
        )
        description = document.select(".row .well").let {
            it.select("h5").remove()
            it.textWithNewlines()
        }

        document.select(".row .dl-horizontal dt").forEach { element ->
            when (element.text().lowercase().removeSuffix(":")) {
                in detailAuthor -> author = element.nextElementSibling()!!.text()

                in detailArtist -> artist = element.nextElementSibling()!!.text()

                in detailGenre -> genre = element.nextElementSibling()!!.select("a").joinToString {
                    it.text()
                }

                in detailStatus -> status = when (element.nextElementSibling()!!.text().lowercase()) {
                    in detailStatusComplete -> SManga.COMPLETED
                    in detailStatusOngoing -> SManga.ONGOING
                    in detailStatusDropped -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val title = document.selectFirst(detailsTitleSelector)!!.text()

        return document.select(chapterListSelector()).map { chapterFromElement(it, title) }
    }

    protected open fun chapterListSelector(): String = "ul.chapters > li:not(.btn)"

    protected open fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

    protected open fun chapterFromElement(element: Element, mangaTitle: String): SChapter = SChapter.create().apply {
        val titleWrapper = element.selectFirst(".chapter-title-rtl")!!
        val anchor = titleWrapper.selectFirst("a")!!

        setUrlWithoutDomain(anchor.absUrl("href"))
        name = cleanChapterName(mangaTitle, titleWrapper.text())
        val dateStr = element.selectFirst(".date-chapter-title-rtl")?.text()
        date_upload = dateFormat.tryParse(dateStr)
    }

    /**
     * Function to clean up chapter names. Mostly useful for sites that
     * don't know what a chapter title is and do "One Piece 1234 : Chapter 1234".
     */
    protected open fun cleanChapterName(mangaTitle: String, name: String): String {
        val initialName = name.replaceFirst(chapterNamePrefix + mangaTitle, chapterString)

        val splits = initialName.split(":", limit = 2).map { it.trim() }

        return if (splits.size < 2 || splits[0] == splits[1]) {
            splits[0]
        } else {
            "${splits[0]}: ${splits[1]}"
        }
    }

    override fun pageListParse(response: Response): List<Page> = pageListParse(response.asJsoup())

    protected open fun pageListParse(document: Document): List<Page> = document.select("#all > img.img-responsive").mapIndexed { i, it ->
        Page(i, imageUrl = it.imgAttr())
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getFilterList(): FilterList {
        fetchFilterOptions()

        val filters = buildList {
            add(Filter.Header(intl["filter_warning"]))
            if (fetchFilterOptions && fetchFiltersStatus != FetchFilterStatus.FETCHED) {
                add(Filter.Header(intl["filter_missing_warning"]))
            }
            add(Filter.Separator())

            if (supportsAdvancedSearch) {
                if (categories.isNotEmpty()) {
                    add(
                        UriMultiSelectFilter(
                            intl["category_filter_title"],
                            "categories[]",
                            categories.toTypedArray(),
                        ),
                    )
                }

                if (statuses.isNotEmpty()) {
                    add(
                        UriMultiSelectFilter(
                            intl["status_filter_title"],
                            "status[]",
                            statuses.toTypedArray(),
                        ),
                    )
                }

                if (tags.isNotEmpty()) {
                    add(
                        UriMultiSelectFilter(
                            intl["type_filter_title"],
                            "types[]",
                            tags.toTypedArray(),
                        ),
                    )
                }

                add(TextFilter(intl["year_filter_title"], "release"))
                add(TextFilter(intl["author_filter_title"], "author"))
            } else {
                if (categories.isNotEmpty()) {
                    add(
                        UriPartFilter(
                            intl["category_filter_title"],
                            "cat",
                            arrayOf(
                                "Any" to "",
                                *categories.toTypedArray(),
                            ),
                        ),
                    )
                }

                add(UriPartFilter(intl["title_begins_with_filter_title"], "alpha", alphaOptions))

                if (tags.isNotEmpty()) {
                    add(
                        UriPartFilter(
                            intl["tag_filter_title"],
                            "tag",
                            arrayOf(
                                "Any" to "",
                                *tags.toTypedArray(),
                            ),
                        ),
                    )
                }

                if (sortOptions.isNotEmpty()) {
                    add(SortFilter(intl, sortOptions))
                }
            }
        }

        return FilterList(filters)
    }

    private val alphaOptions by lazy {
        arrayOf(
            "Any" to "",
            *"#ABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray().map {
                Pair(it.toString(), it.toString())
            }.toTypedArray(),
        )
    }
    private var categories = emptyList<Pair<String, String>>()
    private var statuses = emptyList<Pair<String, String>>()
    private var tags = emptyList<Pair<String, String>>()
    private var sortOptions = emptyArray<Pair<String, String>>()

    private var fetchFiltersStatus = FetchFilterStatus.NOT_FETCHED
    private var fetchFiltersAttempts = 0
    private val scope = CoroutineScope(Dispatchers.IO)

    protected open fun fetchFilterOptions() {
        if (!fetchFilterOptions) {
            return
        }

        if (fetchFiltersStatus != FetchFilterStatus.NOT_FETCHED || fetchFiltersAttempts >= 3) {
            return
        }

        fetchFiltersStatus = FetchFilterStatus.FETCHING
        fetchFiltersAttempts++
        scope.launch {
            try {
                if (supportsAdvancedSearch) {
                    val document = client.newCall(GET("$baseUrl/advanced-search", headers))
                        .await()
                        .asJsoup()

                    categories = document.select("select[name='categories[]'] option").map {
                        it.text() to it.attr("value")
                    }
                    statuses = document.select("select[name='status[]'] option").map {
                        it.text() to it.attr("value")
                    }
                    tags = document.select("select[name='types[]'] option").map {
                        it.text() to it.attr("value")
                    }
                } else {
                    val document = client.newCall(GET("$baseUrl/$itemPath-list", headers))
                        .await()
                        .asJsoup()

                    categories = document.select("a.category").map {
                        it.text() to it.absUrl("href").toHttpUrl().queryParameter("cat")!!
                    }
                    tags = document.select("div.tag-links a").map {
                        it.text() to it.absUrl("href").toHttpUrl().pathSegments.last()
                    }
                    sortOptions = document.select("#sort-types label:has(input)").map {
                        it.ownText() to it.selectFirst("input")!!.id()
                    }.toTypedArray()
                }

                fetchFiltersStatus = FetchFilterStatus.FETCHED
            } catch (e: Exception) {
                fetchFiltersStatus = FetchFilterStatus.NOT_FETCHED
                Log.e("MMRCMS/$name", "Could not fetch filters", e)
            }
        }
    }

    protected fun guessCover(mangaUrl: String, url: String?): String = if (url == null || url.endsWith("no-image.png")) {
        "$baseUrl/uploads/manga/${mangaUrl.substringAfterLast('/')}/cover/cover_250x350.jpg"
    } else {
        url
    }

    protected fun Element.imgAttr(): String = when {
        hasAttr("data-background-image") -> absUrl("data-background-image")
        hasAttr("data-cfsrc") -> absUrl("data-cfsrc")
        hasAttr("data-lazy-src") -> absUrl("data-lazy-src")
        hasAttr("data-src") -> absUrl("data-src")
        else -> absUrl("src")
    }

    protected fun Elements.textWithNewlines() = run {
        select("p, br").prepend("\\n")
        text().replace("\\n", "\n").replace("\n ", "\n")
    }
}

private enum class FetchFilterStatus {
    NOT_FETCHED,
    FETCHED,
    FETCHING,
}
