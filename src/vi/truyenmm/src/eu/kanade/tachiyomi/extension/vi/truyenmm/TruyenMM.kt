package eu.kanade.tachiyomi.extension.vi.truyenmm

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@Source
abstract class TruyenMM : HttpSource() {
    override val supportsLatest = true

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/danh-sach-truyen/$page", headers)

    override fun popularMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/truyen-moi-cap-nhat/$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = parseMangaListPage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$baseUrl/tim-kiem".toHttpUrl().newBuilder()
                .addQueryParameter("key", query)
                .addQueryParameter("page", page.toString())
                .build()
            return GET(url, headers)
        }

        val genreSlug = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
            ?: return popularMangaRequest(page)

        return GET("$baseUrl/the-loai/$genreSlug/$page", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseMangaListPage(response)

    private fun parseMangaListPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("article:has(a[href^=/truyen/])").map { element ->
            SManga.create().apply {
                val mangaLink = element.selectFirst("a[href^=/truyen/]")!!
                title = element.selectFirst("h2, h3")!!.text()
                setUrlWithoutDomain(mangaLink.absUrl("href"))
                thumbnail_url = element.selectFirst("img")?.extractImageUrl()
            }
        }

        return MangasPage(mangaList, hasNextPage(document, response.request.url.toString()))
    }

    private fun hasNextPage(document: Document, requestUrl: String): Boolean {
        if (document.selectFirst("link[rel=next]") != null) return true

        val currentUrl = requestUrl.toHttpUrlOrNull() ?: return false
        val currentPage = currentUrl.queryParameter("page")?.toIntOrNull()
            ?: currentUrl.pathSegments.lastOrNull()?.toIntOrNull()
            ?: 1
        val nextPage = currentPage + 1

        return document.select("a[href]").any { anchor ->
            val pageUrl = anchor.absUrl("href").toHttpUrlOrNull() ?: return@any false
            val pageValue = pageUrl.queryParameter("page")?.toIntOrNull()
                ?: pageUrl.pathSegments.lastOrNull()?.toIntOrNull()
            pageValue == nextPage
        }
    }

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ===============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("h1")!!.text()
            thumbnail_url = document.selectFirst("img[alt*=Bìa], img[alt*=bìa]")?.extractImageUrl()

            author = findInfoValue(document, "Tác giả")
            status = parseStatus(findInfoValue(document, "Loại Truyện"))
            genre = document.select("dd a[href*='/the-loai/']")
                .map(Element::text)
                .distinct()
                .joinToString()
                .ifEmpty { null }
        }
    }

    private fun findInfoValue(document: Document, label: String): String? = document.select("dl > div").firstOrNull {
        it.selectFirst("dt")?.text()?.startsWith(label, ignoreCase = true) == true
    }?.selectFirst("dd")?.text()?.ifEmpty { null }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.contains("hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        statusText.contains("đang tiến hành", ignoreCase = true) -> SManga.ONGOING
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        val topicId = document.selectFirst("script#script-chapter")?.attr("data-id")
        val topic = topicId?.let(::fetchTopic)
        if (topic != null) {
            return topic.chapters.orEmpty().mapNotNull { chapter ->
                val chapterId = chapter.id ?: return@mapNotNull null
                val chapterName = chapter.name ?: return@mapNotNull null
                val chapterUrl = getChapterUrl(chapterId)

                SChapter.create().apply {
                    setUrlWithoutDomain(chapterUrl)
                    name = chapterName
                    date_upload = chapter.update_time ?: 0L
                }
            }
        }

        return document.select("#chapter-list a[href*='/chapter-']").map { chapterElement ->
            SChapter.create().apply {
                setUrlWithoutDomain(chapterElement.absUrl("href"))
                name = chapterElement.selectFirst("span")?.text() ?: chapterElement.text()
                date_upload = parseChapterDate(chapterElement.selectFirst("time")?.text())
            }
        }
    }

    private fun parseChapterDate(dateText: String?): Long {
        if (dateText == null) return 0L
        val normalized = dateText.replace("🗓", "")
        return parseRelativeDate(normalized).takeIf { it != 0L }
            ?: chapterDateFormat.tryParse(normalized)
    }

    private fun parseRelativeDate(dateText: String): Long {
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"))

        if (dateText.contains("vừa xong", ignoreCase = true)) {
            return calendar.timeInMillis
        }

        val number = DATE_NUMBER_REGEX.find(dateText)?.value?.toIntOrNull() ?: return 0L

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

    private fun fetchTopic(topicId: String): TruyenMMTopic? {
        val url = "$baseUrl/api/get-topic".toHttpUrl().newBuilder()
            .addQueryParameter("id", topicId)
            .build()

        return runCatching {
            client.newCall(GET(url, headers)).execute().use { response ->
                if (!response.isSuccessful) return@use null
                response.parseAs<TruyenMMGetTopicResponse>().topic
            }
        }.getOrNull()
    }

    private fun getChapterUrl(rawChapterId: String): String {
        val normalizedChapterId = rawChapterId.replace("-chapter-", "/chapter-")
        val splitIndex = normalizedChapterId.indexOf("/chapter-")
        if (splitIndex == -1) {
            return "$baseUrl/truyen/$normalizedChapterId"
        }

        val mangaId = normalizedChapterId.substring(0, splitIndex)
        val chapterId = normalizedChapterId.substring(splitIndex + 1)
        return "$baseUrl/truyen/$mangaId/$chapterId"
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()

        val imageUrls = document.select("div.w-full.flex.flex-col.items-center img").ifEmpty {
            document.select("img[data-src], img[src]")
        }.mapNotNull { imageElement ->
            imageElement.extractImageUrl()
                ?.takeIf { it.isNotEmpty() }
                ?.takeIf { url ->
                    !url.contains("/chapter-") &&
                        !url.endsWith("/loading.webp") &&
                        !url.endsWith("/page_logo.png")
                }
        }.distinct()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Element.extractImageUrl(): String? {
        val dataSrc = attr("data-src")
        if (dataSrc.isNotBlank()) {
            return absUrl("data-src")
        }

        val src = attr("src")
        if (src.isNotBlank()) {
            return absUrl("src")
        }

        return null
    }

    companion object {
        private val DATE_NUMBER_REGEX = Regex("""\d+""")

        private val chapterDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }
}
