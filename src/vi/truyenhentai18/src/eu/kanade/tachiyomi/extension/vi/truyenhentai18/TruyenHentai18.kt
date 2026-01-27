package eu.kanade.tachiyomi.extension.vi.truyenhentai18

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale

class TruyenHentai18 : HttpSource() {

    override val name = "Truyện Hentai 18+"

    override val baseUrl = "https://truyenhentai18.net"

    override val lang = "vi"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ======================================

    override fun popularMangaRequest(page: Int): Request {
        val url = if (page > 1) {
            "$baseUrl/xem-nhieu-nhat/page/$page"
        } else {
            "$baseUrl/xem-nhieu-nhat"
        }
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.col-6.col-md-4.col-lg-2.mb-3").map { element ->
            mangaFromElement(element)
        }
        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("a")!!
        setUrlWithoutDomain(linkElement.absUrl("href"))
        title = element.selectFirst("h2")?.text() ?: ""
        thumbnail_url = element.selectFirst("img")?.let { img ->
            img.absUrl("data-src").ifEmpty { img.absUrl("src") }
        }
    }

    // ============================== Latest ======================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = if (page > 1) {
            "$baseUrl/moi-cap-nhat/page/$page"
        } else {
            "$baseUrl/moi-cap-nhat"
        }
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // ============================== Search ======================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Check if genre filter is selected
        val genreFilter = filters.filterIsInstance<GenreFilter>().firstOrNull()
        val selectedGenre = genreFilter?.toUriPart()

        return if (query.isBlank() && !selectedGenre.isNullOrEmpty()) {
            val url = if (page > 1) {
                "$baseUrl/category/$selectedGenre/page/$page"
            } else {
                "$baseUrl/category/$selectedGenre"
            }
            GET(url, headers)
        } else {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addQueryParameter("s", query)
                .build()
            GET(url, headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.col-6.col-md-4.col-lg-2.mb-3").map { element ->
            mangaFromElement(element)
        }
        val hasNextPage = document.selectFirst("ul.pagination a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Filters ======================================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ======================================

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET(getMangaUrl(manga), headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()

        title = document.selectFirst("h1")?.text() ?: ""
        thumbnail_url = document.selectFirst("img.manga-cover")?.absUrl("src")
            ?: document.selectFirst(".card img.img-fluid")?.absUrl("src")

        genre = document.select("a.badge.bg-primary").joinToString { it.text() }

        document.select(".list-group-item, div").forEach { element ->
            val text = element.text()
            when {
                text.contains("Trạng thái:", ignoreCase = true) -> {
                    status = when {
                        text.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
                        text.contains("Đang tiến hành", ignoreCase = true) -> SManga.ONGOING
                        else -> SManga.UNKNOWN
                    }
                }
                text.contains("Tác giả:", ignoreCase = true) -> {
                    author = text.substringAfter(":").trim()
                }
            }
        }

        description = document.selectFirst(".card-body p")?.text()
            ?: document.selectFirst(".card-body")?.ownText()

        setUrlWithoutDomain(response.request.url.toString())
    }

    // ============================== Chapters ======================================

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun chapterListRequest(manga: SManga): Request {
        return GET(getMangaUrl(manga), headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val chapters = document.select("div.chapter-item")
        val totalChapters = chapters.size
        return chapters.mapIndexed { index, element ->
            chapterFromElement(element, totalChapters - index)
        }
    }

    private fun chapterFromElement(element: Element, index: Int): SChapter = SChapter.create().apply {
        val linkElement = element.selectFirst("a.fw-bold")!!
        setUrlWithoutDomain(linkElement.absUrl("href"))
        name = linkElement.text()
        chapter_number = index.toFloat()

        // Try to parse date from text content
        val dateText = element.text()
        val dateMatch = DATE_REGEX.find(dateText)
        date_upload = dateMatch?.value?.let { dateFormat.parse(it)?.time } ?: 0L
    }

    // ============================== Pages ======================================

    override fun pageListRequest(chapter: SChapter): Request {
        return GET(getChapterUrl(chapter), headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("#viewer img, div.chapter-container img").mapIndexed { index, element ->
            val imageUrl = element.absUrl("data-src").ifEmpty { element.absUrl("src") }
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    companion object {
        private val DATE_REGEX = """(\d{1,2}[/-]\d{1,2}[/-]\d{2,4})""".toRegex()
        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT)
    }
}
