package eu.kanade.tachiyomi.extension.tr.TrManga
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TrManga : ParsedHttpSource() {

    override val name = "trmanga"

    override val baseUrl = "https://www.trmanga.com"

    override val lang = "tr"

    private val dateFormat = SimpleDateFormat("dd MMMM, yy", Locale.ENGLISH)

    override val supportsLatest = true

    // popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/webtoon-listesi?sort=views&short_type=DESC&page=$page", headers)
    override fun popularMangaSelector() = "div.row>div.col-xl-4"
    override fun popularMangaNextPageSelector() = "a.page-link:contains(Sonraki)"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a[class]")!!.absUrl("href"))
        title = element.selectFirst("a[class]")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("data-src")
    }

    // latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/son-eklenenler?page=$page", headers)
    override fun latestUpdatesSelector() = "main#bslistMain>div>div"
    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.absUrl("href"))
        title = element.selectFirst("span.title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // search
    override fun searchMangaFromElement(element: Element): SManga = popularMangaFromElement(element)
    override fun searchMangaSelector() = popularMangaSelector()
    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    protected inline fun <reified T> Iterable<*>.findInstance() = find { it is T } as? T

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val genreFilter = filterList.findInstance<genreFilter>()!!
        val sortFilter = filterList.findInstance<sortFilter>()!!
        val short_typeFilter = filterList.findInstance<OrderFilter>()!!
        val statusFilter = filterList.findInstance<StatusFilter>()!!

        val url = "$baseUrl/webtoon-listesi".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)
            .addQueryParameter("genre", genreFilter.toUriPart())
            .addQueryParameter("sort", sortFilter.toUriPart())
            .addQueryParameter("short_type", short_typeFilter.toUriPart())
            .addQueryParameter("status", statusFilter.toUriPart())
            .build()
        return GET(url, headers)
    }

    // filters
    override fun getFilterList() = FilterList(
        sortFilter(),
        OrderFilter(),
        StatusFilter(),
        genreFilter(),
    )
    class sortFilter(state: Int = 0) : UriPartFilter(
        "Sort",
        arrayOf(
            Pair("Popularity", "views"),
            Pair("Date Updated", "released"),
            Pair("Alphabetical Order", "name"),
        ),
        state,
    )
    class OrderFilter(state: Int = 0) : UriPartFilter(
        "Order",
        arrayOf(
            Pair("Descending", "DESC"),
            Pair("Ascending", "ASC"),
        ),
        state,
    )
    class StatusFilter(state: Int = 0) : UriPartFilter(
        "Status",
        arrayOf(
            Pair("All", ""),
            Pair("Ongoing", "continues"),
            Pair("Completed", "complated"),
        ),
        state,
    )
    class genreFilter(state: Int = 0) : UriPartFilter(
        "Genre",
        arrayOf(
            Pair("All", ""),
            Pair("Aksiyon", "aksiyon"),
            Pair("Bilim Kurgu", "bilim-kurgu"),
            Pair("BL", "bl"),
            Pair("Büyü", "büyü"),
            Pair("Doğaüstü", "dogaustu"),
            Pair("Dövüş Sanatları", "dovus-sanatlari"),
            Pair("Fantastik", "fantastik"),
            Pair("Gerilim", "gerilim"),
            Pair("Isekai", "isekai"),
            Pair("Josei", "josei"),
            Pair("Komedi", "komedi"),
            Pair("Korku", "korku"),
            Pair("Macera", "macera"),
            Pair("Manga", "manga"),
            Pair("Okul", "okul"),
            Pair("One-shot", "one-shot"),
            Pair("Oyun", "oyun"),
            Pair("Psikolojik", "psikolojik"),
            Pair("Reenkarnasyon", "reenkarnasyon"),
            Pair("Romantik", "romantik"),
            Pair("Seinen", "seinen"),
            Pair("Shoujo", "shoujo"),
            Pair("Shounen", "shounen"),
            Pair("Shounen Ai", "shounen-ai"),
            Pair("Slice of Life", "slice-of-life"),
            Pair("Spor", "spor"),
            Pair("Suç", "suc"),
            Pair("Süper Kahraman", "süper-kahraman"),
            Pair("Tarih", "tarih"),
            Pair("Trajedi", "trajedi"),
            Pair("Vampir", "vampir"),
            Pair("Yaoi", "yaoi"),
            Pair("Yetişkin", "yetişkin"),
            Pair("Yuri", "yuri"),
            Pair("Zaman Yolculuğu", "zaman-yolculuğu"),
        ),
        state,
    )

    open class UriPartFilter(
        displayName: String,
        private val vals: Array<Pair<String, String>>,
        state: Int = 0,
    ) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray(), state) {
        fun toUriPart() = vals[state].second
    }

    // manga details
    override fun mangaDetailsParse(document: Document): SManga {
        val authorArtistLabel = "Yazar & Çizer İsim(ler) : "
        val statusLabel = "Durum :"
        return SManga.create().apply {
            title = document.selectFirst(".movie__title")!!.text()
            author = document.select("p:contains($authorArtistLabel)").text().substringAfter(authorArtistLabel)
            artist = author
            status = document.select("p:contains($statusLabel)>span").text().parseStatus()
            description = document.select(".movie__plot").text()
            thumbnail_url = document.select("img[alt=\"$title\"]").attr("abs:src")
        }
    }

    private val statusOngoing = listOf("ongoing", "devam ediyor", "güncel")
    private val statusCompleted = listOf("complete", "tamamlandı", "bitti")

    private fun String.parseStatus(): Int {
        return when (this.lowercase()) {
            in statusOngoing -> SManga.ONGOING
            in statusCompleted -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // chapter list
    override fun chapterListSelector() = "tbody>tr"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            name = it.text()
            date_upload = parseChapterDate(element.select("td").last()?.selectFirst("span")?.text())
            chapter_number = it.text().filter { it.isDigit() }.toFloat()
            scanlator = element.select("td")[1].selectFirst("a")?.text()
        }
    }

    // Date logic lifted from Madara
    private fun parseChapterDate(date: String?): Long {
        date ?: return 0

        fun SimpleDateFormat.tryParse(string: String): Long {
            return try {
                parse(string)?.time ?: 0
            } catch (_: ParseException) {
                0
            }
        }

        return when {
            " önce" in date -> {
                parseRelativeDate(date)
            }
            else -> dateFormat.tryParse(date)
        }
    }

    private fun parseRelativeDate(date: String): Long {
        val number = NUMBER_REGEX.find(date)?.groupValues?.getOrNull(0)?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()

        return when {
            date.contains("yıl") -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            date.contains("ay") -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.contains("hafta") -> cal.apply { add(Calendar.WEEK_OF_MONTH, -number) }.timeInMillis
            date.contains("gün") -> cal.apply { add(Calendar.DAY_OF_MONTH, -number) }.timeInMillis
            date.contains("saat") -> cal.apply { add(Calendar.HOUR, -number) }.timeInMillis
            date.contains("dakika") -> cal.apply { add(Calendar.MINUTE, -number) }.timeInMillis
            date.contains("ikinci") -> cal.apply { add(Calendar.SECOND, -number) }.timeInMillis
            else -> 0
        }
    }

    // page list
    override fun pageListParse(document: Document): List<Page> {
        return document.select("img[data-src]").mapIndexed { i, img ->
            Page(i, document.location(), img.attr("data-src"))
        }
    }
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    companion object {
        private val NUMBER_REGEX = """\d+""".toRegex()
    }
}
