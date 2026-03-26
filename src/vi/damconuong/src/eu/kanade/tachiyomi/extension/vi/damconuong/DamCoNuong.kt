package eu.kanade.tachiyomi.extension.vi.damconuong

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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class DamCoNuong : HttpSource() {
    override val name = "DamCoNuong"
    override val lang = "vi"
    override val baseUrl = "https://damconuong.plus"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(buildListUrl(page, POPULAR_SORT), headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaList(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request = GET(buildListUrl(page, LATEST_SORT), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaList(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: LATEST_SORT
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: DEFAULT_STATUS
        val searchType = filters.firstInstanceOrNull<SearchTypeFilter>()?.toUriPart() ?: "name"
        val selectedGenres = filters.firstInstanceOrNull<GenreFilter>()
            ?.state
            ?.filter { it.state }
            ?.joinToString(",") { it.id }

        val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
            .addQueryParameter("sort", sort)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("filter[status]", status)
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("filter[$searchType]", query)
                }
                if (!selectedGenres.isNullOrEmpty()) {
                    addQueryParameter("filter[accept_genres]", selectedGenres)
                }
            }
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaList(response)

    private fun buildListUrl(page: Int, sort: String) = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
        .addQueryParameter("sort", sort)
        .addQueryParameter("filter[status]", DEFAULT_STATUS)
        .addQueryParameter("page", page.toString())
        .build()

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.manga-vertical").map { element ->
            SManga.create().apply {
                val titleElement = element.selectFirst("h3 a")!!
                title = titleElement.text()
                setUrlWithoutDomain(titleElement.absUrl("href"))

                val imageElement = element.selectFirst("div.cover-frame img")
                thumbnail_url = imageElement?.absUrl("src")
                    ?.ifEmpty { imageElement.absUrl("data-src") }
                    ?.ifEmpty { null }
            }
        }

        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage =
            document.selectFirst("nav[aria-label=Pagination] a[href*=\"page=${currentPage + 1}\"]") != null

        return MangasPage(mangas, hasNextPage)
    }

    // =============================== Details ==============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1.text-xl.ml-1, h1.text-xl")!!.text()

            val imageElement = document.selectFirst("div.cover-frame img")
            thumbnail_url = imageElement?.absUrl("src")
                ?.ifEmpty { imageElement.absUrl("data-src") }
                ?.ifEmpty { null }

            author = document.selectFirst("span:containsOwn(Author:) + span a")?.text()
            genre = document.select("#genres-list a")
                .joinToString { it.text() }
                .ifEmpty { null }

            status = parseStatus(
                document.selectFirst("span:containsOwn(Tình trạng:)")?.parent()?.select("span")?.last()?.text(),
            )

            val descriptionElement = document.selectFirst("div.prose.dark\\:prose-invert.max-w-none")
            description = descriptionElement?.text()
                ?.ifEmpty { null }
        }
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText?.contains("Đang tiến hành", ignoreCase = true) == true -> SManga.ONGOING
        statusText?.contains("Hoàn thành", ignoreCase = true) == true -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("#chapterList > a.block").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.absUrl("href"))
                name = element.selectFirst("div.grow span")!!.text()
                date_upload = parseChapterDate(
                    element.selectFirst("span.ml-2.whitespace-nowrap")?.text(),
                )
            }
        }
    }

    private fun parseChapterDate(dateStr: String?): Long {
        val relativeDate = parseRelativeDate(dateStr)
        if (relativeDate != 0L) return relativeDate
        return DATE_FORMAT.tryParse(dateStr)
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"), Locale.ROOT)
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

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select("#chapter-content img.chapter-img")
            .ifEmpty { document.select("#chapter-content img") }

        return images.mapIndexedNotNull { index, element ->
            val imageUrl = element.absUrl("data-src")
                .ifEmpty { element.absUrl("src") }
                .replace("\r", "")
                .ifEmpty { null }
                ?: return@mapIndexedNotNull null

            Page(index, imageUrl = imageUrl)
        }.distinctBy { it.imageUrl }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    companion object {
        private const val LATEST_SORT = "-updated_at"
        private const val POPULAR_SORT = "-views"
        private const val DEFAULT_STATUS = "2,1"

        private val NUMBER_REGEX = Regex("\\d+")

        private val DATE_FORMAT by lazy {
            SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }
    }
}
