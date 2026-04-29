package eu.kanade.tachiyomi.extension.id.bacakomik

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BacaKomik : HttpSource() {
    override val name = "BacaKomik"
    override val baseUrl = "https://bacakomik.my"
    override val lang = "id"
    override val supportsLatest = true

    private val dateFormat: SimpleDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.US)
    private val chapterRegex = Regex("""Chapter\s([0-9]+)""")

    override val id = 4383360263234319058

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(12, 3)
        .build()

    private fun pagePath(page: Int) = if (page > 1) "page/$page/" else ""

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/daftar-komik/${pagePath(page)}?order=popular", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.animepost").map { mangaFromElement(it) }
        val hasNextPage = document.select("a.next.page-numbers").isNotEmpty()
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/daftar-komik/${pagePath(page)}?order=update", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val builtUrl = if (page == 1) "$baseUrl/daftar-komik/" else "$baseUrl/daftar-komik/page/$page/?order="
        val url = builtUrl.toHttpUrl().newBuilder()
        url.addQueryParameter("title", query)

        filters.forEach { filter ->
            when (filter) {
                is AuthorFilter -> {
                    url.addQueryParameter("author", filter.state)
                }
                is YearFilter -> {
                    url.addQueryParameter("yearx", filter.state)
                }
                is StatusFilter -> {
                    val status = when (filter.state) {
                        Filter.TriState.STATE_INCLUDE -> "completed"
                        Filter.TriState.STATE_EXCLUDE -> "ongoing"
                        else -> ""
                    }
                    url.addQueryParameter("status", status)
                }
                is TypeFilter -> {
                    url.addQueryParameter("type", filter.toUriPart())
                }
                is SortByFilter -> {
                    url.addQueryParameter("order", filter.toUriPart())
                }
                is GenreListFilter -> {
                    filter.state
                        .filter { it.state != Filter.TriState.STATE_IGNORE }
                        .forEach { url.addQueryParameter("genre[]", it.id) }
                }
                else -> {}
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("div.animposx > a")!!.attr("abs:href"))
        title = element.select(".animposx .tt h4").text()
        thumbnail_url = element.selectFirst("div.limit img")?.imgAttr()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("div.infoanime")!!
        val descElement = document.selectFirst("div.desc > .entry-content.entry-content-single")!!

        return SManga.create().apply {
            title = document.select("#breadcrumbs li:last-child span").text()
            author = document.select(".infox .spe span:contains(Author) :not(b)").text()
            artist = document.select(".infox .spe span:contains(Artis) :not(b)").text()
            genre = infoElement.select(".infox > .genre-info > a, .infox .spe span:contains(Jenis Komik) a")
                .joinToString(", ") { it.text() }
            status = parseStatus(document.select(".infox .spe span:contains(Status)").text())
            description = descElement.select("p").text().substringAfter("bercerita tentang ")
            thumbnail_url = document.selectFirst(".thumb > img:nth-child(1)")?.imgAttr()
        }
    }

    private fun parseStatus(element: String): Int = when {
        element.lowercase().contains("berjalan") -> SManga.ONGOING
        element.lowercase().contains("tamat") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("#chapter_list li").map { element ->
            val urlElement = element.selectFirst(".lchx a")!!
            SChapter.create().apply {
                setUrlWithoutDomain(urlElement.attr("abs:href"))
                name = urlElement.text()
                date_upload = element.selectFirst(".dt a")?.text()?.let { parseChapterDate(it) } ?: 0L
            }
        }
    }

    private fun parseChapterDate(date: String): Long = if (date.contains("yang lalu")) {
        val value = date.substringBefore(' ').toIntOrNull() ?: return 0L
        when {
            "detik" in date -> Calendar.getInstance().apply {
                add(Calendar.SECOND, -value)
            }.timeInMillis
            "menit" in date -> Calendar.getInstance().apply {
                add(Calendar.MINUTE, -value)
            }.timeInMillis
            "jam" in date -> Calendar.getInstance().apply {
                add(Calendar.HOUR_OF_DAY, -value)
            }.timeInMillis
            "hari" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, -value)
            }.timeInMillis
            "minggu" in date -> Calendar.getInstance().apply {
                add(Calendar.DATE, -value * 7)
            }.timeInMillis
            "bulan" in date -> Calendar.getInstance().apply {
                add(Calendar.MONTH, -value)
            }.timeInMillis
            "tahun" in date -> Calendar.getInstance().apply {
                add(Calendar.YEAR, -value)
            }.timeInMillis
            else -> 0L
        }
    } else {
        dateFormat.tryParse(date)
    }

    override fun prepareNewChapter(chapter: SChapter, manga: SManga) {
        chapterRegex.find(chapter.name)?.let {
            chapter.chapter_number = it.groupValues[1].toFloatOrNull() ?: -1f
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("div:has(>img[alt*=\"Chapter\"]) img")
            .filter { it.parent()?.tagName() != "noscript" }
            .mapIndexedNotNull { i, element ->
                val url = element.attr("onError").substringAfter("src='").substringBefore("';")
                if (url.isNotEmpty()) Page(i, imageUrl = url) else null
            }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, newHeaders)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("NOTE: Ignored if using text search!"),
        Filter.Separator(),
        AuthorFilter(),
        YearFilter(),
        StatusFilter(),
        TypeFilter(),
        SortByFilter(),
        GenreListFilter(getGenreList()),
    )

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        else -> attr("abs:src")
    }
}
