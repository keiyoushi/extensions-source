package eu.kanade.tachiyomi.extension.en.asurascans

import android.app.Application
import android.content.SharedPreferences
import android.webkit.CookieManager
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import keiyoushi.utils.getPreferences
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.injectLazy
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.text.RegexOption

class AsuraScans : ParsedHttpSource(), ConfigurableSource {

    override val name = "Asura Scans"

    override val baseUrl = "https://asuracomic.net"

    private val apiUrl = "https://gg.asuracomic.net/api"

    override val lang = "en"

    override val supportsLatest = true

    private val dateFormat = SimpleDateFormat("MMMM d yyyy", Locale.US)

    private val preferences: SharedPreferences = getPreferences()

    private val application: Application by injectLazy()
    private val cookieManager by lazy { CookieManager.getInstance() }

    @Volatile
    private var cachedAuthState: Boolean? = null

    @Volatile
    private var lastAuthCheck: Long = 0L

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

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .cookieJar(
            object : CookieJar {
                override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                    if (cookies.isEmpty()) return
                    for (cookie in cookies) {
                        runCatching {
                            cookieManager.setCookie(url.toString(), cookie.toString())
                        }
                    }
                    runCatching { cookieManager.flush() }
                }

                override fun loadForRequest(url: HttpUrl): MutableList<Cookie> {
                    val cookieString = runCatching {
                        cookieManager.getCookie(url.toString())
                    }.getOrNull() ?: return mutableListOf()

                    return cookieString.split(';')
                        .mapNotNull { Cookie.parse(url, it.trim()) }
                        .toMutableList()
                }
            },
        )
        .addInterceptor(::authInterceptor)
        .addInterceptor(::forceHighQualityInterceptor)
        .rateLimit(2, 2)
        .build()

    private var failedHighQuality = false

    private fun forceHighQualityInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()

        val shouldTryHighQuality = runCatching {
            request.header(HQ_ATTEMPT_HEADER) == null &&
                preferences.forceHighQuality() &&
                isAuthenticated() &&
                !failedHighQuality &&
                request.url.fragment == "pageListParse"
        }.getOrDefault(false)

        if (shouldTryHighQuality) {
            STANDARD_IMAGE_PATH_REGEX.find(request.url.encodedPath)?.also { match ->
                val (id, filename) = match.destructured
                val optimizedName = "$filename-optimized.webp"
                val optimizedUrl = request.url.newBuilder()
                    .encodedPath("/storage/media/$id/conversions/$optimizedName")
                    .build()

                val hiResRequest = request.newBuilder()
                    .url(optimizedUrl)
                    .header(HQ_ATTEMPT_HEADER, "1")
                    .build()
                val response = runCatching { chain.proceed(hiResRequest) }.getOrNull()
                if (response?.isSuccessful == true) {
                    return response
                } else {
                    failedHighQuality = true
                    response?.close()
                }
            }
        }

        return chain.proceed(request)
    }

    private fun authInterceptor(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())

        if (response.code == 401 || response.code == 403) {
            handleSessionExpiry(response)
        }

        val location = response.header("Location")
        if (location != null && location.contains("/login", ignoreCase = true)) {
            handleSessionExpiry(response)
        }

        return response
    }

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/series?genres=&status=-1&types=-1&order=rating&page=$page", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/series?genres=&status=-1&types=-1&order=update&page=$page", headers)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

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
                val filters = json.decodeFromString<FiltersDto>(response.body.string())

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

    override fun chapterListSelector(): String {
        val authenticated = runCatching { isAuthenticated() }.getOrDefault(false)
        return when {
            authenticated -> "div.scrollbar-thumb-themecolor > div.group"
            preferences.hidePremiumChapters() -> "div.scrollbar-thumb-themecolor > div.group:not(:has(svg))"
            else -> "div.scrollbar-thumb-themecolor > div.group"
        }
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("abs:href").toPermSlugIfNeeded())
        val chNumber = element.selectFirst("h3")!!.ownText()
        val chTitle = element.select("h3 > span").joinToString(" ") { it.ownText() }
        name = if (chTitle.isBlank()) chNumber else "$chNumber - $chTitle"
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
        val chapterMeta = document.extractChapterMetadata()
        val isPremium = chapterMeta?.isEarlyAccess == true

        if (isPremium) {
            val chapterId = chapterMeta?.id ?: throw Exception("Chapter metadata not found")
            if (!isAuthenticated()) {
                throw Exception(PREMIUM_AUTH_MESSAGE)
            }

            val unlockData = unlockChapter(chapterId)
            val unlockToken = unlockData.unlockToken ?: run {
                resetAuthCache()
                throw Exception("Missing unlock token. Please login again.")
            }

            val quality = MEDIA_QUALITY_MAX
            val orderedPages = unlockData.pages.sortedBy { it.order }
            if (orderedPages.isEmpty()) {
                throw Exception("Premium chapter pages unavailable.")
            }

            return orderedPages.mapIndexed { index, page ->
                val imageUrl = fetchMediaUrl(page.id, chapterId, unlockToken, quality)
                Page(index, imageUrl = appendPageFragment(imageUrl))
            }
        }

        return parseStandardPageList(document)
    }

    private fun parseStandardPageList(document: Document): List<Page> {
        val scriptElement = document.select("script")
            .firstOrNull { PAGES_REGEX.containsMatchIn(it.data()) }
            ?: throw Exception("Failed to find chapter pages")
        val scriptData = scriptElement.data()
        val pagesData = PAGES_REGEX.find(scriptData)?.groupValues?.get(1)
            ?: throw Exception("Failed to find chapter pages")
        val pageList = json.decodeFromString<List<PageDto>>(pagesData.unescape()).sortedBy { it.order }
        return pageList.mapIndexed { index, page ->
            Page(index, imageUrl = appendPageFragment(page.url))
        }
    }

    private fun Document.extractChapterMetadata(): ChapterMetadata? {
        val script = select("script")
            .map { it.data() }
            .firstOrNull { it.contains(CHAPTER_DATA_TOKEN) }
            ?: return null
        val match = CHAPTER_DATA_REGEX.find(script) ?: return null
        val id = match.groupValues[1].toIntOrNull() ?: return null
        val isEarly = match.groupValues[2].toBoolean()
        return ChapterMetadata(id, isEarly)
    }

    private fun appendPageFragment(url: String): String {
        return url.toHttpUrlOrNull()?.newBuilder()
            ?.fragment("pageListParse")
            ?.build()
            ?.toString()
            ?: url
    }

    private fun buildApiPostRequest(url: String, body: RequestBody): Request {
        val builder = Request.Builder()
            .url(url)
            .headers(headersBuilder().build())
            .post(body)
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Requested-With", "XMLHttpRequest")

        getXsrfToken()?.let { token ->
            builder.addHeader("X-XSRF-TOKEN", token)
        }

        return builder.build()
    }

    private fun getXsrfToken(): String? {
        val cookieToken = sequence {
            apiUrl.toHttpUrlOrNull()?.let { yield(it) }
            baseUrl.toHttpUrlOrNull()?.let { yield(it) }
        }.mapNotNull { httpUrl ->
            client.cookieJar.loadForRequest(httpUrl)
                .firstOrNull { it.name.equals("XSRF-TOKEN", ignoreCase = true) }
                ?.value
        }.firstOrNull()

        return decodeCookieValue(cookieToken)
    }

    private fun decodeCookieValue(value: String?): String? {
        if (value.isNullOrEmpty()) return null
        return runCatching { URLDecoder.decode(value, StandardCharsets.UTF_8.name()) }.getOrDefault(value)
    }

    private fun unlockChapter(chapterId: Int): UnlockDataDto {
        val payload = json.encodeToString(UnlockRequestDto(chapterId))
        val body = payload.toRequestBody(JSON_MEDIA_TYPE)
        val request = buildApiPostRequest("$apiUrl/chapter/unlock", body)
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty unlock response")

            if (!response.isSuccessful) {
                response.close()
                if (response.code == 401 || response.code == 403 || response.code == 419) {
                    resetAuthCache()
                    throw Exception("Session expired. Please login again.")
                }
                throw Exception("Unable to unlock premium chapter. (${response.code})")
            }

            val unlock = json.decodeFromString<UnlockResponseDto>(responseBody)
            val data = unlock.data
            if (!unlock.success || data == null || data.unlockToken.isNullOrEmpty()) {
                resetAuthCache()
                throw Exception(unlock.message ?: "Unable to unlock premium chapter. Please login again.")
            }
            cachedAuthState = true
            lastAuthCheck = System.currentTimeMillis()
            return data
        }
    }

    private fun fetchMediaUrl(mediaId: Int, chapterId: Int, token: String, quality: String): String {
        val payload = json.encodeToString(MediaRequestDto(mediaId, chapterId, token, quality))
        val body = payload.toRequestBody(JSON_MEDIA_TYPE)
        val request = buildApiPostRequest("$apiUrl/media", body)
        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw Exception("Empty media response")

            if (!response.isSuccessful) {
                response.close()
                if (response.code == 401 || response.code == 403 || response.code == 419) {
                    resetAuthCache()
                    throw Exception("Session expired while fetching media. Please login again.")
                }
                throw Exception("Unable to fetch media URL. (${response.code})")
            }

            val media = json.decodeFromString<MediaResponseDto>(responseBody)
            return appendPageFragment(media.data)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private data class ChapterMetadata(
        val id: Int,
        val isEarlyAccess: Boolean,
    )

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

    private inline fun <reified R> List<*>.firstInstanceOrNull(): R? =
        filterIsInstance<R>().firstOrNull()

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
            title = "Enable max quality"
            val baseSummary = "Asura+ Basic/Premium subscribers can request optimized max quality images. Requires authentication. Increases bandwidth by ~50%."
            summary = if (failedHighQuality) {
                "$baseSummary\n*DISABLED* because of missing max quality images."
            } else {
                baseSummary
            }
            setDefaultValue(false)
            setOnPreferenceChangeListener { _, _ ->
                failedHighQuality = false
                summary = baseSummary
                true
            }
        }.let(screen::addPreference)
    }

    private var SharedPreferences.slugMap: MutableMap<String, String>
        get() {
            val jsonMap = getString(PREF_SLUG_MAP, "{}")!!
            return try {
                json.decodeFromString<Map<String, String>>(jsonMap).toMutableMap()
            } catch (_: Exception) {
                mutableMapOf()
            }
        }
        set(newSlugMap) {
            edit()
                .putString(PREF_SLUG_MAP, json.encodeToString(newSlugMap))
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

    private fun String.unescape(): String {
        return UNESCAPE_REGEX.replace(this, "$1")
    }

    private fun handleSessionExpiry(response: Response): Nothing {
        resetAuthCache()
        response.close()
        throw Exception("Authentication failed. Please login again via WebView.")
    }

    private fun resetAuthCache() {
        cachedAuthState = null
        lastAuthCheck = 0L
    }

    private fun isAuthenticated(force: Boolean = false): Boolean {
        // Check if we have WebView cookies
        val hasWebViewCookies = runCatching {
            cookieManager.getCookie("https://$ASURA_MAIN_HOST")?.isNotEmpty() == true ||
                cookieManager.getCookie("https://$ASURA_API_HOST")?.isNotEmpty() == true
        }.getOrDefault(false)

        if (!hasWebViewCookies) {
            cachedAuthState = false
            lastAuthCheck = System.currentTimeMillis()
            return false
        }

        val now = System.currentTimeMillis()
        val cached = cachedAuthState
        if (!force && cached != null && now - lastAuthCheck < AUTH_CACHE_DURATION) {
            return cached
        }

        val request = GET("$apiUrl/user", headersBuilder().build())
        val isAuthed = runCatching { client.newCall(request).execute() }.getOrNull()?.use { resp ->
            if (!resp.isSuccessful) return@use false
            val body = resp.body?.string() ?: return@use false
            val root = runCatching { json.parseToJsonElement(body).jsonObject }.getOrNull() ?: return@use false
            root["data"] != null
        } ?: false

        cachedAuthState = isAuthed
        lastAuthCheck = now
        return isAuthed
    }

    companion object {
        private val UNESCAPE_REGEX = """\\(.)""".toRegex()
        private val PAGES_REGEX = """\\"pages\\":(\[.*?])""".toRegex()
        private val CLEAN_DATE_REGEX = """(\d+)(st|nd|rd|th)""".toRegex()
        private val OLD_FORMAT_MANGA_REGEX = """^/manga/(\d+-)?([^/]+)/?$""".toRegex()
        private val OLD_FORMAT_CHAPTER_REGEX = """^/(\d+-)?[^/]*-chapter-\d+(-\d+)*/?$""".toRegex()
        private val STANDARD_IMAGE_PATH_REGEX = """^/storage/media/(\d+)/([^/]+?)\.[^./]+$""".toRegex(RegexOption.IGNORE_CASE)
        private val CHAPTER_DATA_REGEX = """\\"chapter\\":\{\\"id\\":(\d+).*?\\"is_early_access\\":(true|false)""".toRegex(RegexOption.DOT_MATCHES_ALL)

        private const val ASURA_MAIN_HOST = "asuracomic.net"
        private const val ASURA_API_HOST = "gg.asuracomic.net"
        private const val AUTH_CACHE_DURATION = 60_000L
        private const val CHAPTER_DATA_TOKEN = """\"chapter\":"""
        private const val PREMIUM_AUTH_MESSAGE = "Premium chapter requires authentication. Login via WebView."
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
        private const val MEDIA_QUALITY_MAX = "max-quality"
        private const val HQ_ATTEMPT_HEADER = "X-Asura-HQ-Attempt"

        private const val PREF_SLUG_MAP = "pref_slug_map_2"
        private const val PREF_DYNAMIC_URL = "pref_dynamic_url"
        private const val PREF_HIDE_PREMIUM_CHAPTERS = "pref_hide_premium_chapters"
        private const val PREF_FORCE_HIGH_QUALITY = "pref_force_high_quality"
    }
}
