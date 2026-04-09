package eu.kanade.tachiyomi.extension.vi.truyenhentaiz

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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TruyenHentaiz : HttpSource() {
    override val name = "TruyenHentaiz"

    override val lang = "vi"

    override val baseUrl = "https://truyenhentaiz.net"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(buildPagedUrl("/xem-nhieu-nhat", page), headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = GET(buildPagedUrl("/moi-cap-nhat", page), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = baseUrl.toHttpUrl().newBuilder().apply {
                if (page > 1) {
                    addPathSegment("page")
                    addPathSegment(page.toString())
                }
                addQueryParameter("s", query)
            }.build()

            return GET(url, headers)
        }

        val genreSlug = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        if (genreSlug != null) {
            return GET(buildPagedUrl("/category/$genreSlug", page), headers)
        }

        return popularMangaRequest(page)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangaList = document.select("section.container-box-manga .card.card-manga")
            .map { element ->
                SManga.create().apply {
                    val linkElement = element.selectFirst("div.card-manga-body > a[href]")!!
                    val thumbnailElement: Element? = element.selectFirst("img.card-img-top")
                    title = linkElement.selectFirst("h2.card-manga-title")!!.text()
                    setUrlWithoutDomain(linkElement.absUrl("href"))
                    thumbnail_url = if (thumbnailElement != null) {
                        imageUrlFromElement(thumbnailElement)
                    } else {
                        null
                    }
                }
            }

        val currentPage = parseCurrentPage(response.request.url)
        val hasNextPage = document.selectFirst(".pagination a.page-link[data-page=${currentPage + 1}]") != null

        return MangasPage(mangaList, hasNextPage)
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val mangaInfoElement = document.selectFirst(".card.mb-3 .manga-info")
        val thumbnailElement: Element? = document.selectFirst(".card.mb-3 img.single-thumbnail, .card.mb-3 img")

        return SManga.create().apply {
            title = document.selectFirst(".card.mb-3 h3.card-title, .pagetitle h1")!!.text()
            thumbnail_url = if (thumbnailElement != null) {
                imageUrlFromElement(thumbnailElement)
            } else {
                null
            }
            status = parseStatus(mangaInfoElement?.selectFirst("span:contains(Status:) strong")?.text())
            genre = mangaInfoElement?.select(".categories a")
                ?.joinToString { it.text() }
                ?.ifEmpty { null }
            description = document.selectFirst(".card.mb-3 p.desc")
                ?.text()
                ?.ifEmpty { null }
        }
    }

    private fun parseStatus(statusText: String?): Int {
        val normalizedStatus = statusText?.lowercase(Locale.ROOT)

        return when {
            normalizedStatus == null -> SManga.UNKNOWN
            "đang tiến hành" in normalizedStatus -> SManga.ONGOING
            "đang cập nhật" in normalizedStatus -> SManga.ONGOING
            "hoàn thành" in normalizedStatus -> SManga.COMPLETED
            "tạm ngưng" in normalizedStatus -> SManga.ON_HIATUS
            "tạm dừng" in normalizedStatus -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("div.card:has(h2.card-title:contains(Chapters)) li.list-group-item:has(a[href])")
            .ifEmpty { document.select("li.list-group-item:has(a[href])") }
            .map { element ->
                SChapter.create().apply {
                    val chapterLinkElement = element.selectFirst("a[href]")!!
                    name = chapterLinkElement.selectFirst("span.fw-bold")?.text() ?: chapterLinkElement.text()
                    setUrlWithoutDomain(chapterLinkElement.absUrl("href"))
                    date_upload = parseChapterDate(element.selectFirst("em")?.text())
                }
            }
    }

    private fun parseChapterDate(dateText: String?): Long {
        if (dateText.isNullOrBlank()) return 0L

        val normalizedDate = MULTIPLE_SPACES_REGEX.replace(dateText, " ")

        val relativeDate = parseRelativeDate(normalizedDate)
        if (relativeDate != 0L) return relativeDate

        return chapterDateTimeFormat.tryParse(normalizedDate)
            .takeIf { it > 0L }
            ?: chapterDateFormat.tryParse(normalizedDate)
    }

    private fun parseRelativeDate(dateText: String): Long {
        val normalizedDate = dateText.lowercase(Locale.ROOT)
        val calendar = Calendar.getInstance(vietnamTimeZone)

        if (normalizedDate == "mới" || normalizedDate.contains("vừa xong")) {
            return calendar.timeInMillis
        }

        val number = RELATIVE_DATE_NUMBER_REGEX.find(normalizedDate)?.value?.toIntOrNull() ?: return 0L

        when {
            normalizedDate.contains("giây") -> calendar.add(Calendar.SECOND, -number)
            normalizedDate.contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            normalizedDate.contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            normalizedDate.contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            normalizedDate.contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            normalizedDate.contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            normalizedDate.contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val imageUrls = document.select("#chapter-content img[src], #chapter-content img[data-src]")
            .mapNotNull { imageUrlFromElement(it) }
            .filterNot { it.startsWith("data:") }
            .ifEmpty {
                document.select("#chapter-content img, .chapter-content img")
                    .mapNotNull { imageUrlFromElement(it) }
                    .filterNot { it.startsWith("data:") }
            }
            .distinct()

        if (imageUrls.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun parseCurrentPage(url: HttpUrl): Int {
        val pageIndex = url.pathSegments.indexOf("page")

        return url.pathSegments.getOrNull(pageIndex + 1)?.toIntOrNull() ?: 1
    }

    private fun buildPagedUrl(path: String, page: Int): String = if (page > 1) {
        "$baseUrl$path/page/$page"
    } else {
        "$baseUrl$path"
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    private fun imageUrlFromElement(element: Element): String? = element.absUrl("src")
        .ifBlank { element.absUrl("data-src") }
        .ifBlank { null }

    companion object {
        private val MULTIPLE_SPACES_REGEX = Regex("\\s+")
        private val RELATIVE_DATE_NUMBER_REGEX = Regex("\\d+")
        private val vietnamTimeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")

        private val chapterDateTimeFormat = SimpleDateFormat("HH:mm dd-MM-yyyy", Locale.ROOT).apply {
            timeZone = vietnamTimeZone
        }

        private val chapterDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.ROOT).apply {
            timeZone = vietnamTimeZone
        }
    }
}
