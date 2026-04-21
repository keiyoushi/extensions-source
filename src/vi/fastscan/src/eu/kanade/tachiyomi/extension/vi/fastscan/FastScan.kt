package eu.kanade.tachiyomi.extension.vi.fastscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class FastScan : HttpSource() {
    override val name = "FastScan"
    override val lang = "vi"
    override val baseUrl = "https://fastscan.org"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder()
            .addQueryParameter("category", "")
            .addQueryParameter("notcategory", "")
            .addQueryParameter("status", "0")
            .addQueryParameter("minchapter", "0")
            .addQueryParameter("sort", "4")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListPage(
        document = response.asJsoup(),
        currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1,
    )

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder()
            .addQueryParameter("category", "")
            .addQueryParameter("notcategory", "")
            .addQueryParameter("status", "0")
            .addQueryParameter("minchapter", "0")
            .addQueryParameter("sort", "0")
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListPage(
        document = response.asJsoup(),
        currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1,
    )

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("q", query)
                .addQueryParameter("page", page.toString())
                .build()

            return GET(url, headers)
        }

        val category = filters.firstInstanceOrNull<GenreFilter>()?.selected
        val minChapter = filters.firstInstanceOrNull<MinChapterFilter>()?.value ?: "0"
        val status = filters.firstInstanceOrNull<StatusFilter>()?.value ?: "0"
        val sort = filters.firstInstanceOrNull<SortFilter>()?.value ?: "0"

        val hasFilters = category != null || minChapter != "0" || status != "0" || sort != "0"
        if (!hasFilters) {
            return latestUpdatesRequest(page)
        }

        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder()
            .addQueryParameter("category", category ?: "")
            .addQueryParameter("notcategory", "")
            .addQueryParameter("status", status)
            .addQueryParameter("minchapter", minChapter)
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaListPage(
        document = response.asJsoup(),
        currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1,
    )

    private fun parseMangaListPage(document: Document, currentPage: Int): MangasPage {
        val mangas = document.select("ul.list_grid.grid > li")
            .mapNotNull(::mangaFromElement)

        val hasNextPage = document.select(".page_redirect a[href]")
            .mapNotNull { element ->
                PAGE_REGEX.find(element.absUrl("href"))
                    ?.groupValues
                    ?.get(1)
                    ?.toIntOrNull()
            }
            .any { page -> page > currentPage }

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga? {
        val mangaLink = element.selectFirst(".book_avatar a[href], .book_name h3 a[href]") ?: return null
        val mangaTitle = element.selectFirst(".book_name h3 a, .book_name a")!!.text()

        return SManga.create().apply {
            setUrlWithoutDomain(mangaLink.absUrl("href"))
            title = mangaTitle
            thumbnail_url = element.selectFirst(".book_avatar img")
                ?.let { imageElement ->
                    imageElement.absUrl("data-src").ifEmpty {
                        imageElement.absUrl("src")
                    }
                }
                ?.ifEmpty { null }
        }
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst(".book_detail .book_other h1")!!.text()
            author = document.selectFirst("li.author p.col-xs-9")
                ?.text()
                ?.ifEmpty { null }
            genre = document.select(".book_other ul.list01 a")
                .joinToString { it.text() }
                .ifEmpty { null }
            description = document.selectFirst(".story-detail-info.detail-content")
                ?.text()
                ?.ifEmpty { null }
            status = parseStatus(document.selectFirst("li.status p.col-xs-9")?.text())
            thumbnail_url = document.selectFirst(".book_info .book_avatar img")
                ?.absUrl("src")
                ?.ifEmpty { null }
        }
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.contains("Đang Cập Nhật", ignoreCase = true) -> SManga.ONGOING
        statusText.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select(".list_chapter .works-chapter-item").map { element ->
            chapterFromElement(element)
        }
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        val chapterLink = element.selectFirst(".name-chap a[href]")!!
        setUrlWithoutDomain(chapterLink.absUrl("href"))
        name = chapterLink.text()
        date_upload = dateFormat.tryParse(
            element.selectFirst(".time-chap")?.text(),
        )
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val imageUrls = document.select("#chapter_content .chapter_content img.lozad, #chapter_content .chapter_content img")
            .map { element ->
                element.absUrl("data-src").ifEmpty {
                    element.absUrl("src")
                }
            }
            .filter { imageUrl ->
                imageUrl.isNotBlank() && !imageUrl.startsWith("data:")
            }
            .distinct()
            .ifEmpty {
                document.select("img.lozad[data-src], .page-chapter img[data-src]")
                    .map { element -> element.absUrl("data-src") }
                    .filter { imageUrl ->
                        imageUrl.isNotBlank() && !imageUrl.startsWith("data:")
                    }
                    .distinct()
            }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val PAGE_REGEX = Regex("""[?&]page=(\d+)""")

        private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }
}
