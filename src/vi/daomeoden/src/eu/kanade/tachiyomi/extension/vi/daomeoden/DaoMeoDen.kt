package eu.kanade.tachiyomi.extension.vi.daomeoden

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
import kotlinx.serialization.Serializable
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DaoMeoDen : HttpSource() {
    override val name = "DaoMeoDen"
    override val lang = "vi"
    override val baseUrl = "https://daomeoden.net"
    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl$LIST_PATH".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("order", POPULAR_ORDER)
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = parseBrowsePage(response)

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl$LIST_PATH".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = parseBrowsePage(response)

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val filterList = filters.ifEmpty { getFilterList() }

        val status = filterList.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: DEFAULT_STATUS
        val category = filterList.firstInstanceOrNull<CategoryFilter>()?.toUriPart() ?: DEFAULT_CATEGORY
        val genre = filterList.firstInstanceOrNull<GenreFilter>()?.toUriPart() ?: DEFAULT_GENRE
        val explicit = filterList.firstInstanceOrNull<ExplicitFilter>()?.toUriPart() ?: DEFAULT_EXPLICIT
        val order = filterList.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: DEFAULT_ORDER

        val url = "$baseUrl$LIST_PATH".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("textSearch", query)
                }
                if (status != DEFAULT_STATUS) {
                    addQueryParameter("status", status)
                }
                if (category != DEFAULT_CATEGORY) {
                    addQueryParameter("category", category)
                }
                if (genre != DEFAULT_GENRE) {
                    addQueryParameter("genre", genre)
                }
                if (explicit != DEFAULT_EXPLICIT) {
                    addQueryParameter("explicit", explicit)
                }
                if (order != DEFAULT_ORDER) {
                    addQueryParameter("order", order)
                }
            }
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseBrowsePage(response)

    // ============================== Manga List ============================

    private fun parseBrowsePage(response: Response): MangasPage {
        val document = response.asJsoup()

        val apiRequest = buildBookListApiRequest(document, response.request.url.toString())
            ?: return MangasPage(emptyList(), false)

        val payload = client.newCall(apiRequest).execute().use { apiResponse ->
            apiResponse.parseAs<BookListApiResponse>()
        }

        val listDocument = parsePayloadDocument(payload.status, payload.htmlBook)
            ?: return MangasPage(emptyList(), false)

        val mangas = listDocument.select("div.item-list").map { mangaFromElement(it) }
        val currentPage = extractScriptVariable(document, "pageCurrent")
            ?.toIntOrNull()
            ?: response.request.url.queryParameter("page")?.toIntOrNull()
            ?: 1
        val lastPage = extractScriptVariable(document, "pageLast")?.toIntOrNull() ?: currentPage

        return MangasPage(mangas, currentPage < lastPage)
    }

    private fun buildBookListApiRequest(document: Document, referer: String): Request? {
        val token = extractScriptVariable(document, "_token") ?: return null
        val pageCurrent = extractScriptVariable(document, "pageCurrent") ?: return null
        val pageLast = extractScriptVariable(document, "pageLast") ?: return null
        val status = extractScriptVariable(document, "status") ?: DEFAULT_STATUS
        val ages = extractScriptVariable(document, "ages").orEmpty()
        val category = extractScriptVariable(document, "category") ?: DEFAULT_CATEGORY
        val genre = extractScriptVariable(document, "genre").orEmpty()
        val explicit = extractScriptVariable(document, "explicit").orEmpty()
        val magazine = extractScriptVariable(document, "magazine").orEmpty()
        val tags = extractScriptVariable(document, "tags").orEmpty()
        val order = extractScriptVariable(document, "order") ?: DEFAULT_ORDER
        val pagiParam = extractScriptVariable(document, "pagiParam") ?: return null
        val textSearch = extractScriptVariable(document, "textSearch").orEmpty()

        val body = FormBody.Builder()
            .add("token", token)
            .add("pageCurrent", pageCurrent)
            .add("pageLast", pageLast)
            .add("status", status)
            .add("ages", ages)
            .add("category", category)
            .add("genre", genre)
            .add("explicit", explicit)
            .add("magazine", magazine)
            .add("tags", tags)
            .add("order", order)
            .add("pagiParam", pagiParam)
            .add("textSearch", textSearch)
            .build()

        val apiHeaders = headers.newBuilder()
            .set("Origin", baseUrl)
            .set("Referer", referer)
            .set("X-Requested-With", "XMLHttpRequest")
            .build()

        return POST("$baseUrl/apps/controllers/book/bookList.php", apiHeaders, body)
    }

    // ============================== Details ===============================

    private fun mangaFromElement(element: Element): SManga = SManga.create().apply {
        val titleElement = element.selectFirst("div.item-title a")!!

        title = titleElement.text()
        setUrlWithoutDomain(titleElement.absUrl("href"))

        thumbnail_url = element.selectFirst("div.item-cover img")
            ?.absUrl("src")
            ?.normalizeImageUrl()
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
            title = document.selectFirst("div.info-name")!!.text()
            thumbnail_url = document.selectFirst("div.info-cover-img img")
                ?.absUrl("src")
                ?.normalizeImageUrl()
            genre = document.select("div.info-tag.tag-category span, div.info-tag.tag-genre span, div.info-tag.tag-tag span")
                .joinToString { it.text() }
                .ifEmpty { null }
            status = parseStatus(document.selectFirst("div.info-tag.tag-status span")?.text())
            description = document.selectFirst("div.info-description div.content")?.text()
        }
    }

    private fun parseStatus(statusText: String?): Int = when {
        statusText == null -> SManga.UNKNOWN
        statusText.contains("ongoing", true) -> SManga.ONGOING
        statusText.contains("full", true) -> SManga.COMPLETED
        statusText.contains("completed", true) -> SManga.COMPLETED
        statusText.contains("hoàn", true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()

        return document.select("div#TabChapterChapter div.chapter").mapNotNull { element ->
            val chapterUrl = chapterUrlRegex.find(element.attr("onclick"))
                ?.groupValues
                ?.get(1)
                ?: return@mapNotNull null

            SChapter.create().apply {
                setUrlWithoutDomain(chapterUrl)
                name = element.selectFirst("div.chapter-info div.name-sub")
                    ?.text()
                    ?: element.selectFirst("div.chapter-info div.name")!!.text()
                date_upload = chapterDateFormat.tryParse(element.selectFirst("div.chapter-info div.time > div")?.text())
                chapterNumberRegex.find(name)?.value?.toFloatOrNull()?.let { chapter_number = it }
            }
        }
    }

    // ============================== Pages =================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val chapterId = extractScriptVariable(document, "chapterId") ?: return emptyList()
        val token = extractScriptVariable(document, "_token") ?: return emptyList()

        val apiHeaders = headers.newBuilder()
            .set("Origin", baseUrl)
            .set("Referer", response.request.url.toString())
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
        val body = FormBody.Builder()
            .add("token", token)
            .add("chapterId", chapterId)
            .add("cookies", CHAPTER_COOKIES)
            .build()
        val apiRequest = POST("$baseUrl/apps/controllers/book/bookChapterContent.php", apiHeaders, body)

        val payload = client.newCall(apiRequest).execute().use { apiResponse ->
            apiResponse.parseAs<ChapterContentApiResponse>()
        }

        val chapterDocument = parsePayloadDocument(payload.status, payload.data)
            ?: return emptyList()

        return chapterDocument.select("img").mapIndexedNotNull { index, element ->
            val imageUrl = element.absUrl("data-src")
                .ifEmpty { element.absUrl("src") }
                .normalizeImageUrl()

            if (imageUrl.isEmpty()) {
                return@mapIndexedNotNull null
            }

            Page(index, response.request.url.toString(), imageUrl)
        }.distinctBy { it.imageUrl }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Helpers ===============================

    private fun extractScriptVariable(document: Document, key: String): String? = Regex("""var\s+$key\s*=\s*'([^']*)'""")
        .find(document.html())
        ?.groupValues
        ?.get(1)

    private fun parsePayloadDocument(status: Int, html: String?): Document? {
        if (status != 200) return null
        val payloadHtml = html ?: return null
        return Jsoup.parseBodyFragment(payloadHtml, baseUrl)
    }

    private fun String.normalizeImageUrl(): String = if (startsWith("//")) "https:$this" else this

    @Serializable
    private class BookListApiResponse(
        val status: Int = 0,
        val mess: String? = null,
        val htmlBook: String? = null,
        val htmlPagi: String? = null,
    )

    @Serializable
    private class ChapterContentApiResponse(
        val status: Int = 0,
        val mess: String? = null,
        val data: String? = null,
    )

    companion object {
        private const val LIST_PATH = "/danh-sach-truyen-tranh.html"
        private const val POPULAR_ORDER = "viewsAll"
        private const val DEFAULT_STATUS = "0"
        private const val DEFAULT_CATEGORY = "all"
        private const val DEFAULT_GENRE = "0"
        private const val DEFAULT_EXPLICIT = "0"
        private const val DEFAULT_ORDER = "updated_at"
        private const val CHAPTER_COOKIES = "W10="

        private val chapterUrlRegex = Regex("""openUrl\('([^']+)'\)""")
        private val chapterNumberRegex = Regex("""\d+(?:\.\d+)?""")

        private val chapterDateFormat by lazy {
            SimpleDateFormat("dd.MM.yyyy - HH:mm", Locale.ROOT).apply {
                timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
            }
        }
    }
}
