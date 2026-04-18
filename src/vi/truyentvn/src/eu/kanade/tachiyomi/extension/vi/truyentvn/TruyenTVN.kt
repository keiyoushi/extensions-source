package eu.kanade.tachiyomi.extension.vi.truyentvn

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class TruyenTVN : HttpSource() {
    override val name = "TruyenTVN"

    override val lang = "vi"

    override val baseUrl = "https://truyentvn.net"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(5)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val ajaxHeaders by lazy {
        headersBuilder()
            .add("X-Requested-With", "XMLHttpRequest")
            .add("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            .build()
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET(buildPagedUrl(POPULAR_PATH, page), headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response.asJsoup())

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = GET(buildPagedUrl(LATEST_PATH, page), headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaPage(response.asJsoup())

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            return searchAjaxRequest(query)
        }

        val genreUri = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
        val topicUri = filters.firstInstanceOrNull<TopicFilter>()?.toUriPart()
        val countryUri = filters.firstInstanceOrNull<CountryFilter>()?.toUriPart()

        val selectedUri = genreUri ?: topicUri ?: countryUri ?: return popularMangaRequest(page)

        return GET(buildPagedUrl(selectedUri, page), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val isAjaxSearchResponse = response.request.url.encodedPath.endsWith(AJAX_PATH)
        if (!isAjaxSearchResponse) {
            return parseMangaPage(response.asJsoup())
        }

        val searchResponse = response.parseAs<SearchAjaxResponseDto>()
        val searchSeries = searchResponse.series()
        val mangaList = searchSeries.map { series ->
            SManga.create().apply {
                title = series.title()
                setUrlWithoutDomain(series.url())
                thumbnail_url = series.thumbnail()
            }
        }

        return MangasPage(mangaList, false)
    }

    private fun searchAjaxRequest(query: String): Request {
        val url = "$baseUrl$AJAX_PATH".toHttpUrl().newBuilder().build()
        val formBody = FormBody.Builder()
            .add("action", "baka_ajax")
            .add("type", "search_series")
            .add("q", query)
            .build()

        return POST(url.toString(), ajaxHeaders, formBody)
    }

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val mangaId = document.selectFirst("input#post_manga_id")?.attr("value")

        val thumbnailFromFirstChapter = mangaId?.let { fetchFirstChapterThumbnail(it) }
        val fallbackThumbnail = document.selectFirst("#ratingModalCover, #series-thumbnail img, img[alt]")?.absUrl("src")

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = thumbnailFromFirstChapter ?: fallbackThumbnail
            author = parseAuthor(document)
            genre = document.select("#genres-tags-container a[href]").joinToString { it.text() }
            status = parseStatus(document.selectFirst("span:has(i[title='Trạng thái'])")?.text())
            description = document.selectFirst("#synopsisText")?.text()
        }
    }

    private fun parseAuthor(document: Document): String? {
        val preferredAuthor = document.selectFirst("span:has(i[title='Tác Giả']) > span")?.text()
        if (!preferredAuthor.isNullOrBlank()) {
            return preferredAuthor
        }

        return document.selectFirst("span:has(i[title='Tác Giả'])")?.text()
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.contains("đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        statusText.contains("đang cập nhật", ignoreCase = true) -> SManga.ONGOING
        statusText.contains("hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    private fun fetchFirstChapterThumbnail(parentId: String): String? {
        val chapterPage = fetchChapterPage(parentId = parentId, page = 1, perPage = 1)
        val chapterHtml = chapterPage?.html() ?: return null
        val chapterDocument = Jsoup.parseBodyFragment(chapterHtml, baseUrl)
        return chapterDocument.selectFirst("div.comic-card img")?.let { imageElement: Element ->
            imageElement.absUrl("src").ifEmpty { imageElement.absUrl("data-src") }
        }
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaId = document.selectFirst("input#post_manga_id")?.attr("value") ?: return emptyList()

        val firstPage = fetchChapterPage(parentId = mangaId, page = 1) ?: return emptyList()
        val allChapterHtml = mutableListOf<String>()
        firstPage.html()?.let { allChapterHtml.add(it) }

        val totalPages = extractTotalChapterPages(firstPage.pagination())
        if (totalPages > 1) {
            for (page in 2..totalPages) {
                val chapterPage = fetchChapterPage(parentId = mangaId, page = page) ?: continue
                chapterPage.html()?.let { allChapterHtml.add(it) }
            }
        }

        return allChapterHtml.flatMap { chapterHtml ->
            val chapterDocument = Jsoup.parseBodyFragment(chapterHtml, baseUrl)

            chapterDocument.select("div.comic-card > a[href]").map { chapterElement ->
                SChapter.create().apply {
                    name = chapterElement.attr("title").ifEmpty {
                        chapterElement.selectFirst("h3")!!.text()
                    }
                    setUrlWithoutDomain(chapterElement.absUrl("href"))
                    date_upload = parseChapterDate(
                        chapterElement.selectFirst("div.absolute.top-2.left-2 span, span.text-white")?.text(),
                    )
                }
            }
        }
    }

    private fun fetchChapterPage(
        parentId: String,
        page: Int,
        order: String = CHAPTER_ORDER_NEWEST,
        perPage: Int = CHAPTERS_PER_PAGE,
    ): ChaptersAjaxDataDto? {
        val request = buildChapterPageRequest(
            parentId = parentId,
            page = page,
            order = order,
            perPage = perPage,
        )
        val response = client.newCall(request).execute()
        response.use {
            val chapterResponse = it.parseAs<ChaptersAjaxResponseDto>()
            return chapterResponse.data()
        }
    }

    private fun buildChapterPageRequest(
        parentId: String,
        page: Int,
        order: String,
        perPage: Int,
    ): Request {
        val formBody = FormBody.Builder()
            .add("action", "baka_ajax")
            .add("type", "load_chapters_paginated")
            .add("parent_id", parentId)
            .add("page", page.toString())
            .add("order", order)
            .add("per_page", perPage.toString())
            .build()

        val url = "$baseUrl$AJAX_PATH".toHttpUrl().newBuilder().build()
        return POST(url.toString(), ajaxHeaders, formBody)
    }

    private fun extractTotalChapterPages(paginationHtml: String?): Int {
        if (paginationHtml.isNullOrBlank()) return 1

        return CHAPTER_PAGE_REGEX.findAll(paginationHtml)
            .map { it.groupValues[1].toIntOrNull() ?: 1 }
            .maxOrNull() ?: 1
    }

    private fun parseChapterDate(dateText: String?): Long {
        if (dateText.isNullOrBlank()) return 0L

        val relativeDate = parseRelativeDate(dateText)
        if (relativeDate != 0L) return relativeDate

        return chapterDateFormat.tryParse(dateText)
    }

    private fun parseRelativeDate(dateText: String): Long {
        if (dateText.contains("vừa xong", ignoreCase = true)) {
            return Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh")).timeInMillis
        }

        val number = DATE_NUMBER_REGEX.find(dateText)?.value?.toIntOrNull() ?: return 0L
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))

        when {
            dateText.contains("giây", ignoreCase = true) -> calendar.add(Calendar.SECOND, -number)
            dateText.contains("phút", ignoreCase = true) -> calendar.add(Calendar.MINUTE, -number)
            dateText.contains("giờ", ignoreCase = true) -> calendar.add(Calendar.HOUR_OF_DAY, -number)
            dateText.contains("ngày", ignoreCase = true) -> calendar.add(Calendar.DAY_OF_MONTH, -number)
            dateText.contains("tuần", ignoreCase = true) -> calendar.add(Calendar.WEEK_OF_YEAR, -number)
            dateText.contains("tháng", ignoreCase = true) -> calendar.add(Calendar.MONTH, -number)
            dateText.contains("năm", ignoreCase = true) -> calendar.add(Calendar.YEAR, -number)
            else -> return 0L
        }

        return calendar.timeInMillis
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val images = document.select("main.webtoon-mode img.page-image")
            .ifEmpty { document.select("#webtoonContainer img.page-image, #webtoonContainer img, main img") }

        val pageList = images.mapIndexedNotNull { index, imageElement ->
            val imageUrl = imageElement.absUrl("src")
                .ifEmpty { imageElement.absUrl("data-original-src") }
            if (imageUrl.isEmpty()) return@mapIndexedNotNull null
            Page(index, imageUrl = imageUrl)
        }

        if (pageList.isEmpty()) {
            throw Exception("Không tìm thấy hình ảnh")
        }

        return pageList
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    private fun parseMangaPage(document: Document): MangasPage {
        val mangaList = document.select("main div.comic-card > a[href]").map { mangaElement ->
            SManga.create().apply {
                title = mangaElement.attr("title").ifEmpty {
                    mangaElement.selectFirst("h3, img[alt]")!!.let { titleElement: Element ->
                        if (titleElement.tagName() == "img") {
                            titleElement.attr("alt")
                        } else {
                            titleElement.text()
                        }
                    }
                }
                setUrlWithoutDomain(mangaElement.absUrl("href"))
                thumbnail_url = mangaElement.selectFirst("img")?.let { imageElement: Element ->
                    imageElement.absUrl("src").ifEmpty { imageElement.absUrl("data-src") }
                }
            }
        }

        val hasNextPage = document.selectFirst("link[rel=next], a[title='Tiếp']") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private fun buildPagedUrl(path: String, page: Int): String = if (page > 1) {
        "$baseUrl$path/page/$page"
    } else {
        "$baseUrl$path"
    }

    companion object {
        private const val AJAX_PATH = "/wp-admin/admin-ajax.php"
        private const val LATEST_PATH = "/moi-cap-nhat"
        private const val POPULAR_PATH = "/xem-nhieu-nhat"

        private const val CHAPTER_ORDER_NEWEST = "newest_first"
        private const val CHAPTERS_PER_PAGE = 16

        private val chapterDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }

        private val CHAPTER_PAGE_REGEX = Regex("""data-page="(\d+)"""")
        private val DATE_NUMBER_REGEX = Regex("\\d+")
    }
}
