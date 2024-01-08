package eu.kanade.tachiyomi.multisrc.mymangacms

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Evaluator
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

abstract class MyMangaCMS(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : ParsedHttpSource() {

    protected open val parseAuthorString = "Tác giả"
    protected open val parseAlternativeNameString = "Tên khác"
    protected open val parseAlternative2ndNameString = "Tên gốc"
    protected open val parseStatusString = "Tình trạng"
    protected open val parseStatusOngoingStringLowerCase = "đang tiến hành"
    protected open val parseStatusOnHoldStringLowerCase = "tạm ngưng"
    protected open val parseStatusCompletedStringLowerCase = "đã hoàn thành"

    /**
     * List of words to be removed when parsing alternative names
     */
    protected open val removeGenericWords = listOf("manhwa", "engsub")

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder().apply {
        rateLimit(3, 1)
        connectTimeout(1, TimeUnit.MINUTES)
        readTimeout(1, TimeUnit.MINUTES)
        writeTimeout(1, TimeUnit.MINUTES)
    }.build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder().apply {
        add("Referer", "$baseUrl/")
        add(
            "User-Agent",
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:101.0) Gecko/20100101 Firefox/101.0",
        )
    }

    //region Source settings

    open val timeZone = "Asia/Ho_Chi_Minh"

    open val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.US).apply {
        timeZone = TimeZone.getTimeZone(this@MyMangaCMS.timeZone)
    }

    open fun dateUpdatedParser(date: String): Long =
        runCatching { dateFormatter.parse(date)?.time }.getOrNull() ?: 0L

    private val floatingNumberRegex = Regex("""([+-]?(?:[0-9]*[.])?[0-9]+)""")

    /**
     * Regex for extracting URL from CSS `background-image: url()` property.
     *
     * - `url\(` matches the opening `url(`
     * - `['"]?` checks for the existence (or lack thereof) of single/double quotes
     * - `(.*?)` captures everything up to but not including the next quote
     * - `\)` to match the closing bracket.
     */
    private val backgroundImageRegex = Regex("""url\(['"]?(.*?)['"]?\)""")
    //endregion

    //region Popular

    override fun popularMangaRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("tim-kiem")
            addQueryParameter("sort", "top")
            addQueryParameter("page", page.toString())
        }.build().toString(),
    )

    override fun popularMangaSelector(): String = "div.thumb-item-flow.col-6.col-md-2"

    override fun popularMangaNextPageSelector(): String? =
        "div.pagination_wrap a.paging_item:last-of-type:not(.disabled)"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("a").first()!!.attr("abs:href"))
        title = element.select("div.thumb_attr.series-title a[title]").first()!!.text()
        thumbnail_url = element.select("div[data-bg]").first()!!.attr("data-bg")
    }
    //endregion

    //region Latest

    override fun latestUpdatesRequest(page: Int): Request = GET(
        baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("tim-kiem")
            addQueryParameter("sort", "update")
            addQueryParameter("page", page.toString())
        }.build().toString(),
    )

    override fun latestUpdatesSelector(): String = popularMangaSelector()

    override fun latestUpdatesNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun latestUpdatesFromElement(element: Element): SManga =
        popularMangaFromElement(element)
    //endregion

    //region Search

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when {
            query.startsWith(PREFIX_URL_SEARCH) -> {
                fetchMangaDetails(
                    SManga.create().apply {
                        url = query.removePrefix(PREFIX_URL_SEARCH).trim().replace(baseUrl, "")
                    },
                )
                    .map { MangasPage(listOf(it), false) }
            }
            else -> super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET(
            baseUrl.toHttpUrl().newBuilder().apply {
                val genres = mutableListOf<Int>()
                val genresEx = mutableListOf<Int>()
                addPathSegment("tim-kiem")
                addQueryParameter("page", page.toString())
                (if (filters.isEmpty()) getFilterList() else filters).forEach {
                    when (it) {
                        is GenreList -> it.state.forEach { genre ->
                            when (genre.state) {
                                Filter.TriState.STATE_INCLUDE -> genres.add(genre.id)
                                Filter.TriState.STATE_EXCLUDE -> genresEx.add(genre.id)
                                else -> {}
                            }
                        }
                        is Author -> if (it.state.isNotEmpty()) {
                            addQueryParameter("artist", it.state)
                        }
                        is Sort -> addQueryParameter("sort", it.toUriPart())
                        is Status -> if (it.state != 0) {
                            addQueryParameter("status", it.state.toString())
                        }
                        else -> {}
                    }
                }
                if (genresEx.isNotEmpty()) {
                    addQueryParameter("reject_genres", genresEx.joinToString(","))
                }
                if (genres.isNotEmpty()) {
                    addQueryParameter("accept_genres", genres.joinToString(","))
                }
                if (query.isNotEmpty()) {
                    addQueryParameter("q", query)
                }
            }.build().toString(),
        )

    override fun searchMangaSelector(): String = popularMangaSelector()

    override fun searchMangaNextPageSelector(): String? = popularMangaNextPageSelector()

    override fun searchMangaFromElement(element: Element): SManga =
        popularMangaFromElement(element)
    //endregion

    //region Manga details

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl${manga.url}")

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        setUrlWithoutDomain(
            document.select(".series-name-group a")
                .first()!!
                .attr("abs:href"),
        )
        title = document.select(".series-name").first()!!.text().trim()

        var alternativeNames: String = ""
        document.select(".info-item").forEach {
            val value = it.select(".info-value")
            when (it.select(".info-name").text().trim()) {
                "$parseAlternativeNameString:" -> alternativeNames += value.joinToString(", ") { name ->
                    removeGenericWords(name.text()).trim() + ", "
                }
                "$parseAlternative2ndNameString:" -> alternativeNames += value.joinToString(", ") { name ->
                    removeGenericWords(name.text()).trim() + ", "
                }
                "$parseAuthorString:" -> author = value.joinToString(", ") { auth ->
                    auth.text().trim()
                }
                "$parseStatusString:" -> status = when (value.first()!!.text().lowercase().trim()) {
                    parseStatusOngoingStringLowerCase -> SManga.ONGOING
                    parseStatusOnHoldStringLowerCase -> SManga.ON_HIATUS
                    parseStatusCompletedStringLowerCase -> SManga.COMPLETED
                    else -> SManga.UNKNOWN
                }
            }
        }

        val descElem = document.select(".summary-content")
        description = if (descElem.select("p").any()) {
            descElem.select("p").joinToString("\n") {
                it.run {
                    select(Evaluator.Tag("br")).prepend("\\n")
                    this.text()
                        .replace("\\n", "\n")
                        .replace("\n ", "\n")
                }
            }.trim()
        } else {
            descElem.text().trim()
        }

        if (alternativeNames.isNotEmpty()) {
            description = "$parseAlternativeNameString: ${alternativeNames}\n\n" + description
        }

        genre = document.select("a[href*=the-loai] span.badge")
            .joinToString(", ") { it.text().trim() }

        thumbnail_url = document
            .select("div.content.img-in-ratio")
            .first()!!
            .attr("style")
            .let { backgroundImageRegex.find(it)?.groups?.get(1)?.value }
    }

    private fun removeGenericWords(name: String): String {
        val locale = Locale.forLanguageTag(lang)

        return name.split(' ')
            .filterNot { word -> word.lowercase(locale) in removeGenericWords }
            .joinToString(" ")
    }
    //endregion

    //region Chapter list

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListSelector(): String = "ul.list-chapters > a"

    override fun chapterFromElement(element: Element): SChapter = throw Exception("Not used")

    private fun chapterFromElement(element: Element, scanlator: String?): SChapter =
        SChapter.create().apply {
            setUrlWithoutDomain(element.attr("abs:href"))
            name = element.select("div.chapter-name").first()!!.text()
            date_upload = dateUpdatedParser(
                element.select("div.chapter-time").first()!!.text(),
            )

            val match = floatingNumberRegex.find(name)
            chapter_number = if (name.lowercase().startsWith("vol")) {
                match?.groups?.get(2)
            } else {
                match?.groups?.get(1)
            }?.value?.toFloat() ?: -1f

            this.scanlator = scanlator
        }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val originalScanlator = document.select("div.fantrans-value a")
        val scanlator: String? = if (originalScanlator.isEmpty() ||
            originalScanlator.first()!!.text().trim().lowercase() == "đang cập nhật"
        ) {
            null
        } else {
            originalScanlator.first()!!.text().trim()
        }

        return document.select(chapterListSelector()).map { chapterFromElement(it, scanlator) }
    }
    //endregion

    //region Pages

    override fun pageListRequest(chapter: SChapter): Request = GET("$baseUrl${chapter.url}")

    override fun pageListParse(document: Document): List<Page> =
        document
            .select("div#chapter-content img")
            .filterNot { it.attr("abs:data-src").isNullOrEmpty() }
            .mapIndexed { index, elem -> Page(index, "", elem.attr("abs:data-src")) }

    override fun imageUrlParse(document: Document): String = throw Exception("Not used")
    //endregion

    //region Filters
    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        state: Int = 0,
    ) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }
    protected class Status(
        displayName: String = "Tình trạng",
        statusAll: String = "Tất cả",
        statusOngoing: String = "Đang tiến hành",
        statusOnHold: String = "Tạm ngưng",
        statusCompleted: String = "Hoàn thành",
    ) : Filter.Select<String>(
        displayName,
        arrayOf(
            statusAll,
            statusOngoing,
            statusOnHold,
            statusCompleted,
        ),
    )
    protected class Sort(
        displayName: String = "Sắp xếp",
        sortAZ: String = "A-Z",
        sortZA: String = "Z-A",
        sortUpdate: String = "Mới cập nhật",
        sortNew: String = "Truyện mới",
        sortPopular: String = "Xem nhiều",
        sortLike: String = "Được thích nhiều",
    ) : UriPartFilter(
        displayName,
        arrayOf(
            Pair(sortAZ, "az"),
            Pair(sortZA, "za"),
            Pair(sortUpdate, "update"),
            Pair(sortNew, "new"),
            Pair(sortPopular, "top"),
            Pair(sortLike, "like"),
        ),
        4,
    )
    open class Genre(name: String, val id: Int) : Filter.TriState(name)
    protected class Author(displayName: String = "Tác giả") : Filter.Text(displayName)
    protected class GenreList(genres: List<Genre>, displayName: String = "Thể loại") : Filter.Group<Genre>(displayName, genres)

    override fun getFilterList(): FilterList = FilterList(
        Author(),
        Status(),
        Sort(),
        GenreList(getGenreList()),
    )

    // To populate this list:
    // console.log([...document.querySelectorAll("div.search-gerne_item")].map(elem => `Genre("${elem.textContent.trim()}", ${elem.querySelector("label").getAttribute("data-genre-id")}),`).join("\n"))
    abstract fun getGenreList(): List<Genre>
    //endregion

    companion object {
        const val PREFIX_URL_SEARCH = "url:"
    }
}
