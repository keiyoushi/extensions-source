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
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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

    override val baseUrl = "https://asuracomic.net"

    private val apiUrl = "https://gg.asuracomic.net/api"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMMM d yyyy", Locale.US)

    private val preferences: SharedPreferences = getPreferences()

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

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/series?genres=&status=-1&types=-1&order=rating&page=$page", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/page/$page", headers)

    override fun latestUpdatesSelector() = "div.grid.grid-rows-1.grid-cols-1 > div.w-full"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        val link = element.selectFirst("a[href^=/series/]")!!
        setUrlWithoutDomain(link.attr("abs:href").toPermSlugIfNeeded())
        title = element.selectFirst("span.text-\\[15px\\] a")!!.text()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series".toHttpUrl().newBuilder()

        url.addQueryParameter("page", page.toString())

        if (query.isNotBlank()) {
            url.addQueryParameter("name", query)
        }

        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state.orEmpty()
            .filter(Genre::state)
            .map(Genre::id)
            .joinToString(",")

        val status = filters.firstInstanceOrNull<StatusFilter>()?.toUriPart() ?: "-1"
        val types = filters.firstInstanceOrNull<TypeFilter>()?.toUriPart() ?: "-1"
        val order = filters.firstInstanceOrNull<OrderFilter>()?.toUriPart() ?: "rating"

        url.addQueryParameter("genres", genres)
        url.addQueryParameter("status", status)
        url.addQueryParameter("types", types)
        url.addQueryParameter("order", order)

        return GET(url.build(), headers)
    }

    override fun searchMangaSelector() = "div.grid > a[href]"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.attr("abs:href").toPermSlugIfNeeded())
        title = element.selectFirst("div.block > span.block")!!.ownText()
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    override fun searchMangaNextPageSelector() = "div.flex > a.flex.bg-themecolor:contains(Next)"

    override fun getFilterList(): FilterList {
        fetchFilters()
        val filters = mutableListOf<Filter<*>>()
        if (filtersState == FiltersState.FETCHED) {
            filters += listOf(
                GenreFilter("Genres", getGenreFilters()),
                StatusFilter("Status", getStatusFilters()),
                TypeFilter("Types", getTypeFilters()),
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
    private fun getStatusFilters(): List<Pair<String, String>> = statusesList.map { it.first to it.second.toString() }
    private fun getTypeFilters(): List<Pair<String, String>> = typesList.map { it.first to it.second.toString() }

    private var genresList: List<Pair<String, Int>> = emptyList()
    private var statusesList: List<Pair<String, Int>> = emptyList()
    private var typesList: List<Pair<String, Int>> = emptyList()

    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters() {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        thread {
            try {
                val response = client.newCall(GET("$apiUrl/series/filters", headers)).execute()
                val filters = response.body.string().parseAs<FiltersDto>()

                genresList = filters.genres.filter { it.id > 0 }.map { it.name.trim() to it.id }
                statusesList = filters.statuses.map { it.name.trim() to it.id }
                typesList = filters.types.map { it.name.trim() to it.id }

                filtersState = FiltersState.FETCHED
            } catch (e: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (!preferences.dynamicUrl()) return super.mangaDetailsRequest(manga)
        val match = OLD_FORMAT_MANGA_REGEX.find(manga.url)?.groupValues?.get(2)
        val slug = match ?: manga.url.substringAfter("/series/").substringBefore("/")
        val savedSlug = preferences.slugMap[slug] ?: "$slug-"
        return GET("$baseUrl/series/$savedSlug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        if (preferences.dynamicUrl()) {
            val url = response.request.url.toString()
            val newSlug = url.substringAfter("/series/", "").substringBefore("/")
            if (newSlug.isNotEmpty()) {
                val absSlug = newSlug.substringBeforeLast("-")
                preferences.slugMap = preferences.slugMap.apply { put(absSlug, newSlug) }
            }
        }
        return super.mangaDetailsParse(response)
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("span.text-xl.font-bold, h3.truncate")!!.ownText()
        thumbnail_url = document.selectFirst("img[alt=poster]")?.attr("abs:src")
        description = document.selectFirst("span.font-medium.text-sm")?.text()
        author = document.selectFirst("div.grid > div:has(h3:eq(0):containsOwn(Author)) > h3:eq(1)")?.ownText()
        artist = document.selectFirst("div.grid > div:has(h3:eq(0):containsOwn(Artist)) > h3:eq(1)")?.ownText()
        genre = buildList {
            document.selectFirst("div.flex:has(h3:eq(0):containsOwn(type)) > h3:eq(1)")
                ?.ownText()?.let(::add)
            document.select("div[class^=space] > div.flex > button.text-white")
                .forEach { add(it.ownText()) }
        }.joinToString()
        status = parseStatus(document.selectFirst("div.flex:has(h3:eq(0):containsOwn(Status)) > h3:eq(1)")?.ownText())
    }

    private fun parseStatus(status: String?) = when (status) {
        "Ongoing", "Season End" -> SManga.ONGOING
        "Hiatus" -> SManga.ON_HIATUS
        "Completed" -> SManga.COMPLETED
        "Dropped" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        if (preferences.dynamicUrl()) {
            val url = response.request.url.toString()
            val newSlug = url.substringAfter("/series/", "").substringBefore("/")
            if (newSlug.isNotEmpty()) {
                val absSlug = newSlug.substringBeforeLast("-")
                preferences.slugMap = preferences.slugMap.apply { put(absSlug, newSlug) }
            }
        }
        return super.chapterListParse(response)
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListSelector() = if (preferences.hidePremiumChapters()) "div.scrollbar-thumb-themecolor > div.group:not(:has(svg))" else "div.scrollbar-thumb-themecolor > div.group"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href").toPermSlugIfNeeded())
        val chNumber = element.selectFirst("h3")!!.ownText()
        val chTitle = element.select("h3 > span").joinToString(" ") { it.ownText() }
        val isPremiumChapter = element.selectFirst("svg") != null
        this.name = if (chTitle.isBlank()) chNumber else "$chNumber - $chTitle"

        date_upload = try {
            val text = element.selectFirst("h3 + h3")!!.ownText()
            val cleanText = text.replace(CLEAN_DATE_REGEX, "$1")
            dateFormat.parse(cleanText)?.time ?: 0
        } catch (_: Exception) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (!preferences.dynamicUrl()) return super.pageListRequest(chapter)
        val match = OLD_FORMAT_CHAPTER_REGEX.containsMatchIn(chapter.url)
        if (match) throw Exception("Please refresh the chapter list before reading.")
        val slug = chapter.url.substringAfter("/series/").substringBefore("/")
        val savedSlug = preferences.slugMap[slug] ?: "$slug-"
        return GET(baseUrl + chapter.url.replace(slug, savedSlug), headers)
    }

    override fun pageListParse(document: Document): List<Page> {
        val scriptData = document.select("script:containsData(self.__next_f.push)")
            .joinToString("") { it.data().substringAfter("\"").substringBeforeLast("\"") }

        val chapterDataMatch = CHAPTER_DATA_REGEX.find(scriptData)
        val pagesData = PAGES_REGEX.find(scriptData)?.groupValues?.get(1)

        if (chapterDataMatch != null && pagesData != null) {
            // check for premium chapter
            val chapterId = chapterDataMatch.groupValues[1].toIntOrNull()
            val pages = try {
                pagesData.unescape().parseAs<List<PageDto>>()
            } catch (_: Exception) {
                emptyList()
            }

            // If unlocked, fetch info via API
            if (chapterId != null && pages.isEmpty()) {
                return fetchChapterImages(chapterId)
            }
        }

        // continue with normal chapter handling
        if (pagesData == null) {
            throw Exception("Failed to find chapter pages")
        }

        val pageList = pagesData.unescape().parseAs<List<PageDto>>().sortedBy { it.order }
        return pageList.mapIndexed { i, page ->
            val newUrl = page.url.toHttpUrlOrNull()?.run {
                newBuilder()
                    .fragment("pageListParse")
                    .build()
                    .toString()
            }

            Page(i, imageUrl = newUrl ?: page.url)
        }
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
            key = PREF_HIDE_PREMIUM_CHAPTERS
            title = "Hide premium chapters"
            summary = "Hides the chapters that require a subscription to view"
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

    private var SharedPreferences.slugMap: MutableMap<String, String>
        get() {
            val jsonMap = getString(PREF_SLUG_MAP, "{}")!!
            return try {
                jsonMap.parseAs<Map<String, String>>().toMutableMap()
            } catch (_: Exception) {
                mutableMapOf()
            }
        }
        set(newSlugMap) {
            edit()
                .putString(PREF_SLUG_MAP, newSlugMap.toJsonString())
                .apply()
        }

    private fun SharedPreferences.dynamicUrl(): Boolean = getBoolean(PREF_DYNAMIC_URL, true)
    private fun SharedPreferences.hidePremiumChapters(): Boolean = getBoolean(
        PREF_HIDE_PREMIUM_CHAPTERS,
        true,
    )
    private fun SharedPreferences.forceHighQuality(): Boolean = getBoolean(
        PREF_FORCE_HIGH_QUALITY,
        false,
    )

    private fun String.toPermSlugIfNeeded(): String {
        if (!preferences.dynamicUrl()) return this
        val slug = this.substringAfter("/series/").substringBefore("/")
        val absSlug = slug.substringBeforeLast("-")
        preferences.slugMap = preferences.slugMap.apply { put(absSlug, slug) }
        return this.replace(slug, absSlug)
    }

    private fun String.unescape(): String = UNESCAPE_REGEX.replace(this, "$1")

    companion object {
        private val UNESCAPE_REGEX = """\\(.)""".toRegex()
        private val PAGES_REGEX = """\\"pages\\":(\[.*?])""".toRegex()

        // Match chapter metadata: "chapter":{"id":123..."is_early_access":true}
        private val CHAPTER_DATA_REGEX = """\\"chapter\\":\{\\"id\\":(\d+).*?\\"is_early_access\\":(true|false)""".toRegex(RegexOption.DOT_MATCHES_ALL)
        private val CLEAN_DATE_REGEX = """(\d+)(st|nd|rd|th)""".toRegex()
        private val OLD_FORMAT_MANGA_REGEX = """^/manga/(\d+-)?([^/]+)/?$""".toRegex()
        private val OLD_FORMAT_CHAPTER_REGEX = """^/(\d+-)?[^/]*-chapter-\d+(-\d+)*/?$""".toRegex()
        private val OPTIMIZED_IMAGE_PATH_REGEX = """^/storage/media/(\d+)/conversions/(.*)-optimized\.webp$""".toRegex()

        private const val PREF_SLUG_MAP = "pref_slug_map_2"
        private const val PREF_DYNAMIC_URL = "pref_dynamic_url"
        private const val PREF_HIDE_PREMIUM_CHAPTERS = "pref_hide_premium_chapters"
        private const val PREF_FORCE_HIGH_QUALITY = "pref_force_high_quality"
    }
}
