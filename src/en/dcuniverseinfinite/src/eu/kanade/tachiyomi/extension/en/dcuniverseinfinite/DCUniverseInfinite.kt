package eu.kanade.tachiyomi.extension.en.dcuniverseinfinite

import android.app.Application
import android.util.Base64
import android.webkit.CookieManager
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class DCUniverseInfinite : HttpSource(), ConfigurableSource {

    override val name = "DC Universe Infinite"
    override val baseUrl = "https://www.dcuniverseinfinite.com"
    override val lang = "en"
    override val supportsLatest = true

    private val apiUrl = "$baseUrl/api"

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private fun manualCookieString(): String {
        val session = preferences.getString(PREF_SESSION, "").orEmpty().trim()
        val psm = preferences.getString(PREF_PSM, "").orEmpty().trim()
        return buildList {
            if (session.isNotEmpty()) add("session=$session")
            if (psm.isNotEmpty()) add("psmSessionId=$psm")
        }.joinToString("; ")
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }
    private val dateFormatDay = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if ((response.code == 401 || response.code == 403) &&
                request.url.host == apiUrl.toHttpUrl().host &&
                request.url.pathSegments.any { it == "rights" || it == "download" }
            ) {
                response.close()
                throw IOException(LOGIN_MESSAGE)
            }
            response
        }
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            if (!request.url.host.endsWith("dcuniverseinfinite.com")) {
                return@addNetworkInterceptor chain.proceed(request)
            }
            // AndroidCookieJar can hand OkHttp an empty Cookie set even when the
            // WebView jar is populated. Always rebuild the Cookie header straight
            // from CookieManager for both apex and www hosts.
            val cm = CookieManager.getInstance()
            // Manual cookies last so a pasted, authenticated session/psmSessionId
            // overrides the anonymous values the in-app WebView produces.
            val merged = mergeCookies(
                request.header("Cookie"),
                cm.getCookie("https://dcuniverseinfinite.com"),
                cm.getCookie("https://www.dcuniverseinfinite.com"),
                manualCookieString(),
            )
            val finalRequest = if (merged.isBlank()) {
                request
            } else {
                request.newBuilder().header("Cookie", merged).build()
            }
            chain.proceed(finalRequest)
        }
        .build()

    private fun mergeCookies(vararg sources: String?): String {
        val jar = LinkedHashMap<String, String>()
        sources.forEach { src ->
            src?.split(";")?.forEach { pair ->
                val t = pair.trim()
                val i = t.indexOf('=')
                if (i > 0) jar[t.substring(0, i)] = t.substring(i + 1)
            }
        }
        return jar.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .set("Referer", "$baseUrl/")
        .set("Origin", baseUrl)
        .set("x-consumer-key", CONSUMER_KEY)

    // Popular

    override fun popularMangaRequest(page: Int): Request =
        searchRequest(page, null, "first_released", "desc")

    override fun popularMangaParse(response: Response): MangasPage = parseSeriesList(response)

    // Latest

    override fun latestUpdatesRequest(page: Int): Request =
        searchRequest(page, null, "first_released", "desc")

    override fun latestUpdatesParse(response: Response): MangasPage = parseSeriesList(response)

    // Search

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val sort = filters.firstInstanceOrNull<SortFilter>()?.selected ?: SORTS[0]
        return searchRequest(page, query.ifBlank { null }, sort.field, sort.direction, filters)
    }

    override fun searchMangaParse(response: Response): MangasPage = parseSeriesList(response)

    private fun searchRequest(
        page: Int,
        query: String?,
        sortField: String,
        sortDirection: String,
        filters: FilterList = FilterList(),
    ): Request {
        val seriesFilters = buildString {
            append("{")
            val era = filters.firstInstanceOrNull<EraFilter>()?.selectedValue
            if (!era.isNullOrEmpty()) {
                append(""""eras":${era.jsonStr()}""")
            }
            append("}")
        }
        val body = buildString {
            append("{")
            append(""""page":$page,""")
            append(""""per_page":$PER_PAGE,""")
            append(""""document_types":["comicseries"],""")
            if (!query.isNullOrBlank()) {
                append(""""q":${query.jsonStr()},""")
            }
            append(""""filters":{"comicseries":$seriesFilters},""")
            append(""""sort_field":{"comicseries":${sortField.jsonStr()}},""")
            append(""""sort_direction":{"comicseries":${sortDirection.jsonStr()}},""")
            append(""""apply_transform":true""")
            append("}")
        }
        return POST(
            "$apiUrl/search_proxy/1/search",
            headers,
            body.toRequestBody(JSON_MEDIA_TYPE),
        )
    }

    private fun parseSeriesList(response: Response): MangasPage {
        val result = response.parseAs<SearchResponseDto>()
        val mangas = result.items.map { it.toSManga() }
        val info = result.pageInfo
        val hasNextPage = (info?.current_page ?: 1) < (info?.num_pages ?: 1)
        return MangasPage(mangas, hasNextPage)
    }

    private fun RecordDto.toSManga(): SManga = SManga.create().apply {
        val s = slug ?: "comic"
        setUrlWithoutDomain("/comics/series/$s/$uuid")
        title = this@toSManga.title.orEmpty()
        description = this@toSManga.description
        thumbnail_url = base_asset_url?.let { "$it/box.jpg?w=600&auto=format,compress" }
    }

    // Details

    override fun mangaDetailsRequest(manga: SManga): Request {
        val uuid = (baseUrl + manga.url).toHttpUrl().pathSegments.last()
        return GET("$apiUrl/comics/1/series/$uuid/?trans=en", headers)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun mangaDetailsParse(response: Response): SManga {
        val series = response.parseAs<SeriesDto>()
        return SManga.create().apply {
            title = series.title
            description = series.description
            thumbnail_url = series.base_asset_url?.let { "$it/box.jpg?w=600&auto=format,compress" }
            genre = series.tags
                .filter { it.categories.none { c -> c.equals("Data Driven", true) } }
                .joinToString { it.name }
            author = series.tags.firstOrNull { it.categories.any { c -> c.equals("Creators", true) } }?.name
            status = SManga.UNKNOWN
            initialized = true
        }
    }

    // Chapters

    override fun chapterListRequest(manga: SManga): Request {
        val seriesUuid = (baseUrl + manga.url).toHttpUrl().pathSegments.last()
        return seriesBooksRequest(seriesUuid, 1)
    }

    private fun seriesBooksRequest(seriesUuid: String, page: Int): Request =
        GET("$apiUrl/comics/1/series/$seriesUuid/books/issue?page=$page&trans=en", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        var result = response.parseAs<SeriesBooksDto>()
        val segments = response.request.url.pathSegments
        val seriesUuid = segments.getOrNull(segments.indexOf("series") + 1)
        val books = result.values.toMutableList()
        var page = 1
        while (seriesUuid != null && page < result.num_pages) {
            page++
            result = client.newCall(seriesBooksRequest(seriesUuid, page)).execute()
                .parseAs<SeriesBooksDto>()
            if (result.values.isEmpty()) break
            books.addAll(result.values)
        }
        return books.mapIndexed { index, book -> book.toSChapter(index) }.reversed()
    }

    private fun SeriesBookDto.toSChapter(index: Int): SChapter = SChapter.create().apply {
        val s = slug ?: "comic"
        setUrlWithoutDomain("/comics/book/$s/$uuid")
        name = title ?: issue_number?.let { "Issue #$it" } ?: "Issue"
        chapter_number = issue_number?.toFloatOrNull() ?: (index + 1).toFloat()
        date_upload = dateFormatDay.tryParse(publish_date ?: release_date)
    }

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl${chapter.url}/c/reader"

    // Pages

    override fun pageListRequest(chapter: SChapter): Request {
        // PSM auth: the durable psmSessionId cookie (set by WebView login) must be
        // exchanged for a short-lived `session` cookie via set_cookie on every read,
        // exactly as the site's JS does. Without this the rights endpoint returns
        // user_guid=null / can_read=false even when psmSessionId is present.
        runCatching {
            client.newCall(
                POST("$apiUrl/users/set_cookie?trans=en", headers, "".toRequestBody()),
            ).execute().close()
        }
        val uuid = (baseUrl + chapter.url).toHttpUrl().pathSegments.last()
        return GET("$apiUrl/5/1/rights/comic/$uuid?trans=en", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val raw = response.body.string().trim()
        if (response.code != 200) {
            throw IOException("Rights check failed (HTTP ${response.code}). Open WebView, sign in, and retry.")
        }
        val jwt = raw.removeSurrounding("\"")
        val rights = decodeJwtPayload(jwt).parseAs<RightsDto>()
        if (!rights.rights.can_read) {
            val who = if (rights.user_guid.isNullOrBlank()) {
                "Signed out. Komikku's WebView can't complete DC's SSO login. " +
                    "Open this source's settings and paste your 'session' (and " +
                    "ideally 'psmSessionId') cookie value from a desktop browser " +
                    "where you're logged in. See the settings screen for steps."
            } else {
                "Signed in, but this issue isn't readable on your account (it likely needs an active subscription)."
            }
            throw IOException(who)
        }

        val pageHeaders = headersBuilder()
            .set("x-auth-jwt", jwt)
            .build()

        val pages = mutableListOf<Page>()
        var pageNum = 1
        var numPages = 1
        while (pageNum <= numPages) {
            val request = GET(
                "$apiUrl/comics/1/book/download/?page=$pageNum&quality=SD&trans=en",
                pageHeaders,
            )
            val manifest = client.newCall(request).execute().parseAs<DownloadDto>()
            numPages = manifest.num_pages
            manifest.images.forEach { img ->
                val url = img.thumbnail_url ?: return@forEach
                pages.add(Page(pages.size, imageUrl = url))
            }
            pageNum++
        }
        return pages
    }

    override fun imageRequest(page: Page): Request {
        val imageHeaders = super.headersBuilder()
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, imageHeaders)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Filters

    override fun getFilterList(): FilterList = FilterList(
        SortFilter(),
        EraFilter(),
    )

    private class SortOption(val name: String, val field: String, val direction: String)

    private class SortFilter : Filter.Select<String>("Sort by", SORTS.map { it.name }.toTypedArray()) {
        val selected: SortOption get() = SORTS[state]
    }

    private class EraFilter : Filter.Select<String>("Era", ERAS.map { it.first }.toTypedArray()) {
        val selectedValue: String get() = ERAS[state].second
    }

    // Settings

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val intro = "Komikku's in-app WebView cannot complete DC Universe Infinite's " +
            "login (it uses Warner Bros. federated SSO), so the session it creates is " +
            "signed-out. Work around it by pasting the cookie value from a desktop " +
            "browser where you ARE logged in:\n" +
            "1. Log in at dcuniverseinfinite.com in Chrome/Firefox\n" +
            "2. DevTools (F12) -> Application -> Cookies -> https://www.dcuniverseinfinite.com\n" +
            "3. Copy the Value of the row, paste it below (value only, not the name)\n\n" +
            "The session value expires roughly daily; if reading stops working, " +
            "paste a fresh one. Pasting psmSessionId too lets the extension refresh " +
            "the session automatically and last much longer."

        EditTextPreference(screen.context).apply {
            key = PREF_SESSION
            title = "session cookie value"
            summary = intro
            dialogTitle = "Paste 'session' value"
            setDefaultValue("")
            screen.addPreference(this)
        }
        EditTextPreference(screen.context).apply {
            key = PREF_PSM
            title = "psmSessionId cookie value (optional)"
            summary = "Optional but recommended: paste the 'psmSessionId' value too " +
                "so the session auto-refreshes and you rarely need to re-paste."
            dialogTitle = "Paste 'psmSessionId' value"
            setDefaultValue("")
            screen.addPreference(this)
        }
    }

    // Helpers

    private fun decodeJwtPayload(jwt: String): String {
        val payload = jwt.split(".").getOrNull(1)
            ?: throw IOException("Invalid rights token")
        return String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP))
    }

    private fun String.jsonStr(): String = buildString {
        append('"')
        this@jsonStr.forEach { c ->
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }

    companion object {
        private const val CONSUMER_KEY = "DA59dtVXYLxajktV"
        private const val PER_PAGE = 20
        private val JSON_MEDIA_TYPE = "application/json;charset=UTF-8".toMediaType()
        private const val LOGIN_MESSAGE =
            "Log in via WebView with an active subscription to read this issue."
        private const val PREF_SESSION = "session_cookie"
        private const val PREF_PSM = "psm_session_id"

        private val SORTS = listOf(
            SortOption("Newest", "first_released", "desc"),
            SortOption("Oldest", "first_released", "asc"),
            SortOption("Title A-Z", "title", "asc"),
            SortOption("Title Z-A", "title", "desc"),
        )

        private val ERAS = listOf(
            "All" to "",
            "Golden Age" to "golden-age",
            "Silver Age" to "silver-age",
            "Bronze Age" to "bronze-age",
            "Modern Age" to "modern-age",
            "New 52" to "new-52",
            "Rebirth" to "rebirth",
        )
    }
}
