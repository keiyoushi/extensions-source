package eu.kanade.tachiyomi.extension.en.asurascans

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread

class AsuraScans :
    ParsedHttpSource(),
    ConfigurableSource {

    override val name = "Asura Scans"

    override val baseUrl = "https://asurascans.com"

    private val apiUrl = "https://gg.asuracomic.net/api"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMMM d yyyy", Locale.US)

    private val preferences: SharedPreferences = getPreferences()

    private val slugMap: MutableMap<String, String> by lazy {
        loadSlugMap()
    }

    init {
        // remove legacy preferences
        preferences.run {
            if (contains("pref_url_map")) {
                edit().remove("pref_url_map").apply()
            }
            if (contains("pref_base_url_host")) {
                edit().remove("pref_base_url_host").apply()
            }
            if (contains("pref_permanent_manga_url_2_en")) {
                edit().remove("pref_permanent_manga_url_2_en").apply()
            }
            if (contains("pref_slug_map")) {
                edit().remove("pref_slug_map").apply()
            }
        }
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::forceHighQualityInterceptor)
        .rateLimit(2, 2)
        .build()

    // separate client for API calls with minimal rate limiting
    private val apiClient = network.cloudflareClient.newBuilder()
        .rateLimit(10, 1)
        .build()

    private var failedHighQuality = false

    private fun forceHighQualityInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        if (preferences.forceHighQuality() && !failedHighQuality && request.url.fragment == "pageListParse") {
            OPTIMIZED_IMAGE_PATH_REGEX.find(request.url.encodedPath)?.also { match ->
                val (id, page) = match.destructured
                val newUrl = request.url.newBuilder()
                    .encodedPath("/storage/media/$id/$page.webp")
                    .build()

                val response = chain.proceed(request.newBuilder().url(newUrl).build())
                if (response.code != 404) {
                    return response
                } else {
                    failedHighQuality = true
                    response.close()
                }
            }
        }

        return chain.proceed(request)
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = browseRequest(page, sort = "popular")

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaParse(response: Response): MangasPage = parseMangaPage(response)

    override fun latestUpdatesRequest(page: Int): Request = if (page == 1) {
        GET(baseUrl, headers)
    } else {
        browseRequest(page, order = "latest")
    }

    override fun latestUpdatesSelector() = "div.grid.grid-rows-1.grid-cols-1 > div.w-full"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst(COMIC_LINK_SELECTOR)!!
        setUrlWithoutDomain(link.attr("abs:href").toPermSlugIfNeeded())
        title = link.text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        return if (response.request.url.encodedPath == "/") {
            // The homepage "Latest Updates" block does not follow the normal browse listing structure.
            parseHomepageSectionMangas(
                document = document,
                sectionTitle = "Latest Updates",
                stopTitles = setOf("Popular", "Announcements"),
                hasNextPage = true,
            )
        } else {
            parseMangaPage(document, hasNextPage = true)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter(Genre::state)
            .map(Genre::value)
            .joinToString(",")

        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart().orEmpty()
        val order = filters.firstInstanceOrNull<OrderFilter>()?.toUriPart() ?: "rating"

        return browseRequest(
            page = page,
            query = query.takeIf(String::isNotBlank),
            genres = genres.takeIf(String::isNotBlank),
            status = status.takeIf(String::isNotBlank),
            order = order,
        )
    }

    private fun browseRequest(
        page: Int,
        query: String? = null,
        genres: String? = null,
        status: String? = null,
        order: String? = null,
        sort: String? = null,
    ): Request {
        val url = "$baseUrl/browse".toHttpUrl().newBuilder()
            .addQueryParameter("page", page.toString())

        query?.takeIf(String::isNotBlank)?.let { url.addQueryParameter("q", it) }
        genres?.takeIf(String::isNotBlank)?.let { url.addQueryParameter("genres", it) }
        status?.takeIf(String::isNotBlank)?.let { url.addQueryParameter("status", it) }
        order?.takeIf(String::isNotBlank)?.let { url.addQueryParameter("order", it) }
        sort?.takeIf(String::isNotBlank)?.let { url.addQueryParameter("sort", it) }

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = "div.grid $COMIC_LINK_SELECTOR"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href").toPermSlugIfNeeded())
        title = element.selectFirst("div.block > span.block")!!.ownText()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun searchMangaNextPageSelector() = NEXT_PAGE_SELECTOR

    override fun searchMangaParse(response: Response): MangasPage = parseMangaPage(response)

    private fun parseMangaPage(response: Response): MangasPage {
        val document = response.asJsoup()
        val currentPage = response.request.url.queryParameter("page")?.toIntOrNull() ?: 1
        val hasNextPage = document.select("a[href]").any { element ->
            element.attr("abs:href")
                .toHttpUrlOrNull()
                ?.queryParameter("page")
                ?.toIntOrNull() == currentPage + 1
        }
        return parseMangaPage(document, hasNextPage)
    }

    private fun parseMangaPage(document: Document, hasNextPage: Boolean): MangasPage {
        val mangas = LinkedHashMap<String, SManga>()

        document.select(COMIC_LINK_SELECTOR).forEach { element ->
            addMangaFromElement(mangas, element)
        }

        return MangasPage(mangas.values.filter { !it.parsedTitle().isNullOrBlank() }, hasNextPage)
    }

    private fun parseHomepageSectionMangas(
        document: Document,
        sectionTitle: String,
        stopTitles: Set<String>,
        hasNextPage: Boolean,
    ): MangasPage {
        val mangas = LinkedHashMap<String, SManga>()
        val heading = document.select(HOMEPAGE_SECTION_HEADING_SELECTOR)
            .firstOrNull { it.text().trim() == sectionTitle }
            ?: return MangasPage(emptyList(), hasNextPage)

        var current: Element? = heading
        while (current != null) {
            current = current.nextElementSibling() ?: break

            val nextHeading = current.selectFirst(HOMEPAGE_SECTION_HEADING_SELECTOR)
                ?.text()
                ?.trim()
            if (nextHeading in stopTitles) break

            current.select(COMIC_LINK_SELECTOR).forEach { element ->
                addMangaFromElement(mangas, element)
            }
        }

        return MangasPage(mangas.values.filter { !it.parsedTitle().isNullOrBlank() }, hasNextPage)
    }

    private fun addMangaFromElement(mangas: LinkedHashMap<String, SManga>, element: Element) {
        val url = element.attr("abs:href")
        if (url.isBlank()) return

        val path = url.toHttpUrlOrNull()?.encodedPath ?: return
        val manga = mangas.getOrPut(path) {
            SManga.create().apply {
                setUrlWithoutDomain(url.toPermSlugIfNeeded())
            }
        }

        if (manga.parsedTitle().isNullOrBlank()) {
            val parsedTitle = element.selectFirst("img[alt]")?.attr("alt")
                ?.takeIf(String::isNotBlank)
                ?: cleanMangaTitle(element.text()).takeIf(String::isNotBlank)

            if (parsedTitle != null) {
                manga.title = parsedTitle
            }
        }

        if (manga.thumbnail_url.isNullOrBlank()) {
            manga.thumbnail_url = element.selectFirst("img")?.attr("abs:src")
        }
    }

    override fun getFilterList(): FilterList {
        fetchFilters()
        val filters = mutableListOf<Filter<*>>()
        if (filtersState == FiltersState.FETCHED) {
            filters += listOf(
                GenreFilter("Genres", getGenreFilters()),
                StatusFilter("Status", getStatusFilters()),
            )
        } else {
            filters += Filter.Header("Press 'Reset' to attempt to fetch the filters")
        }

        filters += OrderFilter(
            "Order by",
            listOf(
                Pair("Rating", "rating"),
                Pair("Update", "update"),
                Pair("Latest", "latest"),
                Pair("Z-A", "desc"),
                Pair("A-Z", "asc"),
            ),
        )

        return FilterList(filters)
    }

    private fun getGenreFilters(): List<Genre> = genresList.map { Genre(it.first, it.second) }
    private fun getStatusFilters(): List<Pair<String, String>> = listOf("All" to "") + statusesList

    private var genresList: List<Pair<String, String>> = emptyList()
    private var statusesList: List<Pair<String, String>> = emptyList()

    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters() {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        thread {
            try {
                val filters = client.newCall(GET("$apiUrl/series/filters", headers)).execute().use { response ->
                    response.body.string().parseAs<FiltersDto>()
                }

                genresList = filters.genres
                    .filter { it.id > 0 }
                    .mapNotNull { item ->
                        item.name.trim().takeIf(String::isNotBlank)?.let { name ->
                            name to name.toBrowseSlug()
                        }
                    }

                statusesList = filters.statuses
                    .filter { it.id > 0 }
                    .mapNotNull { item ->
                        item.name.trim().takeIf(String::isNotBlank)?.let { name ->
                            name to name.toBrowseSlug()
                        }
                    }

                filtersState = FiltersState.FETCHED
            } catch (e: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val normalizedUrl = manga.url.normalizeComicPath()
        if (!preferences.dynamicUrl()) return GET(baseUrl + normalizedUrl, headers)
        val match = OLD_FORMAT_MANGA_REGEX.find(normalizedUrl)?.groupValues?.get(2)
        val slug = match ?: normalizedUrl.extractComicSlug()
        val savedSlug = slugMap[slug] ?: "$slug-"
        return GET("$baseUrl/comics/$savedSlug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        rememberDynamicSlug(response.request.url.toString())
        return super.mangaDetailsParse(response)
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("meta[property=og:title]")?.attr("content")
            ?.substringBefore(" - Asura Scans")
            ?.takeIf(String::isNotBlank)
            ?: document.selectFirst("h1")?.text()
            ?: document.selectFirst("title")?.text()?.substringBefore(" - Asura Scans")
            ?: throw Exception("Failed to parse manga title")

        thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
            ?: document.selectFirst("img[src*=/asura-images/covers/]")?.attr("abs:src")

        description = document.selectFirst("meta[property=og:description]")?.attr("content")
            ?.takeIf(String::isNotBlank)
            ?: document.select("p")
                .map { it.text().trim() }
                .firstOrNull { text -> text.length > 80 && !text.startsWith("By the Studio") }

        genre = document.select("a[href^=/browse?genres=]")
            .map { it.text().trim() }
            .filter(String::isNotBlank)
            .distinct()
            .joinToString()
            .ifBlank { null }

        status = parseStatus(
            document.select("body").text().let { bodyText ->
                listOf("Ongoing", "Hiatus", "Completed", "Dropped", "Season End")
                    .firstOrNull(bodyText::contains)
            },
        )
    }

    private fun parseStatus(status: String?) = when (status) {
        "Ongoing", "Season End" -> SManga.ONGOING
        "Hiatus" -> SManga.ON_HIATUS
        "Completed" -> SManga.COMPLETED
        "Dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        rememberDynamicSlug(response.request.url.toString())

        val document = response.asJsoup()
        val chapters = LinkedHashMap<String, SChapter>()

        document.select(CHAPTER_LINK_SELECTOR).forEach { element ->
            val url = element.attr("abs:href")
            if (!url.contains("/comics/") || !url.contains("/chapter/")) return@forEach

            val rawText = element.text().replace(WHITESPACE_REGEX, " ").trim()
            if (!rawText.isRealChapterEntry()) return@forEach

            val path = url.toHttpUrlOrNull()?.encodedPath ?: return@forEach
            if (chapters.containsKey(path)) return@forEach

            val isPremium = rawText.contains("EARLY ACCESS", ignoreCase = true) ||
                rawText.contains("Unlocks in", ignoreCase = true)

            if (isPremium) return@forEach

            chapters[path] = SChapter.create().apply {
                setUrlWithoutDomain(url.toPermSlugIfNeeded())
                name = buildChapterName(rawText, path.substringAfterLast('/'))
            }
        }

        if (chapters.isNotEmpty()) return chapters.values.toList()
        return super.chapterListParse(response)
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListSelector() = "div.scrollbar-thumb-themecolor > div.group:not(:has(svg))"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href").toPermSlugIfNeeded())
        val chNumber = element.selectFirst("h3")!!.ownText()
        val chTitle = element.select("h3 > span").joinToString(" ") { it.ownText() }
        val isPremiumChapter = element.selectFirst("svg") != null
        val baseName = if (chTitle.isBlank()) chNumber else "$chNumber - $chTitle"
        name = if (isPremiumChapter) "🔒 $baseName" else baseName

        date_upload = try {
            val text = element.selectFirst("h3 + h3")!!.ownText()
            val cleanText = text.replace(CLEAN_DATE_REGEX, "$1")
            dateFormat.parse(cleanText)?.time ?: 0
        } catch (_: Exception) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val normalizedUrl = chapter.url.normalizeComicPath().ensureTrailingSlash()
        if (!preferences.dynamicUrl()) {
            return GET(baseUrl + normalizedUrl, headers)
        }
        val match = OLD_FORMAT_CHAPTER_REGEX.containsMatchIn(normalizedUrl)
        if (match) throw Exception("Please refresh the chapter list before reading.")
        val slug = normalizedUrl.extractComicSlug()
        val savedSlug = slugMap[slug] ?: "$slug-"
        return GET(baseUrl + normalizedUrl.replaceComicSlug(slug, savedSlug), headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val scriptData = document.select("script:containsData(self.__next_f.push)")
            .joinToString("") { it.data().substringAfter("\"").substringBeforeLast("\"") }

        val chapterDataMatch = CHAPTER_DATA_REGEX.find(scriptData)
        val pagesData = PAGES_REGEX.find(scriptData)?.groupValues?.get(1)

        if (chapterDataMatch != null && pagesData != null) {
            val chapterId = chapterDataMatch.groupValues[1].toIntOrNull()
            val pages = try {
                pagesData.unescape().parseAs<List<PageDto>>()
            } catch (_: Exception) {
                emptyList()
            }

            if (pages.isNotEmpty()) {
                return pages.sortedBy(PageDto::order).toPageList()
            }

            if (chapterId != null) {
                return fetchChapterImages(chapterId)
            }
        }

        // Prefer explicit image nodes before falling back to a raw HTML scan.
        val images = document.select("img[src*=/chapters/], img[data-src*=/chapters/]")
            .mapNotNull { el ->
                el.attr("abs:src").ifBlank { el.attr("abs:data-src") }.ifBlank { null }
            }
            .map(::normalizeChapterImageUrl)
            .distinct()

        val scriptImages = extractChapterImageUrlsFromHtml(scriptData.unescape())

        // The site occasionally leaves chapter CDN URLs only in embedded HTML/script payloads.
        val htmlImages = extractChapterImageUrlsFromHtml(document.html())

        val inferredImages = inferSequentialChapterImageUrls(document, scriptImages + htmlImages + images)
        if (inferredImages.isNotEmpty()) {
            return inferredImages.toImagePages()
        }

        if (scriptImages.isNotEmpty()) {
            return scriptImages.toImagePages()
        }

        if (htmlImages.isNotEmpty()) {
            return htmlImages.toImagePages()
        }

        if (images.isNotEmpty()) {
            return images.toImagePages()
        }

        throw Exception("Failed to find chapter pages")
    }

    private fun fetchChapterImages(chapterId: Int): List<Page> {
        val xsrfToken = getXsrfToken()
        val unlockPayload = UnlockRequestDto(chapterId).toJsonString()

        val unlockResponse = apiClient.newCall(
            buildApiRequest("$apiUrl/chapter/unlock", unlockPayload, xsrfToken),
        ).execute()

        val unlockData = unlockResponse.parseAs<UnlockResponseDto>("Failed to load chapter")

        if (!unlockData.success) {
            throw Exception("Chapter locked. Please unlock in WebView first.")
        }

        val unlockToken = unlockData.data.unlockToken
        val pages = unlockData.data.pages.sortedBy { it.order }

        return pages.mapIndexed { index, page ->
            val imageUrl = getPageImageUrl(page.id, chapterId, unlockToken, xsrfToken)
            Page(index, imageUrl = imageUrl)
        }
    }

    private fun getPageImageUrl(mediaId: Int, chapterId: Int, unlockToken: String, xsrfToken: String): String {
        val mediaPayload = MediaRequestDto(mediaId, chapterId, unlockToken, "max-quality").toJsonString()

        val mediaResponse = apiClient.newCall(
            buildApiRequest("$apiUrl/media", mediaPayload, xsrfToken),
        ).execute()

        return mediaResponse.parseAs<MediaResponseDto>("Failed to get image URL").data
    }

    private fun buildApiRequest(url: String, jsonPayload: String, xsrfToken: String): Request = POST(
        url,
        headers.newBuilder()
            .add("X-XSRF-TOKEN", xsrfToken)
            .add("Accept", "application/json")
            .add("X-Requested-With", "XMLHttpRequest")
            .build(),
        jsonPayload.toRequestBody("application/json".toMediaType()),
    )

    private inline fun <reified T> Response.parseAs(errorPrefix: String): T {
        if (!isSuccessful) {
            close()
            val errorMsg = when (code) {
                401 -> "Not logged in. Please log in via WebView."
                403 -> "No premium subscription."
                419 -> "Session expired. Please log in again via WebView."
                else -> "$errorPrefix (HTTP $code)"
            }
            throw Exception(errorMsg)
        }

        val responseBody = body.string()

        return try {
            responseBody.parseAs<T>()
        } catch (e: Exception) {
            throw Exception("$errorPrefix: Invalid response")
        }
    }

    private fun getXsrfToken(): String {
        val xsrfToken = sequence {
            apiUrl.toHttpUrlOrNull()?.let { yield(it) }
            baseUrl.toHttpUrlOrNull()?.let { yield(it) }
        }.mapNotNull { httpUrl ->
            client.cookieJar.loadForRequest(httpUrl)
                .firstOrNull { it.name.equals("XSRF-TOKEN", ignoreCase = true) }
                ?.value
        }.firstOrNull()

        if (xsrfToken == null) {
            throw Exception("Not logged in. Please log in via WebView to access premium chapters.")
        }

        return runCatching {
            URLDecoder.decode(xsrfToken, StandardCharsets.UTF_8.name())
        }.getOrDefault(xsrfToken)
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

    private inline fun <reified R> List<*>.firstInstanceOrNull(): R? = filterIsInstance<R>().firstOrNull()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_DYNAMIC_URL
            title = "Automatically update dynamic URLs"
            summary = "Automatically update random numbers in manga URLs.\nHelps mitigating HTTP 404 errors during update and \"in library\" marks when browsing.\nNote: This setting may require clearing database in advanced settings and migrating all manga to the same source."
            setDefaultValue(true)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_FORCE_HIGH_QUALITY
            title = "Force high quality chapter images"
            summary = "Attempt to use high quality chapter images.\nWill increase bandwidth by ~50%."
            if (failedHighQuality) {
                summary = "$summary\n*DISABLED* because of missing high quality images."
            }
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    private fun SharedPreferences.dynamicUrl(): Boolean = getBoolean(PREF_DYNAMIC_URL, true)
    private fun SharedPreferences.forceHighQuality(): Boolean = getBoolean(
        PREF_FORCE_HIGH_QUALITY,
        false,
    )

    private fun loadSlugMap(): MutableMap<String, String> {
        val jsonMap = preferences.getString(PREF_SLUG_MAP, "{}")!!
        return try {
            jsonMap.parseAs<Map<String, String>>().toMutableMap()
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    private fun persistSlugMapIfChanged(absSlug: String, slug: String) {
        if (absSlug.isBlank() || slug.isBlank()) return
        if (slugMap[absSlug] == slug) return

        slugMap[absSlug] = slug
        preferences.edit()
            .putString(PREF_SLUG_MAP, slugMap.toJsonString())
            .apply()
    }

    private fun rememberDynamicSlug(url: String) {
        if (!preferences.dynamicUrl()) return

        val newSlug = url.extractComicSlug()
        if (newSlug.isEmpty()) return

        val absSlug = newSlug.substringBeforeLast("-")
        persistSlugMapIfChanged(absSlug, newSlug)
    }

    private fun String.toPermSlugIfNeeded(): String {
        val normalized = normalizeComicPath()
        if (!preferences.dynamicUrl()) return normalized
        val slug = normalized.extractComicSlug()
        val absSlug = slug.substringBeforeLast("-")
        persistSlugMapIfChanged(absSlug, slug)
        return normalized.replaceComicSlug(slug, absSlug)
    }

    private fun cleanMangaTitle(rawTitle: String): String = rawTitle.replace(WHITESPACE_REGEX, " ")
        .replace(TRAILING_CHAPTER_NUMBER_REGEX, "")
        .trim()

    private fun buildChapterName(rawText: String, fallbackChapter: String): String {
        val normalized = rawText.replace(WHITESPACE_REGEX, " ").trim()
        val withoutDate = normalized.replace(CHAPTER_DATE_SUFFIX_REGEX, "").trim()

        return withoutDate.ifBlank { "Chapter $fallbackChapter" }
    }

    private fun String.isRealChapterEntry(): Boolean = CHAPTER_ENTRY_REGEX.containsMatchIn(this)

    private fun String.ensureTrailingSlash(): String = if (endsWith('/')) this else "$this/"

    private fun normalizeChapterImageUrl(url: String): String = url
        .replace("\\/", "/")
        .replace("&amp;", "&")
        .let { if (it.startsWith("//")) "https:$it" else it }

    private fun extractChapterImageUrlsFromHtml(html: String): List<String> = normalizeChapterImageUrl(html)
        .let(NORMALIZED_CHAPTER_IMAGE_URL_REGEX::findAll)
        .map { it.value }
        .filter(::isValidChapterImageUrl)
        .distinct()
        .toList()

    private fun inferSequentialChapterImageUrls(document: Document, seedImages: List<String>): List<String> {
        val knownImages = seedImages
            .filter(::isValidChapterImageUrl)
            .distinct()
            .sortedWith(compareBy({ extractChapterImageNumber(it) ?: Int.MAX_VALUE }, { it }))

        val pageCount = extractVisiblePageCount(document) ?: return emptyList()
        if (knownImages.isEmpty()) return emptyList()

        val knownByPage = knownImages.associateBy { extractChapterImageNumber(it) }
        val highestKnownPage = knownByPage.keys.filterNotNull().maxOrNull() ?: knownImages.size
        if (pageCount <= highestKnownPage) return knownImages

        val pattern = buildChapterImagePattern(knownImages) ?: return knownImages

        return (1..pageCount).map { pageNumber ->
            knownByPage[pageNumber] ?: pattern.pageUrl(pageNumber)
        }
    }

    private fun extractVisiblePageCount(document: Document): Int? {
        val labeledPageCount = PAGE_LABEL_REGEX.findAll(document.html())
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .maxOrNull()

        val pagerPageCount = CONSECUTIVE_PAGE_NUMBER_REGEX.findAll(document.text())
            .mapNotNull { match ->
                match.value.trim()
                    .split(WHITESPACE_REGEX)
                    .mapNotNull(String::toIntOrNull)
                    .takeIf { values ->
                        values.size >= 3 && values.zipWithNext().all { (left, right) -> right == left + 1 }
                    }
                    ?.lastOrNull()
            }
            .maxOrNull()

        return listOfNotNull(labeledPageCount, pagerPageCount).maxOrNull()
    }

    private fun buildChapterImagePattern(seedImages: List<String>): ChapterImagePattern? {
        val firstMatch = seedImages.firstNotNullOfOrNull(CHAPTER_IMAGE_SEQUENCE_REGEX::find) ?: return null
        val secondMatch = seedImages
            .mapNotNull(CHAPTER_IMAGE_SEQUENCE_REGEX::find)
            .firstOrNull { it.groupValues[2].toIntOrNull() == 2 }

        val prefix = firstMatch.groupValues[1]
        val width = firstMatch.groupValues[2].length
        val suffix = firstMatch.groupValues[4]
        val subsequentPageVariant = secondMatch?.groupValues?.get(3).orEmpty()

        return ChapterImagePattern(prefix, width, subsequentPageVariant, suffix)
    }

    private fun extractChapterImageNumber(url: String): Int? = CHAPTER_IMAGE_SEQUENCE_REGEX.find(url)
        ?.groupValues
        ?.getOrNull(2)
        ?.toIntOrNull()

    private fun isValidChapterImageUrl(url: String): Boolean {
        val httpUrl = url.toHttpUrlOrNull() ?: return false
        return httpUrl.host.contains("asurascans.com") && CHAPTER_IMAGE_FILENAME_REGEX.containsMatchIn(httpUrl.encodedPath)
    }

    private fun List<String>.toImagePages(): List<Page> = mapIndexed { index, url ->
        Page(index, imageUrl = url)
    }

    private fun List<PageDto>.toPageList(): List<Page> = mapIndexed { index, page ->
        val imageUrl = page.url.toHttpUrlOrNull()
            ?.newBuilder()
            ?.fragment("pageListParse")
            ?.build()
            ?.toString()
            ?: page.url
        Page(index, imageUrl = imageUrl)
    }

    private fun SManga.parsedTitle(): String? = runCatching { title }
        .getOrNull()
        ?.takeIf(String::isNotBlank)

    private fun String.toBrowseSlug(): String = lowercase(Locale.US)
        .replace("&", " and ")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun String.normalizeComicPath(): String = replace("/series/", "/comics/")

    private fun String.extractComicSlug(): String = when {
        contains("/comics/") -> substringAfter("/comics/").substringBefore("/")
        contains("/series/") -> substringAfter("/series/").substringBefore("/")
        else -> substringAfter("/manga/").substringBefore("/")
    }

    private fun String.replaceComicSlug(oldSlug: String, newSlug: String): String {
        val normalized = normalizeComicPath()
        return normalized.replace("/comics/$oldSlug", "/comics/$newSlug")
    }

    private fun String.unescape(): String = UNESCAPE_REGEX.replace(this, "$1")

    private data class ChapterImagePattern(
        val prefix: String,
        val width: Int,
        val subsequentPageVariant: String,
        val suffix: String,
    ) {
        fun pageUrl(pageNumber: Int): String {
            val page = pageNumber.toString().padStart(width, '0')
            val variant = if (pageNumber == 1) "" else subsequentPageVariant
            return "$prefix$page$variant$suffix"
        }
    }

    companion object {
        private const val COMIC_LINK_SELECTOR = "a[href^=/comics/]:not([href*=/chapter/])"
        private const val CHAPTER_LINK_SELECTOR = "a[href*=/chapter/]"
        private const val NEXT_PAGE_SELECTOR = "a:contains(Next page), div.flex > a.flex.bg-themecolor:contains(Next)"
        private const val HOMEPAGE_SECTION_HEADING_SELECTOR = "h2, h3"

        private val UNESCAPE_REGEX = """\\(.)""".toRegex()
        private val WHITESPACE_REGEX = """\\s+""".toRegex()
        private val TRAILING_CHAPTER_NUMBER_REGEX = """\s+\d+(?:\.\d+)?$""".toRegex()
        private val CHAPTER_DATE_SUFFIX_REGEX = """\s+(Just now|last week|\d+\s+(?:minute|minutes|hour|hours|day|days|week|weeks|month|months)\s+ago|[A-Z][a-z]{2}\s+\d{1,2},\s+\d{4})$""".toRegex()
        private val PAGES_REGEX = """\\"pages\\":(\[.*?])""".toRegex()
        private val NORMALIZED_CHAPTER_IMAGE_URL_REGEX = """(?:https?:)?//cdn\.asurascans\.com/asura-images/chapters/[^"'\s<>]+""".toRegex()
        private val CHAPTER_IMAGE_FILENAME_REGEX = """/(\d+(?:_p\d+)?)\.(?:webp|jpg|jpeg|png)$""".toRegex(RegexOption.IGNORE_CASE)
        private val CHAPTER_IMAGE_SEQUENCE_REGEX = """^(.*?/)(\d+)(?:(_p\d+))?(\.[A-Za-z0-9]+(?:\?.*)?)$""".toRegex()
        private val CHAPTER_DATA_REGEX = """\\"chapter\\":\{\\"id\\":(\d+).*?\\"is_early_access\\":(true|false)""".toRegex(RegexOption.DOT_MATCHES_ALL)
        private val CHAPTER_ENTRY_REGEX = """(?i)\bchapter\s+\d""".toRegex()
        private val CLEAN_DATE_REGEX = """(\d+)(st|nd|rd|th)""".toRegex()
        private val PAGE_LABEL_REGEX = """Page\s+(\d+)\s*-\s*Chapter""".toRegex(RegexOption.IGNORE_CASE)
        private val CONSECUTIVE_PAGE_NUMBER_REGEX = """(?:^|\D)(\d{1,3}(?:\s+\d{1,3}){2,})(?=\D|$)""".toRegex()
        private val OLD_FORMAT_MANGA_REGEX = """^/manga/(\d+-)?([^/]+)/?$""".toRegex()
        private val OLD_FORMAT_CHAPTER_REGEX = """^/(\d+-)?[^/]*-chapter-\d+(-\d+)*/?$""".toRegex()
        private val OPTIMIZED_IMAGE_PATH_REGEX = """^/storage/media/(\d+)/conversions/(.*)-optimized\.webp$""".toRegex()

        private const val PREF_SLUG_MAP = "pref_slug_map_2"
        private const val PREF_DYNAMIC_URL = "pref_dynamic_url"
        private const val PREF_FORCE_HIGH_QUALITY = "pref_force_high_quality"
    }
}
