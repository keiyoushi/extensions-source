package eu.kanade.tachiyomi.extension.vi.moetruyen

import android.content.SharedPreferences
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap

class MoeTruyen :
    HttpSource(),
    ConfigurableSource {

    override val name = "MoeTruyen"
    override val lang = "vi"
    override val baseUrl get() = getPrefBaseUrl()
    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    private val apiUrl = "https://moe.suicaodex.com/v2"

    private val imgxGrants = ConcurrentHashMap<String, PageAccessEntry>()

    override val client = network.client.newBuilder()
        .rateLimit(3)
        .addInterceptor(CookieInterceptor("moetruyen.net", WEB_UNLOCK_COOKIE))
        .addInterceptor(CookieInterceptor("truyen.moe", WEB_UNLOCK_COOKIE))
        .addInterceptor(imgxInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)
        .add("Accept", "application/json, text/plain, */*")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        if (!useApiPref()) {
            return GET(baseUrl, headers)
        }

        val url = "$apiUrl/manga/top".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("include", "")
            .addQueryParameter("sort_by", "views")
            .addQueryParameter("time", "all_time")
            .build()

        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = if (useApiPref()) {
        parseApiMangaList(response.parseAs<ApiListResponse<MangaDto>>())
    } else {
        val mangas = response.asJsoup()
            .select("ol.homepage-ranking-list[data-ranking-period=total] a.homepage-ranking-item__link")
            .map(::popularMangaFromElement)

        MangasPage(mangas, false)
    }

    private fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.absUrl("href"))
        val titleElement = element.selectFirst(".homepage-ranking-item__title")!!
        val titleAttr = titleElement.attr("title")
        title = titleAttr.ifEmpty { titleElement.text() }
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // ============================== Latest ================================

    override fun latestUpdatesRequest(page: Int): Request {
        if (!useApiPref()) {
            val url = "$baseUrl/manga".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .build()

            return GET(url, headers)
        }

        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .addQueryParameter("sort", "updated_at")
            .build()

        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = if (useApiPref()) {
        parseApiMangaList(response.parseAs<ApiListResponse<MangaDto>>())
    } else {
        parseHtmlMangaList(response.asJsoup())
    }

    private fun latestMangaFromElement(element: Element): SManga = SManga.create().apply {
        val linkElement = element.selectFirst("a[href^=/manga/]")!!
        setUrlWithoutDomain(linkElement.absUrl("href"))
        title = getFullListTitle(element)
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    private fun getFullListTitle(element: Element): String {
        val titleElement = element.selectFirst("h3")!!
        val titleAttr = titleElement.attr("title")
        if (titleAttr.isNotEmpty()) {
            return titleAttr
        }

        val titleText = titleElement.text()
        if (!titleText.endsWith("...")) {
            return titleText
        }

        val imageAlt = element.selectFirst("img")?.attr("alt")
            ?.removePrefix("Bìa ")
            ?.trim()
            ?.ifEmpty { null }

        return imageAlt ?: titleText
    }

    private fun parseHtmlMangaList(document: Document): MangasPage {
        val mangas = document.select("article.manga-card--list")
            .map(::latestMangaFromElement)

        val hasNextPage = document
            .selectFirst("nav[aria-label='Phân trang truyện'] a[aria-label='Trang sau']:not(.is-disabled)")
            ?.attr("href")
            ?.let { it != "#" }
            ?: false

        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Search ================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (!useApiPref()) {
            val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()
            val includedGenres = filters.firstInstanceOrNull<GenreFilter>()
                ?.state
                ?.filter { it.state }
                .orEmpty()
            val hasFilter = status != null || includedGenres.isNotEmpty()

            if (query.isBlank() && !hasFilter) {
                return latestUpdatesRequest(page)
            }

            val url = "$baseUrl/manga".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .apply {
                    if (query.isNotBlank()) {
                        addQueryParameter("q", query)
                    }

                    status?.let { addQueryParameter("status", it) }
                    includedGenres.forEach { addQueryParameter("include", it.id) }
                }
                .build()

            return GET(url, headers)
        }

        extractMangaIdFromQuery(query)?.let { mangaId ->
            return GET("$apiUrl/manga/$mangaId?include=genres", headers)
        }

        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart()?.toApiStatus()
        val includedGenres = filters.firstInstanceOrNull<GenreFilter>()
            ?.state
            ?.filter { it.state }
            .orEmpty()

        val hasFilter = status != null || includedGenres.isNotEmpty()

        if (query.isBlank() && !hasFilter) {
            return latestUpdatesRequest(page)
        }

        val url = "$apiUrl/manga".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())
            .addQueryParameter("limit", PAGE_SIZE.toString())
            .apply {
                if (query.isNotBlank()) {
                    addQueryParameter("q", query)
                }

                status?.let { addQueryParameter("status", it) }
                if (includedGenres.isNotEmpty()) {
                    addQueryParameter("genre", includedGenres.joinToString(",") { it.id })
                }
            }
            .build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (!useApiPref()) {
            return parseHtmlMangaList(response.asJsoup())
        }

        val encodedPath = response.request.url.encodedPath
        return if (MANGA_DETAILS_PATH_REGEX.matches(encodedPath)) {
            val manga = response.parseAs<ApiResponse<MangaDto>>().data.toSManga()
            MangasPage(listOf(manga), false)
        } else {
            parseApiMangaList(response.parseAs<ApiListResponse<MangaDto>>())
        }
    }

    private fun parseApiMangaList(apiResponse: ApiListResponse<MangaDto>): MangasPage {
        val mangas = apiResponse.data.map { it.toSManga() }
        val hasNextPage = apiResponse.meta?.pagination?.hasNextPage() ?: false
        return MangasPage(mangas, hasNextPage)
    }

    // ============================== Filters ===============================

    override fun getFilterList(): FilterList = getFilters()

    // ============================== Details ===============================

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!useApiPref() && !manga.url.isApiMangaUrl()) {
            return GET("$baseUrl${manga.url.substringBefore('#')}", headers)
        }

        val mangaId = manga.url.extractMangaId()
            ?: throw Exception("Không thể xác định id truyện từ URL: ${manga.url}")

        return GET("$apiUrl/manga/$mangaId?include=genres", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val isApiResponse = response.request.url.encodedPath.startsWith("/v2/manga/")
        return if (isApiResponse) {
            response.parseAs<ApiResponse<MangaDto>>().data.toSManga()
        } else {
            val document = response.asJsoup()

            SManga.create().apply {
                title = document.selectFirst("h1.manga-detail-title")!!.text()
                author = document.select("p.manga-detail-meta-line")
                    .firstOrNull { line ->
                        line.selectFirst(".manga-detail-meta-label")
                            ?.text()
                            ?.contains("Tác giả")
                            ?: false
                    }
                    ?.select("a.inline-link")
                    ?.joinToString { it.text() }
                    ?.ifEmpty { null }
                genre = document.select(".manga-detail-genre-chips a.chip")
                    .joinToString { it.text() }
                    .ifEmpty { null }
                description = document.selectFirst("[data-description-content]")
                    ?.text()
                    ?.ifEmpty { null }
                    ?: document.selectFirst(".manga-description__text")
                        ?.text()
                        ?.ifEmpty { null }
                status = parseStatus(document.selectFirst(".manga-status-pill")?.text())
                thumbnail_url = document.selectFirst(".detail-cover img")?.absUrl("src")
            }
        }
    }

    // ============================== Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        if (!useApiPref() && !manga.url.isApiMangaUrl()) {
            client.newCall(chapterListRequest(manga)).execute().use { response ->
                return@fromCallable chapterListParsePaginated(response)
            }
        }

        val mangaId = manga.url.extractMangaId()
            ?: throw Exception("Không thể xác định id truyện từ URL: ${manga.url}")

        val chapters = mutableListOf<SChapter>()
        var page = 1
        var hasNextPage: Boolean

        do {
            val url = "$apiUrl/manga/$mangaId/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("page", page.toString())
                .addQueryParameter("limit", CHAPTER_PAGE_SIZE.toString())
                .build()

            val response = client.newCall(GET(url, headers)).execute()
            response.use {
                val apiResponse = it.parseAs<ApiResponse<ChapterListDataDto>>()
                chapters += apiResponse.data.chapters.map { chapter -> chapter.toSChapter(mangaId) }

                hasNextPage = apiResponse.meta?.pagination?.hasNextPage() ?: false
                page++
            }
        } while (hasNextPage)

        chapters
    }

    override fun chapterListRequest(manga: SManga): Request {
        val path = manga.url.substringBefore('#')
        return if (!useApiPref() && !manga.url.isApiMangaUrl()) {
            GET("$baseUrl$path", headers)
        } else {
            val mangaId = manga.url.extractMangaId()
                ?: throw Exception("Không thể xác định id truyện từ URL: ${manga.url}")
            val url = "$apiUrl/manga/$mangaId/chapters".toHttpUrl().newBuilder()
                .addQueryParameter("page", "1")
                .addQueryParameter("limit", CHAPTER_PAGE_SIZE.toString())
                .build()
            GET(url, headers)
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val isApiResponse = response.request.url.encodedPath.startsWith("/v2/manga/")
        return if (isApiResponse) {
            val mangaId = response.request.url.encodedPath.substringAfter("/v2/manga/").substringBefore('/').toLongOrNull()
                ?: throw Exception("Không thể xác định id truyện từ request: ${response.request.url}")
            val apiResponse = response.parseAs<ApiResponse<ChapterListDataDto>>()
            apiResponse.data.chapters.map { it.toSChapter(mangaId) }
        } else {
            parseChapterList(response.asJsoup())
        }
    }

    private fun chapterListParsePaginated(response: Response): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        val visitedPages = mutableSetOf<String>()
        var currentPageUrl = response.request.url.toString()
        var currentDocument = response.asJsoup()

        while (visitedPages.add(currentPageUrl)) {
            chapters += parseChapterList(currentDocument)

            val nextChapterLinkElement: Element? = currentDocument.selectFirst(
                "nav[aria-label*='Phân trang chương'] a[aria-label='Trang chương sau']:not(.is-disabled)",
            )
            val nextChapterPageUrl: String? = nextChapterLinkElement?.let { link ->
                if (link.attr("href") == "#") {
                    null
                } else {
                    link.absUrl("href").ifEmpty { null }
                }
            }

            if (nextChapterPageUrl == null || visitedPages.contains(nextChapterPageUrl)) {
                break
            }

            currentPageUrl = nextChapterPageUrl
            client.newCall(GET(currentPageUrl, headers)).execute().use {
                currentDocument = it.asJsoup()
            }
        }

        return chapters
    }

    private fun parseChapterList(document: Document): List<SChapter> = document.select("ul.chapter-list li.chapter a.chapter-link").map { element ->
        SChapter.create().apply {
            setUrlWithoutDomain(element.absUrl("href"))
            name = element.selectFirst(".chapter-num")!!.text()

            val chapterTime = element.selectFirst(".chapter-time")
            val relativeDate = chapterTime?.text()
            val absoluteDate = chapterTime?.attr("title")
                ?.substringAfter("Cập nhật", missingDelimiterValue = "")
                ?.trim()
                ?.ifEmpty { null }

            date_upload = parseRelativeDate(relativeDate).takeIf { it != 0L }
                ?: htmlDateFormat.tryParse(absoluteDate)
        }
    }

    private fun parseRelativeDate(dateStr: String?): Long {
        if (dateStr.isNullOrBlank()) return 0L

        val calendar = Calendar.getInstance()
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

    override fun pageListRequest(chapter: SChapter): Request {
        if (!useApiPref() && !chapter.url.isApiChapterUrl()) {
            return GET("$baseUrl${chapter.url.substringBefore('#')}", headers)
        }

        val chapterId = chapter.url.extractChapterId()
            ?: throw Exception("Không thể xác định id chapter từ URL: ${chapter.url}")

        return GET("$apiUrl/chapters/$chapterId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val isApiResponse = response.request.url.encodedPath.startsWith("/v2/chapters/")
        if (!isApiResponse) {
            return parseHtmlPageList(response)
        }

        val apiResponse = response.parseAs<ApiResponse<ChapterPagesDataDto>>()
        val data = apiResponse.data
        val slug = data.manga?.slug
        val chapterNumber = data.chapter?.number

        if (slug != null && chapterNumber != null) {
            val accessUrl = "$baseUrl/manga/$slug/chapters/$chapterNumber/page-access"
            val pageCount = data.pageUrls.size
            return fetchPagesWithGrants(accessUrl, pageCount)
        }

        return data.pageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun parseHtmlPageList(response: Response): List<Page> {
        val document = response.asJsoup()
        val images = document.select("img.page-media")
            .filterNot { element ->
                element.parents().any { parent -> parent.tagName().equals("noscript", ignoreCase = true) }
            }

        val accessUrl = images.firstOrNull()?.attr("data-imgx-access-url")?.ifBlank { null }

        if (accessUrl != null) {
            val fullAccessUrl = if (accessUrl.startsWith("http")) accessUrl else "$baseUrl$accessUrl"
            return fetchPagesWithGrants(fullAccessUrl, images.size)
        }

        val imageUrls = images
            .asSequence()
            .map { element ->
                element.absUrl("data-src").ifEmpty { element.absUrl("src") }
            }
            .filter { imageUrl ->
                imageUrl.isNotBlank() && !imageUrl.startsWith("data:")
            }
            .distinct()
            .toList()

        return imageUrls.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun fetchPagesWithGrants(accessUrl: String, pageCount: Int): List<Page> {
        val pages = mutableListOf<Page>()
        val batchSize = IMGX_MAX_WINDOW

        for (start in 0 until pageCount step batchSize) {
            val end = minOf(start + batchSize, pageCount)
            val indices = (start until end).toList()
            val body = PageAccessRequest(pageIndexes = indices).toJsonRequestBody()

            val request = Request.Builder()
                .url(accessUrl)
                .post(body)
                .headers(headers)
                .build()

            val response = client.newCall(request).execute()
            val pageAccess = response.use { it.parseAs<PageAccessResponse>() }

            for (entry in pageAccess.pages) {
                if (entry.downloadUrl.isNotBlank() && entry.grant != null) {
                    imgxGrants[entry.downloadUrl] = entry
                    pages.add(Page(entry.pageIndex, imageUrl = entry.downloadUrl))
                }
            }
        }

        return pages.sortedBy { it.index }
    }

    private fun imgxInterceptor() = Interceptor { chain ->
        val request = chain.request()
        val url = request.url.toString()
        val entry = imgxGrants.remove(url)

        if (entry?.grant == null) {
            return@Interceptor chain.proceed(request)
        }

        val response = chain.proceed(request)
        val imgxData = response.body.bytes()

        if (imgxData.size <= 13 ||
            imgxData[0] != 0x49.toByte() || imgxData[1] != 0x4D.toByte() ||
            imgxData[2] != 0x47.toByte() || imgxData[3] != 0x58.toByte()
        ) {
            return@Interceptor response.newBuilder()
                .body(imgxData.toResponseBody(response.body.contentType()))
                .build()
        }

        val webp = ImgxDecoder.decrypt(imgxData, entry.grant, entry.storageKey)

        response.newBuilder()
            .body(webp.toResponseBody(WEBP_MEDIA_TYPE))
            .build()
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun getMangaUrl(manga: SManga): String {
        val path = manga.url.substringBefore('#')
        return if (path.startsWith(API_MANGA_PREFIX)) {
            val mangaId = path.removePrefix(API_MANGA_PREFIX).substringBefore('/')
            "$baseUrl/manga/$mangaId"
        } else {
            "$baseUrl$path"
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val path = chapter.url.substringBefore('#')
        return if (path.startsWith(API_MANGA_PREFIX)) {
            val chapterId = path.removePrefix(API_MANGA_PREFIX).substringAfter('/', "")
            "$baseUrl/chapter/$chapterId"
        } else {
            "$baseUrl$path"
        }
    }

    // ============================== Helpers =============================

    private fun MangaDto.toSManga(): SManga = SManga.create().apply {
        url = "$API_MANGA_PREFIX$id"
        title = this@toSManga.title
        thumbnail_url = coverUrl
        author = this@toSManga.author
        genre = genres?.joinToString { it.name }?.ifBlank { null }
        description = buildString {
            this@toSManga.description?.takeIf { it.isNotBlank() }?.let { append(it) }
            if (!this@toSManga.altTitles.isNullOrEmpty()) {
                if (isNotEmpty()) {
                    append("\n\n")
                }
                append("Tên khác: ")
                append(this@toSManga.altTitles.joinToString())
            }
        }.ifBlank { null }
        status = parseStatus(this@toSManga.status)
    }

    private fun parseStatus(status: String?): Int = when (status?.trim()?.lowercase(Locale.ROOT)) {
        "còn tiếp", "ongoing" -> SManga.ONGOING
        "hoàn thành", "completed" -> SManga.COMPLETED
        "tạm dừng", "hiatus" -> SManga.ON_HIATUS
        "cancelled" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private fun String.toApiStatus(): String? = when (this) {
        "Còn tiếp" -> "ongoing"
        "Hoàn thành" -> "completed"
        "Tạm dừng" -> "hiatus"
        else -> null
    }

    private fun ChapterDto.toSChapter(mangaId: Long): SChapter = SChapter.create().apply {
        url = "$API_MANGA_PREFIX$mangaId/$id"

        val chapterLabel = numberText
            ?.removeSuffix(".000")
            ?.ifBlank { null }
            ?: number?.toString()?.removeSuffix(".0")
            ?: "?"

        name = buildString {
            append("Chương ")
            append(chapterLabel)
            title?.takeIf { it.isNotBlank() }?.let {
                append(" - ")
                append(it)
            }
        }

        chapter_number = number?.toFloat() ?: numberText?.toFloatOrNull() ?: 0f
        date_upload = parseChapterDate(date)
        scanlator = groupName?.ifBlank { null }
    }

    private fun parseChapterDate(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0L
        return chapterDateFormat.tryParse(raw)
            ?: chapterDateFormatWithoutMillis.tryParse(raw)
            ?: 0L
    }

    private fun extractMangaIdFromQuery(query: String): Long? {
        if (!query.startsWith("http", ignoreCase = true)) return null

        val parsed = query.toHttpUrlOrNull() ?: return null
        val currentHost = baseUrl.toHttpUrl().host
        if (parsed.host != currentHost) return null

        val slug = parsed.pathSegments
            .dropWhile { it != "manga" }
            .drop(1)
            .firstOrNull()
            ?: return null

        return slug.substringBefore('-').toLongOrNull()
    }

    private fun String.extractMangaId(): Long? {
        val normalized = substringBefore('#')
        if (normalized.startsWith(API_MANGA_PREFIX)) {
            return normalized.removePrefix(API_MANGA_PREFIX).substringBefore('/').toLongOrNull()
        }

        val fragmentId = substringAfter('#', "").toLongOrNull()
        if (fragmentId != null) return fragmentId

        val slug = normalized
            .substringAfter("/manga/", "")
            .substringBefore('/')

        return slug.substringBefore('-').toLongOrNull()
    }

    private fun String.extractChapterId(): Long? {
        val path = substringBefore('#')
        if (path.startsWith(API_MANGA_PREFIX)) {
            return path.removePrefix(API_MANGA_PREFIX).substringAfter('/', "").toLongOrNull()
        }
        if (path.startsWith("/chapter/")) {
            return path.removePrefix("/chapter/").substringBefore('/').toLongOrNull()
        }
        return substringAfter('#', "").toLongOrNull()
    }

    private fun String.isApiMangaUrl(): Boolean = substringBefore('#').startsWith(API_MANGA_PREFIX)

    private fun String.isApiChapterUrl(): Boolean = substringBefore('#').startsWith(API_MANGA_PREFIX)

    // ============================== Preferences ===========================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_USE_API
            title = "Sử dụng API"
            summary = "Bật để dùng api bên thứ ba"
            setDefaultValue(false)
        }.let(screen::addPreference)

        val customUrlPref = EditTextPreference(screen.context).apply {
            key = PREF_CUSTOM_DOMAIN
            title = "Tên miền tùy chỉnh"
            summary = "Nhập tên miền bạn muốn sử dụng (ví dụ: https://moetruyen.xyz)"
            setEnabled(preferences.getPrefUrl() == UrlMode.CUSTOM)
            dialogTitle = "Tên miền tùy chỉnh"
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val inputUrl = newValue as String
                    if (inputUrl.isNotBlank()) {
                        inputUrl.toHttpUrl()
                    }
                    Toast.makeText(screen.context, NOTIFICATION_SHOW, Toast.LENGTH_SHORT).show()
                    true
                } catch (e: Exception) {
                    Toast.makeText(screen.context, "Tên miền không hợp lệ: Error: ${e.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        }

        ListPreference(screen.context).apply {
            key = PREF_DOMAIN
            title = "Tên miền chính"
            entries = LIST_DOMAIN_ENTRIES
            entryValues = LIST_DOMAIN_VALUES
            summary = "%s"
            setDefaultValue("default")
            setOnPreferenceChangeListener { _, newValue ->
                val index = entryValues.indexOf(newValue as String)
                summary = entries[index]
                customUrlPref.setEnabled(newValue == "custom")
                true
            }
        }.let(screen::addPreference)
        customUrlPref.let(screen::addPreference)
    }

    private fun useApiPref(): Boolean = preferences.getBoolean(PREF_USE_API, false)

    private fun getPrefBaseUrl(): String = when (preferences.getPrefUrl()) {
        UrlMode.DEFAULT -> DEFAULT_DOMAIN
        UrlMode.GLOBAL -> DOMAIN_GLOBAL
        UrlMode.CUSTOM -> preferences.getString(PREF_CUSTOM_DOMAIN, DEFAULT_DOMAIN)!!
    }.removeSuffix("/")

    enum class UrlMode {
        DEFAULT,
        GLOBAL,
        CUSTOM,
    }

    private fun SharedPreferences.getPrefUrl(): UrlMode = when (getString(PREF_DOMAIN, "default")) {
        "default" -> UrlMode.DEFAULT
        "global" -> UrlMode.GLOBAL
        else -> UrlMode.CUSTOM
    }

    companion object {
        private const val PAGE_SIZE = 24
        private const val CHAPTER_PAGE_SIZE = 100
        private const val IMGX_MAX_WINDOW = 5
        private const val API_MANGA_PREFIX = "/g/"
        private val WEB_UNLOCK_COOKIE = "moetruyen_full_web" to "Moetruyen123456"
        private val WEBP_MEDIA_TYPE = "image/webp".toMediaType()

        private const val PREF_USE_API = "pref_use_api"
        private const val PREF_DOMAIN = "pref_domain"
        private const val DEFAULT_DOMAIN = "https://moetruyen.net"
        private const val DOMAIN_GLOBAL = "https://truyen.moe"
        private val LIST_DOMAIN_ENTRIES = arrayOf(
            "MoeTruyen.net (Trong nước)",
            "Truyen.moe (Quốc tế)",
            "Tùy chỉnh",
        )
        private val LIST_DOMAIN_VALUES = arrayOf(
            "default",
            "global",
            "custom",
        )
        private const val PREF_CUSTOM_DOMAIN = "pref_custom_domain"
        private const val NOTIFICATION_SHOW = "Tên miền đã được thay đổi."

        private val MANGA_DETAILS_PATH_REGEX = Regex("""/v2/manga/\d+""")
        private val NUMBER_REGEX = Regex("""\d+""")

        private val chapterDateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private val chapterDateFormatWithoutMillis = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        private val htmlDateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh")
        }
    }
}
