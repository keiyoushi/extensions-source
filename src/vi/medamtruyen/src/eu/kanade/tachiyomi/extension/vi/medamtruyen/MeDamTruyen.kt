package eu.kanade.tachiyomi.extension.vi.medamtruyen

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
import kotlinx.serialization.json.JsonElement
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

@Source
abstract class MeDamTruyen : KeiSource() {
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = apply {
        addInterceptor(thumbnailFallbackInterceptor)
        rateLimit(3)
    }

    private val thumbnailFallbackInterceptor = Interceptor { chain ->
        val request = chain.request()
        val response = chain.proceed(request)
        val fallbackUrl = thumbFallbackMap.remove(request.url.toString()) ?: return@Interceptor response

        val isBadCode = response.code == 401 || response.code == 404
        if (!isBadCode) {
            return@Interceptor response
        }

        response.close()

        val fallbackRequest = GET(fallbackUrl, request.headers)
        chain.proceed(fallbackRequest)
    }

    // ============================== Popular ===============================

    override suspend fun getPopularManga(page: Int): MangasPage {
        val document = client.get(baseUrl).asJsoup()
        val mangas = document.select("#day-charts .sidebar-comic-block")
            .mapNotNull(::popularMangaFromElement)
            .distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun popularMangaFromElement(element: Element): SManga? {
        val linkElement = element.selectFirst("a.sidebar-comic-block-link[href*=/truyen/]") ?: return null
        val titleElement = element.selectFirst("h3.sidebar-comic-block-title") ?: return null

        return SManga.create().apply {
            title = titleElement.text()
            setUrlWithoutDomain(linkElement.absUrl("href"))
            thumbnail_url = element.selectFirst("img.koi-img")?.absUrl("src")
        }
    }

    // ============================== Latest ================================

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/truyen-moi-cap-nhat/".toHttpUrl().newBuilder()
            .addQueryParameter("trang", page.toString())
            .build()

        return parseBrowsePage(client.get(url))
    }

    // ============================== Search ================================

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isNotBlank()) {
            val formBody = FormBody.Builder()
                .add("action", "searchtax")
                .add("keyword", query)
                .build()

            return parseSearchApiResponse(client.post("$baseUrl/wp-admin/admin-ajax.php", body = formBody))
        }

        val filterPath = filters.firstInstanceOrNull<GenreFilter>()?.toUriPart()
            ?: filters.firstInstanceOrNull<GroupFilter>()?.toUriPart()
            ?: filters.firstInstanceOrNull<AuthorFilter>()?.toUriPart()
        if (filterPath != null) {
            val url = baseUrl.toHttpUrl().newBuilder()
                .addEncodedPathSegments(filterPath.trim('/'))
                .addQueryParameter("trang", page.toString())
                .build()

            return parseBrowsePage(client.get(url))
        }

        return getLatestUpdates(page)
    }

    private fun parseSearchApiResponse(response: Response): MangasPage {
        val searchResponse = response.parseAs<SearchResponse>()

        val mangas = searchResponse.data.mapNotNull { result ->
            val mangaLink = result.link?.takeIf { truyenPathRegex.containsMatchIn(it) } ?: return@mapNotNull null
            val mangaTitle = result.title ?: return@mapNotNull null

            SManga.create().apply {
                title = mangaTitle
                setUrlWithoutDomain(mangaLink.removePrefix(baseUrl))
                thumbnail_url = resolveSearchThumbnailUrl(result.img)
            }
        }.distinctBy { it.url }

        return MangasPage(mangas, hasNextPage = false)
    }

    private fun parseBrowsePage(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.comic-list-item")
            .mapNotNull(::browseMangaFromElement)
            .distinctBy { it.url }
        val hasNextPage = document.select("a[href*='?trang=']").any { element ->
            element.text().contains("Sau", ignoreCase = true)
        }

        return MangasPage(mangas, hasNextPage)
    }

    private fun browseMangaFromElement(element: Element): SManga? {
        val linkElement = element.selectFirst("a.comic-block-link[href*=/truyen/]") ?: return null
        val titleElement = element.selectFirst("h3.comic-block-title") ?: return null

        return SManga.create().apply {
            title = titleElement.text()
            setUrlWithoutDomain(linkElement.absUrl("href"))
            thumbnail_url = element.selectFirst("div.comic-block-img img")?.absUrl("src")
        }
    }

    private fun resolveSearchThumbnailUrl(url: String?): String? {
        if (url.isNullOrBlank() || !url.contains(thumbLowSize)) {
            return url
        }

        val removed = url.replace(thumbLowSize, "")
        val replaced = url.replace(thumbLowSize, thumbHighSize)
        thumbFallbackMap[removed] = replaced
        return removed
    }

    // ============================== Details ===============================

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null

        val pathSegments = url.pathSegments.filter { it.isNotEmpty() }
        val isChapterUrl = pathSegments.size == 1 &&
            "-chap-" in pathSegments.single().lowercase(Locale.ROOT)
        val detailUrl = if (pathSegments.firstOrNull() == "truyen") {
            url
        } else if (isChapterUrl) {
            client.get(url).asJsoup()
                .selectFirst("a[href*=/truyen/]:matchesOwn(^DS\\. Chương$), a[href*=/truyen/]")
                ?.absUrl("href")
                ?.toHttpUrl()
                ?: return null
        } else {
            return null
        }

        if (detailUrl.pathSegments.firstOrNull() != "truyen" || detailUrl.pathSegments.getOrNull(1).isNullOrEmpty()) {
            return null
        }

        val manga = SManga.create().apply {
            setUrlWithoutDomain(detailUrl.encodedPath)
        }
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

    private fun parseMangaDetails(document: Document, manga: SManga): SManga = SManga.create().apply {
        setUrlWithoutDomain(manga.url)
        val infoRows = document.select("div.comic-desc-list ul.list-unstyled li")

        title = document.selectFirst("h2.comic-title")!!.text()
        thumbnail_url = document.selectFirst("div.comic-desc-list meta[itemprop=image]")
            ?.absUrl("content")
            ?.takeIf { it.isNotEmpty() }
            ?: document.selectFirst("div.comic-info-img img, div.chapter-item-img img")?.absUrl("src")
        author = extractInfoValue(infoRows, "Tác giả")
        genre = document.select("div.comic-desc-list a[href*=/the-loai/]")
            .joinToString { it.text() }
            .ifEmpty { null }
        status = parseStatus(document.selectFirst("span.comic-stt")?.text())
        description = parseDescription(document)
    }

    private fun extractInfoValue(infoRows: List<Element>, label: String): String? = infoRows.firstOrNull { row ->
        row.selectFirst("strong")?.text()?.contains(label, ignoreCase = true) == true
    }?.ownText()?.takeIf { it.isNotEmpty() }

    private fun parseStatus(statusText: String?): Int {
        val status = statusText?.lowercase(Locale.ROOT) ?: return SManga.UNKNOWN
        return when {
            "đang tiến hành" in status -> SManga.ONGOING
            "trọn bộ" in status -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun parseDescription(document: Document): String? {
        val firstParagraph = document.selectFirst("div.hide-long-text p")
            ?.text()
            ?.substringBefore("— Xem Thêm —")
        if (!firstParagraph.isNullOrBlank()) {
            return firstParagraph
        }

        val fullText = document.selectFirst("div.hide-long-text")
            ?.text()
            ?.substringBefore("— Xem Thêm —")
        return fullText?.takeIf { it.isNotBlank() }
    }

    // ============================== Chapters ==============================

    private fun parseChapterList(document: Document): List<SChapter> = document.select("div.chapter-list div.chapter-item")
        .mapNotNull(::chapterFromElement)
        .distinctBy { it.url }

    private fun chapterFromElement(element: Element): SChapter? {
        val linkElement = element.selectFirst("a.chapter-link[href*=-chap-]") ?: return null
        val rawChapterName = element.selectFirst("p.chapter-title")?.text() ?: linkElement.text()

        return SChapter.create().apply {
            setUrlWithoutDomain(linkElement.absUrl("href"))
            name = normalizeChapterName(rawChapterName)
            date_upload = element.selectFirst("p.chapter-meta")
                ?.text()
                ?.let(::parseChapterDate)
                ?: 0L

            chapterNumberRegex.find(name)?.value?.replace(",", ".")?.toFloatOrNull()?.let {
                chapter_number = it
            }
        }
    }

    private fun normalizeChapterName(rawChapterName: String): String {
        val chapterMatch = chapterNameRegex.find(rawChapterName) ?: return rawChapterName
        return chapterMatch.value
            .replace(chapterWordRegex, "Chap")
            .replace(multiSpaceRegex, " ")
    }

    private fun parseChapterDate(chapterMeta: String): Long {
        val dateString = chapterDateRegex.find(chapterMeta)?.value ?: return 0L
        return runCatching {
            LocalDate.parse(dateString, chapterDateFormat)
                .atStartOfDay(dateZone)
                .toInstant()
                .toEpochMilli()
        }.getOrDefault(0L)
    }

    // ============================== Pages =================================

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val response = client.get(getChapterUrl(chapter))
        val chapterUrl = response.request.url.toString()
        val html = response.use { it.body.string() }
        if (passwordFormRegex.containsMatchIn(html)) {
            throw Exception(passwordWebviewMessage)
        }

        val imageUrls = ImageDecryptor.extractImageUrls(html, chapterUrl)
        if (imageUrls.isEmpty()) return emptyList()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, url = chapterUrl, imageUrl = imageUrl)
        }
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .set("Referer", page.url)
            .build()

        return GET(page.imageUrl!!, imageHeaders)
    }

    // ============================== Filters ===============================

    override val supportsFilterFetching get() = true

    override suspend fun fetchFilterData() = client.get(baseUrl).asJsoup().let { document ->
        FilterData(
            genres = parseFilterOptions(document, "#genre-tab"),
            groups = parseFilterOptions(document, "#team-tab"),
            authors = parseFilterOptions(document, "#artist-tab"),
        ).toJsonElement()
    }

    override fun getFilterList(data: JsonElement?): FilterList = getFilters(data?.parseAs<FilterData>())

    private fun parseFilterOptions(document: Document, selector: String): List<FilterOption> = document.select("$selector a[href]").mapNotNull { element ->
        val name = element.text().takeIf { it.isNotEmpty() } ?: return@mapNotNull null
        val path = element.absUrl("href").toHttpUrl().encodedPath
        FilterOption(name, path)
    }.distinctBy { it.path }

    // =============================== Related ==============================

    override val supportsRelatedMangas get() = true

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> {
        val document = client.get(getMangaUrl(manga)).asJsoup()
        val relatedList = document.select("h3")
            .firstOrNull { it.text().equals("Truyện Liên Quan", ignoreCase = true) }
            ?.parent()
            ?.nextElementSibling()
            ?: return emptyList()

        return relatedList.select("div.comic-block").mapNotNull { element ->
            val link = element.selectFirst("a.comic-block-link[href*=/truyen/]") ?: return@mapNotNull null
            val title = element.selectFirst("h3.comic-block-title")?.text() ?: return@mapNotNull null

            SManga.create().apply {
                setUrlWithoutDomain(link.absUrl("href"))
                this.title = title
                thumbnail_url = element.selectFirst("img.koi-img")?.absUrl("src")
            }
        }.distinctBy { it.url }
    }

    private val thumbLowSize = "-150x150"
    private val thumbHighSize = "-720x970"
    private val passwordWebviewMessage = "Vui lòng nhập mật khẩu của chương này qua webview"

    private val truyenPathRegex = Regex("""/truyen/""", RegexOption.IGNORE_CASE)
    private val chapterNameRegex = Regex(
        """chap\s*\d+(?:[.,]\d+)?(?:\s*:\s*.+)?""",
        RegexOption.IGNORE_CASE,
    )
    private val chapterWordRegex = Regex("""chap""", RegexOption.IGNORE_CASE)
    private val chapterNumberRegex = Regex("""\d+(?:[.,]\d+)?""")
    private val chapterDateRegex = Regex("""\d{2}/\d{2}/\d{2}""")
    private val multiSpaceRegex = Regex("""\s+""")
    private val passwordFormRegex = Regex(
        """post-password-form|name=['"]post_password['"]""",
        RegexOption.IGNORE_CASE,
    )
    private val thumbFallbackMap = ConcurrentHashMap<String, String>()
    private val dateZone = ZoneId.of("Asia/Ho_Chi_Minh")
    private val chapterDateFormat = DateTimeFormatter.ofPattern("dd/MM/yy", Locale.ROOT)
}
