package eu.kanade.tachiyomi.extension.vi.thienthaitruyen

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
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ThienThaiTruyen : HttpSource() {
    override val name = "ThienThaiTruyen"
    override val lang = "vi"
    override val baseUrl = "https://thienthaitruyen8.com"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request = browseRequest(
        page = page,
        query = null,
        genre = null,
        status = "all",
        sort = "rating",
    )

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListPage(response.asJsoup())

    private fun parseMangaListPage(document: Document): MangasPage {
        val mangaElements = document.selectFirst("form#filters-container")
            ?.parent()
            ?.nextElementSibling()
            ?.select("a[href*=/truyen-tranh/]")
            ?.filter { it.selectFirst("span.line-clamp-2") != null }
            .orEmpty()

        val mangas = mangaElements
            .ifEmpty {
                document.select("a[href*=/truyen-tranh/]")
                    .filter { it.selectFirst("span.line-clamp-2") != null && it.selectFirst("img[src]") != null }
            }
            .map(::mangaFromElement)

        val hasNextPage = document.select("a[href*=page=]")
            .any { it.text().contains("Sau") }

        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        title = element.selectFirst("span.line-clamp-2")!!.text()
        thumbnail_url = element.selectFirst("img[src]")?.absUrl("src")
    }

    // ============================== Latest ================================
    override fun latestUpdatesRequest(page: Int): Request = browseRequest(
        page = page,
        query = null,
        genre = null,
        status = "all",
        sort = "latest",
    )

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListPage(response.asJsoup())

    // ============================== Search ================================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val appliedFilters = filters.ifEmpty { getFilterList() }
        val genre = appliedFilters.firstInstanceOrNull<GenreFilter>()?.selected
        val status = appliedFilters.firstInstanceOrNull<StatusFilter>()?.selected ?: "all"
        val sort = appliedFilters.firstInstanceOrNull<SortFilter>()?.selected ?: "latest"

        return browseRequest(
            page = page,
            query = query.takeIf(String::isNotBlank),
            genre = genre,
            status = status,
            sort = sort,
        )
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaListPage(response.asJsoup())

    private fun browseRequest(
        page: Int,
        query: String?,
        genre: String?,
        status: String,
        sort: String,
    ): Request {
        val url = "$baseUrl/tim-kiem-nang-cao".toHttpUrl().newBuilder().apply {
            query?.let { addQueryParameter("name", it) }
            genre?.let { addQueryParameter("genres", it) }
            addQueryParameter("sort", sort)
            addQueryParameter("status", status)
            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    // ============================== Filters ===============================
    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ===============================
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            author = document.infoValue("Tác giả")
            genre = document.select("h3:containsOwn(Thể loại) + div a[href*=/the-loai/]")
                .map(Element::text)
                .distinct()
                .joinToString()
                .ifEmpty { null }
            status = parseStatus(document.infoValue("Trạng thái"))
            description = document.selectFirst("p.comic-content.desk, p.comic-content.mobile, p.comic-content")
                ?.text()
            thumbnail_url = document.selectFirst("img[alt=poster]")?.absUrl("src")
        }
    }

    private fun Document.infoValue(label: String): String? = select("p, h3")
        .firstOrNull { it.text() == label && it.parents().none { parent -> parent.tagName() == "a" } }
        ?.nextElementSibling()
        ?.text()

    private fun parseStatus(status: String?): Int = when {
        status == null -> SManga.UNKNOWN
        status.contains("đang ra", ignoreCase = true) -> SManga.ONGOING
        status.contains("hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("div.chapter-items > a.flex.justify-between.items-center.w-full")
            .map { element ->
                SChapter.create().apply {
                    setUrlWithoutDomain(element.absUrl("href"))
                    name = element.selectFirst("p.text-sm.text-white.font-medium")?.text()
                        ?: element.text()
                    date_upload = parseChapterDate(element.selectFirst("p.text-xs span")?.text())
                }
            }
    }

    private fun parseChapterDate(dateStr: String?): Long {
        val relativeDate = parseRelativeDate(dateStr)
        if (relativeDate != 0L) return relativeDate
        return dateStr?.let(dateFormat::tryParse) ?: 0L
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))
        val number = NUMBER_REGEX.find(dateStr)?.value?.toIntOrNull() ?: return 0L

        when {
            dateStr.contains("giây") -> calendar.add(Calendar.SECOND, -number)
            dateStr.contains("phút") -> calendar.add(Calendar.MINUTE, -number)
            dateStr.contains("giờ") -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateStr.contains("ngày") -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateStr.contains("tuần") -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateStr.contains("tháng") -> calendar.add(Calendar.MONTH, -number)
            dateStr.contains("năm") -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // ============================== Pages =================================
    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val imageUrls = document
            .select("div.w-full.mx-auto.center img:not([title=banner])")
            .ifEmpty { document.select("div.center img:not([title=banner])") }
            .mapNotNull { image ->
                image.absUrl("src")
                    .takeIf(String::isNotBlank)
                    ?.takeUnless { it.contains("/banner/") }
            }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }

        private val NUMBER_REGEX = Regex("""\d+""")
    }
}
