package eu.kanade.tachiyomi.extension.vi.daomeoden

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class DaoMeoDen : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = rateLimit(3)

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl$listPath".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("order", popularOrder)
            .build()

        return parseBrowsePage(client.get(url))
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl$listPath".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .build()

        return parseBrowsePage(client.get(url))
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: defaultStatus
        val category = filters.firstInstanceOrNull<CategoryFilter>()?.toUriPart() ?: defaultCategory
        val genre = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart() ?: defaultGenre
        val explicit = filters.firstInstanceOrNull<ExplicitFilter>()?.toUriPart() ?: defaultExplicit
        val order = filters.firstInstanceOrNull<SortFilter>()?.toUriPart() ?: defaultOrder

        val url = "$baseUrl$listPath".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .apply {
                if (query.isNotEmpty()) {
                    addQueryParameter("textSearch", query)
                }
                if (status != defaultStatus) {
                    addQueryParameter("status", status)
                }
                if (category != defaultCategory) {
                    addQueryParameter("category", category)
                }
                if (genre != defaultGenre) {
                    addQueryParameter("genre", genre)
                }
                if (explicit != defaultExplicit) {
                    addQueryParameter("explicit", explicit)
                }
                if (order != defaultOrder) {
                    addQueryParameter("order", order)
                }
            }
            .build()

        return parseBrowsePage(client.get(url))
    }

    // ============================== Manga List ============================

    private suspend fun parseBrowsePage(response: Response): MangasPage {
        val requestUrl = response.request.url
        val document = response.asJsoup()
        val payload = fetchBookList(document, requestUrl.toString())
            ?.parseAs<BookListApiResponse>()
            ?: return MangasPage(emptyList(), false)

        val listDocument = parsePayloadDocument(payload.status, payload.htmlBook)
            ?: return MangasPage(emptyList(), false)

        val mangas = listDocument.select("div.item-list").map { mangaFromElement(it) }
        val currentPage = extractScriptVariable(document, "pageCurrent")
            ?.toIntOrNull()
            ?: requestUrl.queryParameter("page")?.toIntOrNull()
            ?: 1
        val lastPage = extractScriptVariable(document, "pageLast")?.toIntOrNull() ?: currentPage

        return MangasPage(mangas, currentPage < lastPage)
    }

    private suspend fun fetchBookList(document: Document, referer: String): Response? {
        val token = extractScriptVariable(document, "_token") ?: return null
        val pageCurrent = extractScriptVariable(document, "pageCurrent") ?: return null
        val pageLast = extractScriptVariable(document, "pageLast") ?: return null
        val status = extractScriptVariable(document, "status") ?: defaultStatus
        val ages = extractScriptVariable(document, "ages").orEmpty()
        val category = extractScriptVariable(document, "category") ?: defaultCategory
        val genre = extractScriptVariable(document, "genre").orEmpty()
        val explicit = extractScriptVariable(document, "explicit").orEmpty()
        val magazine = extractScriptVariable(document, "magazine").orEmpty()
        val tags = extractScriptVariable(document, "tags").orEmpty()
        val order = extractScriptVariable(document, "order") ?: defaultOrder
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

        return client.post(
            "$baseUrl/apps/controllers/book/bookList.php",
            apiHeaders,
            body,
        )
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

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
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

    private fun parseStatus(statusText: String?): Int = when (val status = statusText?.lowercase()) {
        null -> SManga.UNKNOWN
        else -> when {
            status.contains("ongoing") -> SManga.ONGOING
            status.contains("full") -> SManga.COMPLETED
            status.contains("completed") -> SManga.COMPLETED
            status.contains("hoàn") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val mangaPath = when (url.pathSegments.firstOrNull()) {
            "truyen-tranh" -> url.encodedPath
            "doc-truyen-tranh" -> {
                val mangaSlug = url.pathSegments.getOrNull(1) ?: return null
                "/truyen-tranh/$mangaSlug-0.html"
            }
            else -> return null
        }
        val manga = SManga.create().apply { setUrlWithoutDomain(mangaPath) }

        return fetchMangaUpdate(manga, emptyList(), true, false).manga.apply {
            initialized = true
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    // ============================== Chapters ==============================

    private fun parseChapterList(document: Document): List<SChapter> = document.select("div#TabChapterChapter div.chapter").mapNotNull { element ->
        val chapterUrl = chapterUrlRegex.find(element.attr("onclick"))
            ?.groupValues
            ?.get(1)
            ?: return@mapNotNull null

        SChapter.create().apply {
            setUrlWithoutDomain(chapterUrl)
            name = element.selectFirst("div.chapter-info div.name-sub")
                ?.text()
                ?: element.selectFirst("div.chapter-info div.name")!!.text()
            date_upload = parseChapterDate(element.selectFirst("div.chapter-info div.time > div")?.text())
            chapterNumberRegex.find(name)?.value?.toFloatOrNull()?.let { chapter_number = it }
        }
    }

    private fun parseChapterDate(date: String?): Long {
        if (date == null) return 0L
        return runCatching {
            LocalDateTime.parse(date, chapterDateFormat)
                .atZone(chapterDateZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val chapterUrl = response.request.url.toString()
        val document = response.asJsoup()
        val chapterId = extractScriptVariable(document, "chapterId") ?: return emptyList()
        val token = extractScriptVariable(document, "_token") ?: return emptyList()

        val apiHeaders = headers.newBuilder()
            .set("Origin", baseUrl)
            .set("Referer", chapterUrl)
            .set("X-Requested-With", "XMLHttpRequest")
            .build()
        val body = FormBody.Builder()
            .add("token", token)
            .add("chapterId", chapterId)
            .add("cookies", chapterCookies)
            .build()
        val payload = client.post(
            "$baseUrl/apps/controllers/book/bookChapterContent.php",
            apiHeaders,
            body,
        ).parseAs<ChapterContentApiResponse>()

        val chapterDocument = parsePayloadDocument(payload.status, payload.data)
            ?: return emptyList()

        return chapterDocument.select("img").mapIndexedNotNull { index, element ->
            val imageUrl = element.absUrl("data-src")
                .ifEmpty { element.absUrl("src") }
                .normalizeImageUrl()

            if (imageUrl.isEmpty()) {
                return@mapIndexedNotNull null
            }

            Page(index, url = chapterUrl, imageUrl = imageUrl)
        }.distinctBy { it.imageUrl }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement = client.get("$baseUrl$listPath").asJsoup()
        .select("#filterGenre .filter-item[data-slug]")
        .mapNotNull { element ->
            val id = element.attr("data-slug").takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            val name = element.text().takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null

            GenreOption(name, id)
        }
        .distinctBy { it.id }
        .toJsonElement()

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<List<GenreOption>>())

    // ============================== Related ===============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedSection = document.select("span.widget-main")
            .firstOrNull { it.text().equals("Truyện đề xuất cùng loại", ignoreCase = true) }
            ?.closest("div.widgets")
            ?.nextElementSibling()
            ?: return emptyList()

        return relatedSection.select("div.item-swiper").mapNotNull { element ->
            val titleElement = element.selectFirst("div.item-title a[href*=/truyen-tranh/]")
                ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(titleElement.absUrl("href"))
                title = titleElement.text()
                thumbnail_url = element.selectFirst("div.item-cover img")
                    ?.absUrl("src")
                    ?.normalizeImageUrl()
            }
        }.filterNot { it.url == manga.url }
            .distinctBy { it.url }
    }

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

    private val listPath = "/danh-sach-truyen-tranh.html"
    private val popularOrder = "viewsAll"
    private val defaultStatus = "0"
    private val defaultCategory = "all"
    private val defaultGenre = "0"
    private val defaultExplicit = "0"
    private val defaultOrder = "updated_at"
    private val chapterCookies = "W10="
    private val chapterUrlRegex = Regex("""openUrl\('([^']+)'\)""")
    private val chapterNumberRegex = Regex("""\d+(?:\.\d+)?""")
    private val chapterDateFormat = DateTimeFormatter.ofPattern("dd.MM.yyyy - HH:mm", Locale.ROOT)
    private val chapterDateZone = ZoneId.of("Asia/Ho_Chi_Minh")
}
