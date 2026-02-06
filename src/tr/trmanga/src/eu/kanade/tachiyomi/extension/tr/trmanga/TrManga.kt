package eu.kanade.tachiyomi.extension.tr.trmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TrManga : ParsedHttpSource() {

    override val name = "TrManga"

    override val baseUrl = "https://trmanga.com"

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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val genreFilter = filterList.firstInstance<GenreFilter>()
        val sortFilter = filterList.firstInstance<SortFilter>()
        val shortTypeFilter = filterList.firstInstance<OrderFilter>()
        val statusFilter = filterList.firstInstance<StatusFilter>()

        val url = "$baseUrl/webtoon-listesi".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("q", query)
            .addQueryParameter("genre", genreFilter.toUriPart())
            .addQueryParameter("sort", sortFilter.toUriPart())
            .addQueryParameter("short_type", shortTypeFilter.toUriPart())
            .addQueryParameter("status", statusFilter.toUriPart())
            .build()
        return GET(url, headers)
    }

    // filters
    override fun getFilterList() = FilterList(
        SortFilter(),
        OrderFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    // manga details
    override fun mangaDetailsParse(document: Document): SManga {
        val authorArtistLabel = "Yazar & Çizer İsim(ler) : "
        val statusLabel = "Durum :"
        return SManga.create().apply {
            title = document.selectFirst(".movie__title")!!.text()
            author = document.selectFirst("p:contains($authorArtistLabel)")?.text()?.substringAfter(authorArtistLabel)
            artist = author
            status = document.selectFirst("p:contains($statusLabel)>span")?.text()!!.parseStatus()
            description = document.selectFirst(".movie__plot")?.text()
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("abs:content")
        }
    }

    private val statusOngoing = listOf("ongoing", "devam ediyor", "güncel")
    private val statusCompleted = listOf("complete", "tamamlandı", "bitti")

    private fun String.parseStatus(): Int = when (this.lowercase()) {
        in statusOngoing -> SManga.ONGOING
        in statusCompleted -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // chapter list
    override fun chapterListSelector() = "tbody>tr"

    private val chapterNumberRegex = """\d+(\.\d+)?"""
    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            name = it.text()
            date_upload = parseChapterDate(element.selectFirst("td:last-child span:first-child")?.text())
            chapter_number = Regex(chapterNumberRegex).find(it.text())!!.value.toFloat()
            scanlator = element.selectFirst("td:nth-child(2) a:first-child")?.text()
        }
    }

    // Date logic lifted from Madara
    private fun parseChapterDate(date: String?): Long {
        date ?: return 0

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
    override fun pageListParse(document: Document): List<Page> = document.select("img[data-src]").mapIndexed { i, img ->
        Page(i, document.location(), img.absUrl("data-src"))
    }
    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    companion object {
        private val NUMBER_REGEX = """\d+""".toRegex()
    }
}
