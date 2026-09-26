package eu.kanade.tachiyomi.multisrc.mmrcms

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.lib.i18n.Intl
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import keiyoushi.utils.tryParseDate
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import java.time.format.DateTimeFormatter
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
abstract class MMRCMS : KeiSource() {

    protected open val dateFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM. yyyy", Locale.US)

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

    protected val intl = Intl(
        lang,
        setOf("en", "es"),
        "en",
        this::class.java.classLoader!!,
    )

    protected open fun popularMangaUrl(page: Int) = "$baseUrl/filterList?page=$page&sortBy=views&asc=false"

    override suspend fun getPopularManga(page: Int) = popularMangaParse(client.get(popularMangaUrl(page)).asJsoup())

    protected open fun popularMangaParse(document: Document): MangasPage {
        val mangas = document.select(popularMangaSelector()).map { popularMangaFromElement(it) }
        val hasNextPage = popularMangaNextPageSelector()?.let { document.selectFirst(it) != null } ?: false
        return MangasPage(mangas, hasNextPage)
    }

    protected open fun popularMangaSelector(): String = searchMangaSelector()

    protected open fun popularMangaFromElement(element: Element): SManga = searchMangaFromElement(element)

    protected open fun popularMangaNextPageSelector(): String? = searchMangaNextPageSelector()

    protected open fun latestUpdatesUrl(page: Int) = "$baseUrl/latest-release?page=$page"

    override suspend fun getLatestUpdates(page: Int) = latestUpdatesParse(client.get(latestUpdatesUrl(page)))

    protected open fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val manga = document.select(latestUpdatesSelector())
            .map { latestUpdatesFromElement(it) }
            .distinctBy { it.url }
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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotEmpty()) {
            if (page == 1) {
                return searchMangaParse(client.get(searchMangaUrl(page, query, filters)))
            }
            return parseSearchDirectory(page)
        }

        if (supportsAdvancedSearch) {
            val response = client.get(searchMangaUrl(page, query, filters))
            val fragment = response.request.url.fragment
            val document = response.asJsoup()
            fragment ?: return MangasPage(emptyList(), false)

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

            val resDoc = client.post("$baseUrl/advSearchFilter", body = body).asJsoup()
            val mangas = resDoc.select(searchMangaSelector()).map { searchMangaFromElement(it) }
            val hasNextPage = searchMangaNextPageSelector()?.let { resDoc.selectFirst(it) != null } ?: false
            return MangasPage(mangas, hasNextPage)
        }

        return searchMangaParse(client.get(searchMangaUrl(page, query, filters)))
    }

    protected open fun searchMangaUrl(page: Int, query: String, filters: FilterList): String {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (query.isNotEmpty()) {
                addPathSegment("search")
                addQueryParameter("query", query)
            } else {
                addPathSegment(if (supportsAdvancedSearch) "advanced-search" else "filterList")
                addQueryParameter("page", page.toString())
                filters.filterIsInstance<UriFilter>().forEach { it.addToUri(this) }
            }
        }.build()

        return if (query.isEmpty() && supportsAdvancedSearch) {
            url.toString().replaceFirst("?", "#")
        } else {
            url.toString()
        }
    }

    protected open fun searchMangaParse(response: Response): MangasPage {
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

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host || url.pathSegments.size < 2 || url.pathSegments.first() != itemPath) return null

        return mangaDetailsParse(client.get(url).asJsoup()).apply { setUrlWithoutDomain(url.toString()) }
    }

    // Details and chapters come from the same page
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(mangaDetailsParse(document), chapterListParse(document))
    }

    protected val detailAuthor = hashSetOf("author(s)", "autor(es)", "auteur(s)", "著作", "yazar(lar)", "mangaka(lar)", "pengarang/penulis", "pengarang", "penulis", "autor", "المؤلف", "перевод", "autor/autorzy")
    protected val detailArtist = hashSetOf("artist(s)", "artiste(s)", "sanatçi(lar)", "artista(s)", "artist(s)/ilustrator", "الرسام", "seniman", "rysownik/rysownicy", "artista")
    protected val detailGenre = hashSetOf("categories", "categorías", "catégories", "ジャンル", "kategoriler", "categorias", "kategorie", "التصنيفات", "жанр", "kategori", "tagi", "género")
    protected val detailStatus = hashSetOf("status", "statut", "estado", "状態", "durum", "الحالة", "статус")
    protected val detailStatusComplete = hashSetOf("complete", "مكتملة", "complet", "completo", "zakończone", "concluído", "finalizado")
    protected val detailStatusOngoing = hashSetOf("ongoing", "مستمرة", "en cours", "em lançamento", "prace w toku", "ativo", "em andamento", "activo", "publicándose", "publicandose")
    protected val detailStatusDropped = hashSetOf("dropped")

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

    protected open suspend fun chapterListParse(document: Document): List<SChapter> {
        val title = document.selectFirst(detailsTitleSelector)!!.text()

        return document.select(chapterListSelector()).map { chapterFromElement(it, title) }
    }

    protected open fun chapterListSelector(): String = "ul.chapters > li:not(.btn)"

    protected open fun chapterFromElement(element: Element, mangaTitle: String): SChapter = SChapter.create().apply {
        val titleWrapper = element.selectFirst(".chapter-title-rtl")!!
        val anchor = titleWrapper.selectFirst("a")!!

        setUrlWithoutDomain(anchor.absUrl("href"))
        name = cleanChapterName(mangaTitle, titleWrapper.text())
        val dateStr = element.selectFirst(".date-chapter-title-rtl")?.text()
        date_upload = dateFormat.tryParseDate(dateStr)
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

    override suspend fun getPageList(chapter: SChapter): List<Page> = pageListParse(client.get(getChapterUrl(chapter)).asJsoup())

    protected open fun pageListParse(document: Document): List<Page> = document.select("#all > img.img-responsive").mapIndexed { i, it ->
        Page(i, imageUrl = it.imgAttr())
    }

    override val supportsFilterFetching get() = fetchFilterOptions

    override suspend fun fetchFilterData(): JsonElement = if (supportsAdvancedSearch) {
        val document = client.get("$baseUrl/advanced-search").asJsoup()

        FilterData(
            categories = document.select("select[name='categories[]'] option").map {
                it.text() to it.attr("value")
            },
            statuses = document.select("select[name='status[]'] option").map {
                it.text() to it.attr("value")
            },
            tags = document.select("select[name='types[]'] option").map {
                it.text() to it.attr("value")
            },
            sortOptions = emptyList(),
        )
    } else {
        val document = client.get("$baseUrl/$itemPath-list").asJsoup()

        FilterData(
            categories = document.select("a.category").map {
                it.text() to it.absUrl("href").toHttpUrl().queryParameter("cat")!!
            },
            statuses = emptyList(),
            tags = document.select("div.tag-links a").map {
                it.text() to it.absUrl("href").toHttpUrl().pathSegments.last()
            },
            sortOptions = document.select("#sort-types label:has(input)").map {
                it.ownText() to it.selectFirst("input")!!.id()
            },
        )
    }.toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList {
        val filterData = data?.parseAs<FilterData>()
        val categories = filterData?.categories.orEmpty()
        val statuses = filterData?.statuses.orEmpty()
        val tags = filterData?.tags.orEmpty()
        val sortOptions = filterData?.sortOptions.orEmpty()

        val filters = buildList {
            add(Filter.Header(intl["filter_warning"]))
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
                    add(SortFilter(intl, sortOptions.toTypedArray()))
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
