package eu.kanade.tachiyomi.extension.tr.uzaymanga

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
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class UzayManga : ParsedHttpSource() {
    override val name = "Uzay Manga"

    override val baseUrl = "https://uzaymanga.com"

    private val cdnUrl = "https://manga2.efsaneler.can.re"

    override val lang = "tr"

    override val supportsLatest = true

    override val versionId = 3

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    private val json: Json by injectLazy()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("origin", baseUrl)

    // Popular
    override fun popularMangaRequest(page: Int) = searchMangaRequest(page, "", popularFilter)
    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // Latest
    override fun latestUpdatesRequest(page: Int): Request =
        GET(baseUrl.toHttpUrl().newBuilder().addQueryParameter("page", page.toString()).build(), headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        return super.latestUpdatesParse(response)
    }

    override fun latestUpdatesSelector() = "div[id='content'] > section > section > div.grid.grid.grid-cols-1 > section > div.grid > div"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("a:first-child h2")!!.text()
        thumbnail_url = element.selectFirst(".card-image img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a:first-child")!!.absUrl("href"))
    }

    override fun latestUpdatesNextPageSelector() = "section[aria-label='navigation'] a.rounded-r-lg"

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX).not()) return super.fetchSearchManga(page, query, filters)

        val mangaPath = try {
            mangaPathFromUrl(query.substringAfter(URL_SEARCH_PREFIX))
                ?: return Observable.just(MangasPage(emptyList(), false))
        } catch (e: Exception) {
            return Observable.error(e)
        }

        return fetchMangaDetails(
            SManga.create()
                .apply { this.url = "/manga/$mangaPath/" },
        )
            .map {
                it.url = "/manga/$mangaPath/"
                MangasPage(listOf(it), false)
            }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("search")
            .addQueryParameter("search", query)
            .addQueryParameter("page", page.toString())

        var hasOrderFilter = false

        filters.forEach { filter ->
            when (filter) {
                is StatusFilter -> {
                    url.addQueryParameter("publicStatus", filter.selectedValue())
                }
                is OrderByFilter -> {
                    url.addQueryParameter("order", filter.selectedValue())
                    hasOrderFilter = true
                }
                is GenreListFilter -> {
                    val genreIds = filter.state
                        .filter { it.state }
                        .map { it.value }
                    if (genreIds.isNotEmpty()) {
                        url.addQueryParameter("categories", genreIds.joinToString("%2C"))
                    }
                }
                is CountryFilter -> {
                    url.addQueryParameter("country", filter.selectedValue())
                }
                else -> { /* Do Nothing */ }
            }
        }

        if (!hasOrderFilter) {
            url.addQueryParameter("order", "3")
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        return super.searchMangaParse(response)
    }

    override fun searchMangaSelector() = "section[aria-label='series area'] .card"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h2")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
    }

    override fun searchMangaNextPageSelector() = "section[aria-label='navigation'] a.rounded-r-lg"

    // Manga details
    open val seriesDetailsSelector = "section[title='manga'] > div > section.relative"
    open val seriesTitleSelector = "div > div.content-details > h1"
    open val seriesAuthorSelector = "div > div.content-details > div.grid.grid-cols-1 > div.flex > div > span"
    open val seriesDescriptionSelector = "div > div.content-details > div.grid.grid-cols-1 > div.summary > p"
    open val seriesGenreSelector = "div > div.content-details > div.flex.flex-wrap > a[href^='search?categories']"
    open val seriesCountrySelector = "div > div.content-details > div.grid.grid-cols-1 > div.flex > div > span"
    open val seriesStatusSelector = "div > div.content-details > div.grid.grid-cols-1 > div.flex > div > span"
    open val seriesThumbnailSelector = "div > div.content-info > img"

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        document.selectFirst(seriesDetailsSelector)?.let { seriesDetails ->
            title = seriesDetails.selectFirst(seriesTitleSelector)
                ?.text()
                ?: "Seri İsmi Alınamadı"
            author = seriesDetails.select(seriesAuthorSelector)
                .firstOrNull { it.text().contains("Tarafından") }
                ?.parent()
                ?.select("span")
                ?.getOrNull(1)
                ?.ownText()
                ?: "Yazar Alınamadı"
            description = seriesDetails.select(seriesDescriptionSelector).joinToString("\n") { it.text() }.trim()
            status = seriesDetails.select(seriesStatusSelector)
                .firstOrNull { it.text().contains("Durum") }
                ?.parent()
                ?.select("span")
                ?.getOrNull(1)
                ?.ownText()
                ?.parseStatus() ?: SManga.UNKNOWN
            thumbnail_url = seriesDetails.select(seriesThumbnailSelector).imgAttr()

            val genres = seriesDetails.select(seriesGenreSelector).map { it.text() }.toMutableList()
            // Add country to genres if available (second span from country selector)
            seriesDetails.select(seriesCountrySelector)
                .firstOrNull { it.text().contains("Ülke") }
                ?.parent()
                ?.select("span")
                ?.getOrNull(1)
                ?.ownText()
                ?.takeIf { it.isNotBlank() }
                ?.let { genres.add(it) }
            genre = genres.joinToString { it.trim() }
        }
    }

    open fun String?.parseStatus(): Int = when {
        this == null -> SManga.UNKNOWN
        listOf("Devam Ediyor", "Bırakıldı").any { this.contains(it, ignoreCase = true) } -> SManga.ONGOING
        this.contains("Tamamlandi", ignoreCase = true) -> SManga.COMPLETED
        this.contains("Ara Verildi", ignoreCase = true) -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    // Chapter list
    override fun chapterListSelector() = "section[title='manga'] > div > section.relative > div > div.content-details > div > div > div[aria-label='episodes'] > a"
    open val chapterListNameSelector = "h3.chapternum"
    open val chapterListUploadSelector = "span.text-slate-400.text-sm"
    open val chapterSeriesUploadSelector = "section[title='manga'] > div > section.relative > div > div.content-details > div.grid.grid-cols-1 > div.flex > div > span"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select(chapterListSelector()).map { chapterFromElement(it) }

        // Systematic date update: Start from oldest chapter (Chapter 1) and work upwards
        if (chapters.isNotEmpty()) {
            // Check if the oldest chapter (Chapter 1) has no date
            if (chapters.last().date_upload == 0L) {
                // If Chapter 1 is empty, set all chapters to series publication date
                val seriesDate = document.select(chapterSeriesUploadSelector)
                    .firstOrNull { it.text().contains("Yayınlanma") }
                    ?.parent()
                    ?.select("span")
                    ?.getOrNull(1)
                    ?.ownText()
                    ?.parseChapterDate() ?: parseUpdatedOnDate("Ekim 29 ,2023") // :D

                chapters.forEach { chapter ->
                    chapter.date_upload = seriesDate
                }
            } else {
                // Chapter 1 has date, work from oldest to newest filling empty dates
                for (i in (chapters.size - 2) downTo 0) {
                    if (chapters[i].date_upload == 0L) {
                        chapters[i].date_upload = chapters[i + 1].date_upload
                    }
                }
            }
        }

        return chapters
    }

    private fun parseUpdatedOnDate(date: String): Long {
        return SimpleDateFormat("MMMM dd ,yyyy", Locale("tr")).parse(date)?.time ?: 0L
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        name = element.selectFirst(chapterListNameSelector)?.text() ?: "Bölüm"
        date_upload = element.selectFirst(chapterListUploadSelector)?.text()?.parseChapterDate() ?: 0L
    }

    protected open fun String?.parseChapterDate(): Long {
        if (this == null) return 0
        return try {
            dateFormat.parse(this)?.time ?: 0
        } catch (_: Exception) {
            0
        }
    }

    // Pages
    override fun pageListParse(document: Document): List<Page> {
        val script = document.select("script")
            .map { it.html() }
            .firstOrNull { pageRegex.find(it) != null }
            ?: return emptyList()

        return pageRegex.findAll(script).mapIndexed { index, result ->
            val url = result.groups.get(1)!!.value
            Page(index, document.location(), "$cdnUrl/$url")
        }.toList()
    }

    // Filters
    open class SelectFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        defaultValue: String? = null,
    ) : Filter.Select<String>(
        displayName,
        vals.map { it.first }.toTypedArray(),
        vals.indexOfFirst { it.second == defaultValue }.takeIf { it != -1 } ?: 0,
    ) {
        fun selectedValue() = vals[state].second
    }

    protected class StatusFilter(
        name: String,
        options: Array<Pair<String, String>>,
        defaultOrder: String? = null,
    ) : SelectFilter(
        name,
        options,
        defaultOrder,
    )

    protected open val statusOptions = arrayOf(
        Pair("Tümü", ""),
        Pair("Devam Ediyor", "1"),
        Pair("Tamamlandı", "2"),
        Pair("Bırakıldı", "3"),
        Pair("Ara Verildi", "4"),
    )

    protected class OrderByFilter(
        name: String,
        options: Array<Pair<String, String>>,
        defaultOrder: String? = null,
    ) : SelectFilter(
        name,
        options,
        defaultOrder,
    )

    protected open val orderByFilterOptions = arrayOf(
        Pair("Varsayılan", ""),
        Pair("A-Z", "1"),
        Pair("Z-A", "2"),
        Pair("En Yeni", "3"),
        Pair("Popüler", "4"),
    )

    protected open val popularFilter by lazy { FilterList(OrderByFilter("", orderByFilterOptions, "4")) }

    protected class GenreData(
        val name: String,
        val value: String,
    )

    protected class Genre(
        name: String,
        val value: String,
    ) : Filter.CheckBox(name)

    protected class GenreListFilter(
        name: String,
        genres: List<Genre>,
    ) : Filter.Group<Genre>(
        name,
        genres,
    )

    protected val genrelist: List<GenreData> = listOf(
        GenreData("Aksiyon", "1"),
        GenreData("Avcı", "2"),
        GenreData("Bebek", "3"),
        GenreData("Büyü", "4"),
        GenreData("Canavar", "5"),
        GenreData("Çete", "6"),
        GenreData("Cin", "7"),
        GenreData("Cin-serisi", "8"),
        GenreData("Doğaüstü", "9"),
        GenreData("Doktor", "10"),
        GenreData("Dövüş", "11"),
        GenreData("Dövüş-sanatları", "12"),
        GenreData("Dram", "13"),
        GenreData("Drama", "14"),
        GenreData("Ecchi", "15"),
        GenreData("Fantastik", "16"),
        GenreData("Fantezi", "17"),
        GenreData("Geçmişe-dönme", "18"),
        GenreData("Geri-dönüş", "19"),
        GenreData("Gizem", "20"),
        GenreData("Harem", "21"),
        GenreData("Hayattan-kesitler", "22"),
        GenreData("Intikam", "23"),
        GenreData("Isekai", "24"),
        GenreData("Komedi", "25"),
        GenreData("Kule", "26"),
        GenreData("Macera", "27"),
        GenreData("Manhua", "28"),
        GenreData("Manhwa", "29"),
        GenreData("Mature", "30"),
        GenreData("Murim", "31"),
        GenreData("Okul", "32"),
        GenreData("Okul-hayatı", "33"),
        GenreData("Oyun", "34"),
        GenreData("Peri", "35"),
        GenreData("Reankarnasyon", "36"),
        GenreData("Reankarne", "37"),
        GenreData("Romantik", "38"),
        GenreData("Romantizm", "39"),
        GenreData("Sanal-gerçeklik", "40"),
        GenreData("Sci-fi", "41"),
        GenreData("Seinen", "42"),
        GenreData("Şeytani", "43"),
        GenreData("Shounen", "44"),
        GenreData("Şiddet", "45"),
        GenreData("Sistem", "46"),
        GenreData("Spor", "47"),
        GenreData("Super-güç", "48"),
        GenreData("Tarihi", "49"),
        GenreData("Trajedi", "50"),
        GenreData("Webtoon", "51"),
        GenreData("Yetişkin", "52"),
        GenreData("Yıldız", "53"),
        GenreData("Zindan", "54"),
        GenreData("Zindanlar", "55"),
        GenreData("Zorbalar", "56"),
    )

    protected open fun getGenreList(): List<Genre> {
        return genrelist.map { Genre(it.name, it.value) }
    }

    protected class CountryFilter(
        name: String,
        options: Array<Pair<String, String>>,
    ) : SelectFilter(
        name,
        options,
    )

    protected open val countryOptions = arrayOf(
        Pair("Tümü", ""),
        Pair("Çin", "1"),
        Pair("Kore", "2"),
        Pair("Japon", "3"),
        Pair("Amerika", "4"),
        Pair("Türkiye", "5"),
        Pair("Bilinmiyor", "10"),
    )

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Separator(),
            StatusFilter("Durum", statusOptions),
            CountryFilter("Ülke", countryOptions),
            OrderByFilter("Sıralama", orderByFilterOptions),
        )
        if (genrelist.isNotEmpty()) {
            filters.addAll(
                listOf(
                    Filter.Separator(),
                    GenreListFilter("Kategoriler", getGenreList()),
                ),
            )
        } else {
            filters.add(
                Filter.Header("Kategoriler alınamadı"),
            )
        }
        return FilterList(filters)
    }

    // Helpers
    open val chapterMangeUrlSelector = "section[title='manga'] > div > section > div.header > div[aria-label='header'] > h1 > a"

    protected open fun mangaPathFromUrl(urlString: String): String? {
        val baseMangaUrl = "$baseUrl/manga".toHttpUrl()
        val url = urlString.toHttpUrlOrNull() ?: return null

        val isMangaUrl = (baseMangaUrl.host == url.host && pathLengthIs(url, 3) && url.pathSegments[0] == baseMangaUrl.pathSegments[0])
        if (isMangaUrl) return url.pathSegments[2]

        val potentiallyChapterUrl = pathLengthIs(url, 5) && url.pathSegments[4].endsWith("-bolum")
        if (potentiallyChapterUrl) {
            val response = client.newCall(GET(urlString, headers)).execute()
            if (response.isSuccessful.not()) {
                response.close()
                throw IllegalStateException("HTTP error ${response.code}")
            } else if (response.isSuccessful) {
                val mangaLink = response.asJsoup().selectFirst(chapterMangeUrlSelector)
                if (mangaLink != null) {
                    val href = mangaLink.attr("href")
                    val newUrl = href.toHttpUrlOrNull()
                    if (newUrl != null) {
                        val isNewMangaUrl = (baseMangaUrl.host == newUrl.host && pathLengthIs(newUrl, 3) && newUrl.pathSegments[0] == baseMangaUrl.pathSegments[0])
                        if (isNewMangaUrl) return newUrl.pathSegments[2]
                    }
                }
            }
        }

        return null
    }

    private fun pathLengthIs(url: HttpUrl, n: Int, strict: Boolean = false): Boolean {
        return url.pathSegments.size == n && url.pathSegments[n - 1].isNotEmpty() ||
            (!strict && url.pathSegments.size == n + 1 && url.pathSegments[n].isEmpty())
    }

    protected open fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    protected open fun Elements.imgAttr(): String = this.first()!!.imgAttr()

    // Unused
    override fun popularMangaSelector(): String = throw UnsupportedOperationException()
    override fun popularMangaFromElement(element: Element): SManga = throw UnsupportedOperationException()
    override fun popularMangaNextPageSelector(): String? = throw UnsupportedOperationException()

    override fun imageUrlParse(document: Document): String = throw UnsupportedOperationException()

    companion object {
        const val URL_SEARCH_PREFIX = "url:"
        val dateFormat = SimpleDateFormat("MMMM dd ,yyyy", Locale("tr"))
        val pageRegex = """\\"path\\":\\"([^"]+)\\""".trimIndent().toRegex()
    }
}
