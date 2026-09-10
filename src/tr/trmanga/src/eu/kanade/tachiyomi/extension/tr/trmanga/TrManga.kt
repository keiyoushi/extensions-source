@file:Suppress("SpellCheckingInspection")

package eu.kanade.tachiyomi.extension.tr.trmanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.tryParseDate
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale

@Source
abstract class TrManga : HttpSource() {

    private val dateFormat = DateTimeFormatter.ofPattern("dd MMMM, yy", Locale.ENGLISH)

    override val supportsLatest = true

    // popular
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/webtoon-listesi?sort=views&short_type=DESC&page=$page", headers)

    private fun popularMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst(".wl-name, .tur-name")?.text() ?: element.attr("title")
        thumbnail_url = element.selectFirst("img")?.let {
            it.absUrl("src").ifEmpty { it.absUrl("data-src") }
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".wl-grid > a.wl-card, .tur-grid > a.tur-card").map { popularMangaFromElement(it) }
        val hasNextPage = document.selectFirst("a[aria-label=Sonraki], a[rel=next], a.page-link:contains(Sonraki)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // latest
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/son-eklenenler?page=$page", headers)

    private fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        val link = element.selectFirst("a.dsc-card-title, a.dsc-card-cover")!!
        setUrlWithoutDomain(link.absUrl("href"))
        title = element.selectFirst(".dsc-card-title")?.text() ?: link.text()
        thumbnail_url = element.selectFirst("img")?.let {
            it.absUrl("src").ifEmpty { it.absUrl("data-src") }
        }
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".dsc-card").map { latestUpdatesFromElement(it) }
        val hasNextPage = document.selectFirst("a[aria-label=Sonraki], a[rel=next], a.page-link:contains(Sonraki)") != null
        return MangasPage(mangas, hasNextPage)
    }

    // search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = if (filters.isEmpty()) getFilterList() else filters

        val genreFilter = filterList.firstInstance<GenreFilter>()
        val sortFilter = filterList.firstInstance<SortFilter>()
        val shortTypeFilter = filterList.firstInstance<OrderFilter>()
        val statusFilter = filterList.firstInstance<StatusFilter>()

        val url = "$baseUrl/webtoon-listesi".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", page.toString())
            if (query.isNotBlank()) {
                addQueryParameter("q", query.trim())
            }
            val genre = genreFilter.toUriPart()
            if (genre.isNotEmpty()) {
                addQueryParameter("genre", genre)
            }
            val status = statusFilter.toUriPart()
            if (status == "uptodate") {
                addQueryParameter("uptodate", "1")
            } else if (status.isNotEmpty()) {
                addQueryParameter("status", status)
            }
            val sort = sortFilter.toUriPart()
            if (sort.isNotEmpty()) {
                addQueryParameter("sort", sort)
            }
            val shortType = shortTypeFilter.toUriPart()
            if (shortType.isNotEmpty()) {
                addQueryParameter("short_type", shortType)
            }
        }.build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    // filters
    override fun getFilterList() = FilterList(
        SortFilter(),
        OrderFilter(),
        StatusFilter(),
        GenreFilter(),
    )

    // manga details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val authorArtistLabel = "Yazar & Çizer İsim(ler) : "
        val statusLabel = "Durum :"
        return SManga.create().apply {
            title = document.selectFirst(".movie__title")?.text().orEmpty()
            author = document.selectFirst("p:contains($authorArtistLabel)")?.text()?.substringAfter(authorArtistLabel)?.trim()
            artist = author
            genre = document.select("li.movie__year a[href*=/tur/]").joinToString { it.text() }.takeIf { it.isNotEmpty() }
            status = document.selectFirst("p:contains($statusLabel) > span")?.text()?.parseStatus() ?: SManga.UNKNOWN
            description = document.selectFirst(".movie__plot")?.text()?.trim()
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
    private val chapterNumberRegex = Regex("""\d+(\.\d+)?""")

    private fun chapterFromElement(element: Element) = SChapter.create().apply {
        element.selectFirst("a")!!.let {
            setUrlWithoutDomain(it.absUrl("href"))
            name = it.text().trim()
            date_upload = parseChapterDate(element.selectFirst("td:last-child span:first-child")?.text())
            chapter_number = chapterNumberRegex.find(it.text())?.value?.toFloatOrNull() ?: -1f
            scanlator = element.selectFirst("td:nth-child(2) a:first-child")?.text()?.trim()
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> = response.asJsoup().select("tbody>tr").map { chapterFromElement(it) }

    // Date logic lifted from Madara
    private fun parseChapterDate(date: String?): Long {
        date ?: return 0

        return when {
            " önce" in date -> {
                parseRelativeDate(date)
            }

            else -> dateFormat.tryParseDate(date)
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
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        if (document.selectFirst(".rd-lock, *:containsOwn(Üyelere Özel)") != null) {
            throw Exception("Bu bölüm üyelere özeldir. Okumak için WebView üzerinden giriş yapın")
        }
        return document.select(".reader-img, img[data-src]")
            .distinctBy { it.absUrl("data-src").ifEmpty { it.absUrl("src") } }
            .mapIndexed { i, img ->
                Page(i, imageUrl = img.absUrl("data-src").ifEmpty { img.absUrl("src") })
            }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        private val NUMBER_REGEX = """\d+""".toRegex()
    }
}
