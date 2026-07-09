package eu.kanade.tachiyomi.extension.all.deviantart

import android.content.SharedPreferences
import android.text.InputType
import android.webkit.CookieManager
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

@Source
abstract class DeviantArt :
    HttpSource(),
    ConfigurableSource {
    override val supportsLatest = false

    private val preferences: SharedPreferences by getPreferencesLazy()

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0")
    }

    private val backendBaseUrl = "https://backend.deviantart.com"
    private fun backendBuilder() = backendBaseUrl.toHttpUrl().newBuilder()

    private val dateFormat by lazy {
        SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH)
    }

    // Use JavaNetCookieJar so OkHttp reads/writes cookies via Android's CookieManager.
    // This allows Set-Cookie response headers to be stored and sent back automatically,
    // supporting token rotation.
    private val cookieManager by lazy { CookieManager.getInstance() }

    override val client: OkHttpClient by lazy {
        network.client.newBuilder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .build()
    }

    // Seed cookies into CookieManager when the user enables login via the filter.
    private fun seedCookiesIfEnabled() {
        if (!preferences.cookieLoginEnabled) return
        val url = "https://$DOMAIN/"
        listOfNotNull(
            "auth" to preferences.authCookie,
            "auth_secure" to preferences.authSecureCookie,
            "userinfo" to preferences.userinfoCookie,
            "_px" to preferences.pxCookie,
            "_pxvid" to preferences.pxvidCookie,
            "pxcts" to preferences.pxctsCookie,
            "g_state" to preferences.gStateCookie,
            "td" to preferences.tdCookie,
        ).filter { it.second.isNotBlank() }.forEach { (key, value) ->
            cookieManager.setCookie(url, "$key=$value; Domain=$DOMAIN; Path=/")
        }
    }

    // ============================== Filter ==============================

    class CookieLoginFilter : Filter.CheckBox("Use cookie to login")

    override fun getFilterList(): FilterList = FilterList(CookieLoginFilter())

    // ============================== Popular / Search =====================

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException(SEARCH_FORMAT_MSG)

    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException(SEARCH_FORMAT_MSG)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host != baseUrl.toHttpUrl().host) {
                throw Exception("Unsupported url")
            }
            val username = url.pathSegments[0]
            val folderId = url.pathSegments[2]
            return super.fetchSearchManga(page, "gallery:$username/$folderId", filters)
        }
        // Plain username → list all galleries (with pagination)
        if (query.matches(Regex("""^[\w-]+$"""))) {
            return fetchUsernameGalleries(page, query)
        }
        return super.fetchSearchManga(page, query, filters)
    }

    private fun fetchUsernameGalleries(page: Int, username: String): Observable<MangasPage> = Observable.fromCallable {
        val url = "$baseUrl/$username/gallery${if (page > 1) "?page=$page" else ""}"

        val request = GET(url, headers)
        val response = client.newCall(request).execute()
        usernameGalleryParse(response, username, page)
    }

    // Parse the gallery listing page to extract all folder links
    private fun usernameGalleryParse(response: Response, username: String, page: Int = 1): MangasPage {
        val document = response.asJsoup()
        val lowered = username.lowercase()
        val mangas = mutableListOf<SManga>()
        val seen = mutableSetOf<String>()

        // Strategy: find ALL links that point to /{user}/gallery/{folderId}[/{slug}]
        // DeviantArt pages use absolute URLs like https://www.deviantart.com/user/gallery/id/slug
        // or relative /user/gallery/id/slug.  The "All" folder is /user/gallery/all (3 segments,
        // no separate folderId) and must be handled too.
        val allLinks = document.select("a[href*=/gallery/]")

        allLinks.forEach { link ->
            var href = link.attr("href").trim()
            if (href.startsWith("/")) href = "$baseUrl$href"

            val parsed = try {
                href.toHttpUrl()
            } catch (_: Exception) {
                return@forEach
            }
            val segments = parsed.pathSegments

            // Pattern: /{username}/gallery/{folderId}[/{slug}]
            //  3 segments: /user/gallery/all  (All folder)
            //  4 segments: /user/gallery/12345/featured
            if (segments.size < 3 || segments.size > 5) return@forEach
            if (segments[0].lowercase() != lowered) return@forEach
            if (segments[1] != "gallery") return@forEach

            // Skip pagination like /user/gallery?page=2
            val folderId = segments.getOrNull(2) ?: return@forEach
            if (folderId.isEmpty()) return@forEach

            val path = parsed.encodedPath // /username/gallery/12345/slug or /username/gallery/all

            if (!seen.add(path)) return@forEach

            // Name: prefer img alt / title attributes, fallback to link text
            val name = link.selectFirst("img[alt]")?.attr("alt")
                ?.takeIf { it.isNotBlank() }
                ?: link.selectFirst("[title]")?.attr("title")
                    ?.takeIf { it.isNotBlank() }
                ?: link.ownText().trim()
                    .takeIf { it.isNotBlank() }
                ?: link.text().trim()
                    .takeIf { it.isNotBlank() }
                ?: segments.last().replace("-", " ")

            val title = when {
                folderId == "all" -> "All"
                name.equals("all", ignoreCase = true) -> "All"
                segments.last() == "featured" -> name.ifBlank { "Featured" }
                else -> name
            }

            // Normalise: /username/gallery/12345/slug → username/gallery/12345/slug
            val url = path.removePrefix("/")

            mangas.add(
                SManga.create().apply {
                    this.url = url
                    this.title = title.ifBlank { folderId }
                    author = username
                    thumbnail_url = link.selectFirst("img")?.absUrl("src")
                },
            )
        }

        // If nothing was found and this is page 1, still expose "All"
        if (page <= 1 && mangas.isEmpty()) {
            mangas.add(
                createGalleryManga(username, "All", "$username/gallery/all"),
            )
        }

        // Ensure "All" is always present even when other folders were found.
        // Some DA pages don't link /gallery/all explicitly in the folder list.
        if (mangas.none { it.url.endsWith("/all") }) {
            mangas.add(0, createGalleryManga(username, "All", "$username/gallery/all"))
        }

        // Paginate: split into pages of at most 10 folders.
        // Page 1 skips the need for pagination if total count is manageable.
        val start = (page - 1).coerceAtLeast(0) * 10
        val end = minOf(start + 10, mangas.size)
        if (start >= mangas.size && page > 1) {
            return MangasPage(emptyList(), false)
        }
        val hasMore = end < mangas.size
        return MangasPage(mangas.subList(start, end), hasMore)
    }

    private fun createGalleryManga(username: String, title: String, url: String) = SManga.create().apply {
        this.url = url
        this.title = title
        author = username
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // Check cookie login filter
        val cookieEnabled = (filters.firstOrNull { it is CookieLoginFilter } as? CookieLoginFilter)?.state
            ?: preferences.cookieLoginEnabled
        preferences.edit().putBoolean(COOKIE_LOGIN_PREF, cookieEnabled).apply()
        if (cookieEnabled) seedCookiesIfEnabled()

        val matchGroups = requireNotNull(
            Regex("""gallery:([\w-]+)(?:/(\d+))?""").matchEntire(query)?.groupValues,
        ) { SEARCH_FORMAT_MSG }
        val username = matchGroups[1]
        val folderId = matchGroups[2].ifEmpty { "all" }
        return GET("$baseUrl/$username/gallery/$folderId", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val manga = mangaDetailsParse(response)
        return MangasPage(listOf(manga), false)
    }

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Details =============================

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val gallery = document.selectFirst("#sub-folder-gallery")

        // If manga is sub-gallery then use sub-gallery name, else use gallery name
        val galleryName = gallery?.selectFirst("._2vMZg + ._2vMZg")?.text()?.substringBeforeLast(" ")
            ?: gallery?.selectFirst("[aria-haspopup=listbox] > div")!!.ownText()
        val artistInTitle = (preferences.artistInTitle == ArtistInTitle.ALWAYS.name) ||
            ((preferences.artistInTitle == ArtistInTitle.ONLY_ALL_GALLERIES.name) && (galleryName == "All"))

        return SManga.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            author = document.title().substringBefore(" ")
            title = when {
                artistInTitle -> "$author - $galleryName"
                else -> galleryName
            }
            description = gallery?.selectFirst(".legacy-journal")?.wholeText()
            thumbnail_url = gallery?.selectFirst("img[property=contentUrl]")?.absUrl("src")
        }
    }

    // ============================== Chapters ============================

    override fun chapterListRequest(manga: SManga): Request {
        val pathSegments = getMangaUrl(manga).toHttpUrl().pathSegments
        val username = pathSegments[0]
        val query = when (val folderId = pathSegments[2]) {
            "all" -> "gallery:$username"
            else -> "gallery:$username/$folderId"
        }

        val url = backendBuilder()
            .addPathSegment("rss.xml")
            .addQueryParameter("q", query)
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoupXml()
        val chapterList = parseToChapterList(document).toMutableList()
        var nextUrl = document.selectFirst("[rel=next]")?.absUrl("href")

        while (nextUrl != null) {
            val newRequest = GET(nextUrl, headers)
            val newResponse = client.newCall(newRequest).execute()
            val newDocument = newResponse.asJsoupXml()
            val newChapterList = parseToChapterList(newDocument)
            chapterList.addAll(newChapterList)

            nextUrl = newDocument.selectFirst("[rel=next]")?.absUrl("href")
        }

        return chapterList.also(::orderChapterList).toList()
    }

    private fun parseToChapterList(document: Document): List<SChapter> = document.select("item").map {
        SChapter.create().apply {
            setUrlWithoutDomain(it.selectFirst("link")!!.text())
            name = it.selectFirst("title")!!.text()
            date_upload = dateFormat.tryParse(it.selectFirst("pubDate")?.text())
            scanlator = it.selectFirst("media|credit")?.text()
        }
    }

    private fun orderChapterList(chapterList: MutableList<SChapter>) {
        if (chapterList.first().date_upload < chapterList.last().date_upload) {
            chapterList.reverse()
        }
        chapterList.forEachIndexed { i, chapter ->
            chapter.chapter_number = chapterList.size - i.toFloat()
        }
    }

    // ============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val buttons = document.selectFirst("[draggable=false]")?.children()
        return if (buttons == null) {
            // Single-image deviation: keep the large display URL as-is
            val imageUrl = document.selectFirst("img[fetchpriority=high]")?.absUrl("src")
            listOf(Page(0, imageUrl = imageUrl))
        } else {
            // Multi-image: use the thumbnail URL to get a large version.
            // When the token is image.operations (common for page 1) we keep the
            // /v1/fit/ transform but scale up the dimensions.  When it's a raw PNG
            // with a file.download token (common for page 2+) we strip /v1/ entirely.
            buttons.mapIndexed { i, button ->
                val thumbSrc = button.selectFirst("img")?.absUrl("src")
                val imageUrl = resolveFullImageUrl(thumbSrc)
                Page(i, imageUrl = imageUrl)
            }
        }
    }

    // Tokens embedded in the gallery page thumbnails are either "image.operations"
    // (has /v1/fit/ — scale up inside fit) or "file.download" (no transform — strip /v1/).
    private fun resolveFullImageUrl(src: String?): String? {
        if (src == null) return null
        return if (src.contains("/v1/fit/")) {
            src.replace(
                Regex("""/v1/fit/w_\d+,h_\d+,q_\d+"""),
                "/v1/fit/w_1600,h_1600,q_80",
            )
        } else {
            // No /v1/fit/ — probably a raw PNG with a file.download token
            src.replaceFirst(Regex("""/v1(/.*)?(?=\?)"""), "")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // WixMP CDN checks Referer to prevent hotlinking; must impersonate a page visit
    override fun imageRequest(page: Page): Request {
        val referer = "$baseUrl/"
        return GET(page.imageUrl!!, headersBuilder().add("Referer", referer).build())
    }

    private fun Response.asJsoupXml(): Document = Jsoup.parse(body.string(), request.url.toString(), Parser.xmlParser())

    // ============================== Preferences =========================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        // Artist in title preference
        val artistInTitlePref = ListPreference(screen.context).apply {
            key = ArtistInTitle.PREF_KEY
            title = "Artist name in manga title"
            entries = ArtistInTitle.values().map { it.text }.toTypedArray()
            entryValues = ArtistInTitle.values().map { it.name }.toTypedArray()
            summary = "Current: %s\n\n" +
                "Changing this preference will not automatically apply to manga in Library " +
                "and History, so refresh all DeviantArt manga and/or clear database in Settings " +
                "> Advanced after doing so."
            setDefaultValue(ArtistInTitle.defaultValue.name)
        }
        screen.addPreference(artistInTitlePref)

        // Cookie-based authentication section.
        // Cookies are stored here but NOT applied automatically — the user must
        // open the Filter pane and check "Use cookie to login" to activate them.
        // This allows CookieManager to manage cookies (including key rotation from
        // Set-Cookie response headers) instead of injecting raw header values.
        EditTextPreference(screen.context).apply {
            key = COOKIE_PREF_AUTH
            title = "auth cookie"
            summary = "The 'auth' cookie value from deviantart.com"
            setDefaultValue("")
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = COOKIE_PREF_AUTH_SECURE
            title = "auth_secure cookie"
            summary = "The 'auth_secure' cookie value from deviantart.com"
            setDefaultValue("")
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = COOKIE_PREF_USERINFO
            title = "userinfo cookie"
            summary = "The 'userinfo' cookie value from deviantart.com"
            setDefaultValue("")
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = COOKIE_PREF_PX
            title = "_px cookie"
            summary = "The '_px' cookie (PerimeterX/DDoS protection)"
            setDefaultValue("")
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = COOKIE_PREF_PXVID
            title = "_pxvid cookie"
            summary = "The '_pxvid' cookie (PerimeterX visitor ID)"
            setDefaultValue("")
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = COOKIE_PREF_PXCTS
            title = "pxcts cookie"
            summary = "The 'pxcts' cookie (PerimeterX token)"
            setDefaultValue("")
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = COOKIE_PREF_GSTATE
            title = "g_state cookie"
            summary = "The 'g_state' cookie (Google sign-in state)"
            setDefaultValue("")
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.also(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = COOKIE_PREF_TD
            title = "td cookie"
            summary = "The 'td' cookie (device/screen info)"
            setDefaultValue("")
            setOnBindEditTextListener {
                it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            }
        }.also(screen::addPreference)
    }

    private enum class ArtistInTitle(val text: String) {
        NEVER("Never"),
        ALWAYS("Always"),
        ONLY_ALL_GALLERIES("Only in \"All\" galleries"),
        ;

        companion object {
            const val PREF_KEY = "artistInTitlePref"
            val defaultValue = ONLY_ALL_GALLERIES
        }
    }

    // ============================== SharedPrefs accessors ===============

    private val SharedPreferences.artistInTitle
        get() = getString(ArtistInTitle.PREF_KEY, ArtistInTitle.defaultValue.name)

    val SharedPreferences.cookieLoginEnabled
        get() = getBoolean(COOKIE_LOGIN_PREF, false)

    private val SharedPreferences.authCookie
        get() = getString(COOKIE_PREF_AUTH, "") ?: ""

    private val SharedPreferences.authSecureCookie
        get() = getString(COOKIE_PREF_AUTH_SECURE, "") ?: ""

    private val SharedPreferences.userinfoCookie
        get() = getString(COOKIE_PREF_USERINFO, "") ?: ""

    private val SharedPreferences.pxCookie
        get() = getString(COOKIE_PREF_PX, "") ?: ""

    private val SharedPreferences.pxvidCookie
        get() = getString(COOKIE_PREF_PXVID, "") ?: ""

    private val SharedPreferences.pxctsCookie
        get() = getString(COOKIE_PREF_PXCTS, "") ?: ""

    private val SharedPreferences.gStateCookie
        get() = getString(COOKIE_PREF_GSTATE, "") ?: ""

    private val SharedPreferences.tdCookie
        get() = getString(COOKIE_PREF_TD, "") ?: ""

    companion object {
        private const val SEARCH_FORMAT_MSG = "Please enter a query in the format of gallery:{username} or gallery:{username}/{folderId}"
        const val DOMAIN = "deviantart.com"

        // Cookie preference keys
        private const val COOKIE_PREF_AUTH = "cookie_auth"
        private const val COOKIE_PREF_AUTH_SECURE = "cookie_auth_secure"
        private const val COOKIE_PREF_USERINFO = "cookie_userinfo"
        private const val COOKIE_PREF_PX = "cookie_px"
        private const val COOKIE_PREF_PXVID = "cookie_pxvid"
        private const val COOKIE_PREF_PXCTS = "cookie_pxcts"
        private const val COOKIE_PREF_GSTATE = "cookie_gstate"
        private const val COOKIE_PREF_TD = "cookie_td"
        private const val COOKIE_LOGIN_PREF = "cookie_login_enabled"
    }
}

// Bridges Android's CookieManager (which OkHttp doesn't use natively) into OkHttp's
// CookieJar interface.  This means:
// - Outgoing requests pick up cookies stored by CookieManager.
// - Incoming Set-Cookie headers are persisted into CookieManager automatically.
// Used so that cookie-based login (including token rotation) works end-to-end.
private class JavaNetCookieJar(private val cookieManager: CookieManager) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cookie ->
            cookieManager.setCookie(url.toString(), cookie.toString())
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cookieHeader = cookieManager.getCookie(url.toString()) ?: return emptyList()
        return cookieHeader.split("; ").mapNotNull { Cookie.parse(url, it) }
    }
}
