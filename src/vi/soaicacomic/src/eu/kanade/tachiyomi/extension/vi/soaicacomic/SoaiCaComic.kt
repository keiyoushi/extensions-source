package eu.kanade.tachiyomi.extension.vi.soaicacomic

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonElement
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Source
abstract class SoaiCaComic : KeiSource() {

    private val thumbnailFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val fallbackUrl = request.url.fragment
            ?.takeIf { it.startsWith(thumbnailFallbackFragmentPrefix) }
            ?.removePrefix(thumbnailFallbackFragmentPrefix)
            ?: return@Interceptor response

        if (response.code != 401 && response.code != 404) {
            return@Interceptor response
        }

        response.close()
        chain.proceed(GET(fallbackUrl, request.headers))
    }

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(thumbnailFallbackInterceptor)
        rateLimit(3)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/xem-nhieu-nhat/").asJsoup()
            .select("ul.most-views.single-list-comic li.position-relative")
            .mapNotNull(::archiveMangaFromElement)

        return MangasPage(mangas, hasNextPage = false)
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = if (page == 1) baseUrl else "$baseUrl/page/$page/"
        val document = client.get(url).asJsoup()
        val mangas = document.select(".col-md-3.col-xs-6.comic-item")
            .mapNotNull(::latestMangaFromElement)
        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun latestMangaFromElement(element: Element): SManga? {
        val linkElement = element.selectFirst(".comic-img a[href], .comic-title-link > a[href]") ?: return null
        if (!linkElement.absUrl("href").contains("/truyen-tranh/")) {
            return null
        }

        return SManga.create().apply {
            title = element.selectFirst("h3.comic-title")!!.text()
            setUrlWithoutDomain(linkElement.absUrl("href"))
            thumbnail_url = resolveSearchThumbnailUrl(
                element.selectFirst(".comic-img img, img.img-thumbnail")?.absUrl("src"),
            )
        }
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(
        page: Int,
        query: String,
        filters: FilterList,
    ): MangasPage {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("action", "searchtax")
                .add("keyword", query)
                .build()
            return parseSearchResponse(client.post("$baseUrl/wp-admin/admin-ajax.php", formBody))
        }

        val filterPath = selectedFilterPath(filters)
        if (filterPath != null) {
            val url = "$baseUrl/$filterPath/".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .build()
            return parseSearchResponse(client.get(url))
        }

        return getLatestUpdates(page)
    }

    private fun parseSearchResponse(response: Response): MangasPage {
        val contentType = response.header("Content-Type").orEmpty()
        if (contentType.contains("application/json")) {
            return parseSearchApiResponse(response)
        }

        val document = response.asJsoup()
        val archivePage = response.request.url.queryParameter("page")
            ?.toIntOrNull()

        if (archivePage != null) {
            return if (document.selectFirst("#archive-list-table") != null) {
                parseArchivePage(document, archivePage)
            } else {
                parseFilterFallbackPage(document)
            }
        }

        val mangas = document.select(".col-md-3.col-xs-6.comic-item")
            .mapNotNull(::latestMangaFromElement)
        val hasNextPage = document.selectFirst("ul.pager li.next:not(.disabled) a") != null

        return MangasPage(mangas, hasNextPage)
    }

    private fun parseSearchApiResponse(response: Response): MangasPage {
        val searchResponse = response.parseAs<SearchResponse>()

        val mangas = searchResponse.data
            .filter { it.link.contains("/truyen-tranh/") }
            .mapNotNull { result ->
                val path = result.link.toHttpUrlOrNull()?.encodedPath ?: return@mapNotNull null
                SManga.create().apply {
                    title = result.title
                    setUrlWithoutDomain(path)
                    thumbnail_url = resolveSearchThumbnailUrl(result.imageUrl())
                }
            }
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun parseArchivePage(document: Document, page: Int): MangasPage {
        val mangas = document.select("#archive-list-table > li.position-relative")
            .mapNotNull(::archiveMangaFromElement)
            .distinctBy { it.url }

        val fromIndex = ((page - 1).coerceAtLeast(0)) * 32
        if (fromIndex >= mangas.size) {
            return MangasPage(emptyList(), hasNextPage = false)
        }

        val pageItems = mangas.drop(fromIndex).take(32)
        val hasNextPage = mangas.size > fromIndex + pageItems.size

        return MangasPage(pageItems, hasNextPage)
    }

    private fun parseFilterFallbackPage(document: Document): MangasPage {
        val archiveItems = document.select("ul.most-views.single-list-comic li.position-relative")
            .mapNotNull(::archiveMangaFromElement)
        if (archiveItems.isNotEmpty()) {
            return MangasPage(archiveItems, hasNextPage = false)
        }

        val latestItems = document.select(".col-md-3.col-xs-6.comic-item")
            .mapNotNull(::latestMangaFromElement)
        return MangasPage(latestItems, hasNextPage = false)
    }

    private fun resolveSearchThumbnailUrl(url: String?): String? {
        if (url.isNullOrBlank() || !url.contains("-150x150")) return url

        val removed = url.replace("-150x150", "")
        val replaced = url.replace("-150x150", "-720x970")
        return removed.toHttpUrl().newBuilder()
            .fragment("$thumbnailFallbackFragmentPrefix$replaced")
            .build()
            .toString()
    }

    private fun archiveMangaFromElement(element: Element): SManga? {
        val linkElement = element.selectFirst("p.super-title a[href]") ?: return null
        val url = linkElement.absUrl("href")
        if (!url.contains("/truyen-tranh/")) {
            return null
        }

        return SManga.create().apply {
            title = linkElement.text()
            setUrlWithoutDomain(url)
            thumbnail_url = resolveSearchThumbnailUrl(
                element.selectFirst("img.list-left-img")?.absUrl("src"),
            )
        }
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val mangaPath = if (url.pathSegments.firstOrNull() == "truyen-tranh") {
            url.encodedPath
        } else {
            client.get(url).asJsoup()
                .selectFirst("#post-category-link, .credit-title a[href*=/truyen-tranh/]")
                ?.absUrl("href")
                ?.toHttpUrlOrNull()
                ?.encodedPath
        } ?: return null

        val manga = SManga.create().apply { setUrlWithoutDomain(mangaPath) }
        return fetchMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get("$baseUrl${manga.url}").asJsoup()
        return SMangaUpdate(
            manga = parseMangaDetails(document, manga),
            chapters = parseChapterList(document),
        )
    }

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        title = document.selectFirst("h2.info-title, .info-title")!!.text()
        thumbnail_url = document.selectFirst(".comic-intro img.img-thumbnail")?.absUrl("src")
        author = document.selectFirst("strong:contains(Tác giả) + span")?.text()
        status = document.selectFirst("span.comic-stt")?.text()
            ?.let(::parseStatus)
            ?: SManga.UNKNOWN
        genre = document.select(".comic-info .tags a[href*=/the-loai/]")
            .joinToString { it.text() }
            .ifEmpty { null }
        description = parseDescription(document)
    }

    private fun parseDescription(document: Document): String? {
        val block = document.selectFirst(".intro-container .hide-long-text")
            ?: document.selectFirst(".intro-container > p")
            ?: return null

        val description = block.text()
            .substringBefore("— Xem Thêm —")
            .substringBefore("- Xem thêm -")
            .removePrefix("\"")
            .removeSuffix("\"")

        return description.takeUnless {
            it.isEmpty() ||
                it.equals("Đang cập nhật", ignoreCase = true) ||
                it.equals("Đang cập nhật...", ignoreCase = true) ||
                it.equals("Không có", ignoreCase = true)
        }
    }

    private fun parseStatus(status: String): Int {
        val normalized = status.lowercase()
        return when {
            normalized.contains("đang tiến hành") -> SManga.ONGOING
            normalized.contains("hoàn thành") || normalized.contains("trọn bộ") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ==============================

    private fun parseChapterList(document: Document): List<SChapter> = document
        .select(".chapter-table table tbody tr")
        .mapNotNull(::chapterFromElement)

    private fun chapterFromElement(element: Element): SChapter? {
        val linkElement = element.selectFirst("a.text-capitalize[href]") ?: return null
        val fullText = linkElement.selectFirst("span.hidden-sm.hidden-xs")?.text() ?: linkElement.text()
        val chapterName = parseChapterName(fullText).takeIf { it.isNotEmpty() } ?: return null
        val isLocked = linkElement.selectFirst(".glyphicon-lock, .fa-lock, .icon-lock") != null ||
            element.selectFirst(".glyphicon-lock, .fa-lock, .icon-lock") != null

        return SChapter.create().apply {
            setUrlWithoutDomain(linkElement.absUrl("href"))
            name = if (isLocked) "🔒 $chapterName" else chapterName
            date_upload = element.selectFirst("td.hidden-xs.hidden-sm, td:last-child")
                ?.text()
                ?.let(::parseChapterDate)
                ?: 0L
        }
    }

    private fun parseChapterName(rawName: String): String {
        val match = chapterNameRegex.find(rawName)
        if (match != null) {
            return match.value
                .replace(chapterWordRegex, "CHAP")
                .replace(multiSpaceRegex, " ")
        }

        return rawName.substringAfterLast("–").substringAfterLast("-")
            .ifEmpty { rawName }
    }

    private fun parseChapterDate(dateText: String): Long = runCatching {
        LocalDate.parse(dateText, dateFormat)
            .atStartOfDay(dateZone)
            .toInstant()
            .toEpochMilli()
    }.getOrDefault(0L)

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val (html, chapterUrl) = client.get("$baseUrl${chapter.url}").use { response ->
            response.body.string() to response.request.url.toString()
        }

        if (passwordFieldRegex.containsMatchIn(html)) {
            throw Exception(passwordWebviewMessage)
        }

        val imageUrls = ImageDecryptor.extractImageUrls(html, chapterUrl)
        if (imageUrls.isEmpty()) {
            return emptyList()
        }

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData(): JsonElement {
        val document = client.get(baseUrl).asJsoup()
        return FilterData(
            genres = parseFilterOptions(document, "#nav-tags"),
            teams = parseFilterOptions(document, "#nav-teams"),
            series = parseFilterOptions(document, "#nav-series"),
            keywords = parseFilterOptions(document, "#nav-hashtags"),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<FilterData>())

    private fun parseFilterOptions(document: Document, selector: String): List<FilterOption> = document
        .select("$selector a[href]")
        .mapNotNull { link ->
            val name = link.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            val path = link.absUrl("href").toHttpUrlOrNull()?.encodedPath
                ?.trim('/')
                ?.takeIf { it.isNotEmpty() }
                ?: return@mapNotNull null
            FilterOption(name, path)
        }
        .distinctBy { it.path }

    private fun selectedFilterPath(filters: FilterList): String? = sequenceOf(
        filters.firstInstanceOrNull<GenreFilter>(),
        filters.firstInstanceOrNull<TeamFilter>(),
        filters.firstInstanceOrNull<SeriesFilter>(),
        filters.firstInstanceOrNull<KeywordFilter>(),
    ).mapNotNull { it?.toUriPart()?.ifEmpty { null } }
        .firstOrNull()

    private val dateFormat = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT)
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val chapterNameRegex = Regex("chap\\s*\\d+(?:\\.\\d+)?", RegexOption.IGNORE_CASE)
    private val chapterWordRegex = Regex("chap", RegexOption.IGNORE_CASE)
    private val multiSpaceRegex = Regex("\\s+")
    private val passwordFieldRegex = Regex("name=\\\"post_password\\\"|name=post_password")
    private val thumbnailFallbackFragmentPrefix = "fallback-url:"
    private val passwordWebviewMessage = "Vui lòng nhập mật khẩu của chương này qua webview"
}
