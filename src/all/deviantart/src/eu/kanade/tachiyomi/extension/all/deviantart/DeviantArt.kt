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

// DeviantArt source for Mihon.
//
// = Search
//
//   gallery:{username}[/{folderId}]    Browse a single gallery folder
//   {plain-username}                   List all gallery folders for a user
//   https://www.deviantart.com/...     Paste a full gallery URL
//
// = Login (cookie-based)
//
//   1. Fill in cookie values in Settings (auth, auth_secure, userinfo, …)
//   2. Open Filter → check "Use cookie to login" → Apply
//   3. The extension seeds Android's CookieManager, which OkHttp picks up
//      via JavaNetCookieJar.
//
//   DeviantArt uses Cloudflare + PerimeterX so WebView login doesn't work;
//   cookies must be copied from a browser session.

@Source
abstract class DeviantArt :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = false
    private val preferences: SharedPreferences by getPreferencesLazy()

    // ── HTTP client ──────────────────────────────────────────────────────

    override fun headersBuilder() = Headers.Builder().apply {
        add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:133.0) Gecko/20100101 Firefox/133.0")
    }

    private val cookieManager by lazy { CookieManager.getInstance() }

    override val client: OkHttpClient by lazy {
        network.client.newBuilder()
            .cookieJar(JavaNetCookieJar(cookieManager))
            .build()
    }

    // ── Cookie login ─────────────────────────────────────────────────────

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
        ).filter { it.second.isNotBlank() }.forEach { (k, v) ->
            cookieManager.setCookie(url, "$k=$v; Domain=$DOMAIN; Path=/")
        }
    }

    // ── Filter ───────────────────────────────────────────────────────────

    class CookieLoginFilter : Filter.CheckBox("Use cookie to login")

    override fun getFilterList() = FilterList(CookieLoginFilter())

    // ══════════════════════════════════════════════════════════════════════
    // SEARCH
    // ══════════════════════════════════════════════════════════════════════

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException(SEARCH_HINT)
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException(SEARCH_HINT)

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        // Paste a full URL
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host != baseUrl.toHttpUrl().host) throw Exception("Unsupported URL")
            val username = url.pathSegments[0]
            val folderId = url.pathSegments[2]
            return super.fetchSearchManga(page, "gallery:$username/$folderId", filters)
        }
        // Plain username → list gallery folders
        if (query.matches(USERNAME_RE)) return fetchUsernameGalleries(page, query)
        return super.fetchSearchManga(page, query, filters)
    }

    // ── Username → folder list ──────────────────────────────────────────

    private fun fetchUsernameGalleries(page: Int, username: String) = Observable.fromCallable {
        val url = "$baseUrl/$username/gallery${if (page > 1) "?page=$page" else ""}"
        val response = client.newCall(GET(url, headers)).execute()
        parseFolderList(response, username, page)
    }

    private fun parseFolderList(response: Response, username: String, page: Int): MangasPage {
        val doc = response.asJsoup()
        val lowered = username.lowercase()
        val folders = mutableListOf<SManga>()
        val seen = mutableSetOf<String>()

        doc.select("a[href*=/gallery/]").forEach { link ->
            var href = link.attr("href").trim()
            if (href.startsWith("/")) href = "$baseUrl$href"
            val parsed = try {
                href.toHttpUrl()
            } catch (_: Exception) {
                return@forEach
            }
            val segs = parsed.pathSegments
            if (segs.size < 3 || segs.size > 5) return@forEach
            if (segs[0].lowercase() != lowered) return@forEach
            if (segs[1] != "gallery") return@forEach
            val folderId = segs.getOrNull(2) ?: return@forEach
            if (folderId.isEmpty()) return@forEach
            val path = parsed.encodedPath
            if (!seen.add(path)) return@forEach

            val name = link.selectFirst("img[alt]")?.attr("alt")?.takeIf { it.isNotBlank() }
                ?: link.selectFirst("[title]")?.attr("title")?.takeIf { it.isNotBlank() }
                ?: link.ownText().trim().takeIf { it.isNotBlank() }
                ?: link.text().trim().takeIf { it.isNotBlank() }
                ?: segs.last().replace("-", " ")

            val title = when {
                folderId == "all" || name.equals("all", ignoreCase = true) -> "All"
                segs.last() == "featured" -> name.ifBlank { "Featured" }
                else -> name
            }

            folders += createGalleryManga(username, title, path)
        }

        if (page <= 1 && folders.isEmpty()) {
            folders += createGalleryManga(username, "All", "/$username/gallery/all")
        }
        if (folders.none { it.url.endsWith("/all") }) {
            folders.add(0, createGalleryManga(username, "All", "/$username/gallery/all"))
        }

        val start = (page - 1) * 10
        val end = minOf(start + 10, folders.size)
        if (start >= folders.size && page > 1) return MangasPage(emptyList(), false)
        return MangasPage(folders.subList(start, end), end < folders.size)
    }

    private fun createGalleryManga(user: String, title: String, url: String) = SManga.create().apply {
        this.url = url
        this.title = title
        author = user
    }

    // ── Search → single gallery ────────────────────────────────────────

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val cookieEnabled = (filters.firstOrNull { it is CookieLoginFilter } as? CookieLoginFilter)?.state
            ?: preferences.cookieLoginEnabled
        preferences.edit().putBoolean(COOKIE_LOGIN_PREF, cookieEnabled).apply()
        if (cookieEnabled) seedCookiesIfEnabled()

        val (username, folderId) = requireNotNull(
            GALLERY_RE.matchEntire(query)?.destructured,
        ) { SEARCH_HINT }
        return GET("$baseUrl/$username/gallery/${folderId.ifEmpty { "all" }}", headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val manga = mangaDetailsParse(response)
        return MangasPage(listOf(manga), false)
    }

    // ══════════════════════════════════════════════════════════════════════
    // MANGA DETAILS
    // ══════════════════════════════════════════════════════════════════════

    override fun mangaDetailsParse(response: Response): SManga {
        val doc = response.asJsoup()
        val gallery = doc.selectFirst("#sub-folder-gallery")
        val galleryName = gallery?.selectFirst("._2vMZg + ._2vMZg")?.text()?.substringBeforeLast(" ")
            ?: gallery?.selectFirst("[aria-haspopup=listbox] > div")!!.ownText()
        val artistInTitle = (preferences.artistInTitle == ArtistInTitle.ALWAYS.name) ||
            ((preferences.artistInTitle == ArtistInTitle.ONLY_ALL_GALLERIES.name) && galleryName == "All")

        return SManga.create().apply {
            setUrlWithoutDomain(response.request.url.toString())
            author = doc.title().substringBefore(" ")
            title = if (artistInTitle) "$author - $galleryName" else galleryName
            description = gallery?.selectFirst(".legacy-journal")?.wholeText()
            thumbnail_url = gallery?.selectFirst("img[property=contentUrl]")?.absUrl("src")
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // CHAPTERS (RSS feed)
    // ══════════════════════════════════════════════════════════════════════

    private val backendBaseUrl = "https://backend.deviantart.com"
    private fun backendBuilder() = backendBaseUrl.toHttpUrl().newBuilder()
    private val dateFormat by lazy { SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.ENGLISH) }

    override fun chapterListRequest(manga: SManga): Request {
        val segs = getMangaUrl(manga).toHttpUrl().pathSegments
        val username = segs[0]
        val query = if (segs[2] == "all") {
            "gallery:$username"
        } else {
            "gallery:$username/${segs[2]}"
        }
        return GET(
            backendBuilder().addPathSegment("rss.xml").addQueryParameter("q", query).build(),
            headers,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val doc = response.asJsoupXml()
        val chapters = parseChapters(doc).toMutableList()
        var nextUrl = doc.selectFirst("[rel=next]")?.absUrl("href")
        while (nextUrl != null) {
            val r = client.newCall(GET(nextUrl, headers)).execute()
            val rDoc = r.asJsoupXml()
            r.close() // close the response body to avoid leaks
            chapters += parseChapters(rDoc)
            nextUrl = rDoc.selectFirst("[rel=next]")?.absUrl("href")
        }
        return chapters.also(::orderChapters)
    }

    private fun parseChapters(doc: Document) = doc.select("item").map {
        SChapter.create().apply {
            setUrlWithoutDomain(it.selectFirst("link")!!.text())
            name = it.selectFirst("title")!!.text()
            date_upload = dateFormat.tryParse(it.selectFirst("pubDate")?.text())
            scanlator = it.selectFirst("media|credit")?.text()
        }
    }

    private fun orderChapters(list: MutableList<SChapter>) {
        if (list.first().date_upload < list.last().date_upload) list.reverse()
        list.forEachIndexed { i, ch -> ch.chapter_number = list.size - i.toFloat() }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PAGES
    // ══════════════════════════════════════════════════════════════════════

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        val buttons = doc.selectFirst("[draggable=false]")?.children()
        return if (buttons == null) {
            // Single-image: keep the large display URL as-is
            listOf(Page(0, imageUrl = doc.selectFirst("img[fetchpriority=high]")?.absUrl("src")))
        } else {
            // Multi-image: fetch each file page individually so we get the
            // per-file large display URL that DeviantArt renders for that image.
            buttons.mapIndexed { i, button ->
                val fileUrl = button.attr("abs:href").ifBlank {
                    button.selectFirst("a")?.attr("abs:href").orEmpty()
                }.ifBlank {
                    "${response.request.url}?file=${i + 1}"
                }
                Page(i, url = fileUrl)
            }
        }
    }

    // Fetch ?file=N page → extract the large img src.
    // file=2+ pages may not have fetchpriority=high; fall back to wixmp images.
    override fun imageUrlParse(response: Response): String {
        val doc = response.asJsoup()
        return doc.selectFirst("img[fetchpriority=high]")?.absUrl("src")
            ?: doc.select("img[src*=wixmp]").firstOrNull()?.absUrl("src")
            ?: doc.select("img[src]").firstOrNull()?.absUrl("src")
            ?: throw Exception("No image found on ${response.request.url}")
    }

    // Download with Referer to satisfy WixMP CDN hotlink protection
    override fun imageRequest(page: Page): Request {
        val referer = "$baseUrl/"
        return GET(page.imageUrl!!, headersBuilder().add("Referer", referer).build())
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private fun Response.asJsoupXml(): Document = Jsoup.parse(body.string(), request.url.toString(), Parser.xmlParser())

    // ══════════════════════════════════════════════════════════════════════
    // PREFERENCES
    // ══════════════════════════════════════════════════════════════════════

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        screen.addPreference(
            ListPreference(screen.context).apply {
                key = ArtistInTitle.PREF_KEY
                title = "Artist name in manga title"
                entries = ArtistInTitle.values().map { it.text }.toTypedArray()
                entryValues = ArtistInTitle.values().map { it.name }.toTypedArray()
                summary = "Current: %s\n\n" +
                    "Changing this preference will not automatically apply to manga in " +
                    "Library and History. Refresh DeviantArt manga and/or clear database " +
                    "in Settings > Advanced after changing."
                setDefaultValue(ArtistInTitle.defaultValue.name)
            },
        )

        // Cookie fields — fill in values from browser dev tools, then enable
        // login via Filter → "Use cookie to login"
        for ((key, title, summary) in COOKIE_FIELDS) {
            screen.addPreference(
                EditTextPreference(screen.context).apply {
                    this.key = key
                    this.title = title
                    this.summary = summary
                    setDefaultValue("")
                    setOnBindEditTextListener {
                        it.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                    }
                },
            )
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    // DATA
    // ══════════════════════════════════════════════════════════════════════

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

    // ── SharedPreferences accessors ──────────────────────────────────────

    private val SharedPreferences.artistInTitle get() = getString(ArtistInTitle.PREF_KEY, ArtistInTitle.defaultValue.name)
    val SharedPreferences.cookieLoginEnabled get() = getBoolean(COOKIE_LOGIN_PREF, false)
    private val SharedPreferences.authCookie get() = getString(COOKIE_AUTH, "") ?: ""
    private val SharedPreferences.authSecureCookie get() = getString(COOKIE_AUTH_SECURE, "") ?: ""
    private val SharedPreferences.userinfoCookie get() = getString(COOKIE_USERINFO, "") ?: ""
    private val SharedPreferences.pxCookie get() = getString(COOKIE_PX, "") ?: ""
    private val SharedPreferences.pxvidCookie get() = getString(COOKIE_PXVID, "") ?: ""
    private val SharedPreferences.pxctsCookie get() = getString(COOKIE_PXCTS, "") ?: ""
    private val SharedPreferences.gStateCookie get() = getString(COOKIE_GSTATE, "") ?: ""
    private val SharedPreferences.tdCookie get() = getString(COOKIE_TD, "") ?: ""

    companion object {
        const val DOMAIN = "deviantart.com"
        private const val SEARCH_HINT = "Use: gallery:{username}[/{folderId}] or a plain username, or paste a URL"
        private val USERNAME_RE = Regex("""^[\w-]+$""")
        private val GALLERY_RE = Regex("""gallery:([\w-]+)(?:/(\d+))?""")

        private const val COOKIE_LOGIN_PREF = "cookie_login_enabled"
        private const val COOKIE_AUTH = "cookie_auth"
        private const val COOKIE_AUTH_SECURE = "cookie_auth_secure"
        private const val COOKIE_USERINFO = "cookie_userinfo"
        private const val COOKIE_PX = "cookie_px"
        private const val COOKIE_PXVID = "cookie_pxvid"
        private const val COOKIE_PXCTS = "cookie_pxcts"
        private const val COOKIE_GSTATE = "cookie_gstate"
        private const val COOKIE_TD = "cookie_td"

        private val COOKIE_FIELDS = listOf(
            Triple(COOKIE_AUTH, "auth cookie", "Required for logged-in browsing"),
            Triple(COOKIE_AUTH_SECURE, "auth_secure cookie", "Required for logged-in browsing"),
            Triple(COOKIE_USERINFO, "userinfo cookie", "Required for logged-in browsing"),
            Triple(COOKIE_PX, "_px cookie", "Optional: PerimeterX / DDoS protection"),
            Triple(COOKIE_PXVID, "_pxvid cookie", "Optional: PerimeterX visitor ID"),
            Triple(COOKIE_PXCTS, "pxcts cookie", "Optional: PerimeterX token"),
            Triple(COOKIE_GSTATE, "g_state cookie", "Optional: Google sign-in state"),
            Triple(COOKIE_TD, "td cookie", "Optional: device / screen info"),
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════
// JavaNetCookieJar
// ═════════════════════════════════════════════════════════════════════════
// Bridges Android's CookieManager into OkHttp so that Set-Cookie responses
// are persisted and sent back automatically (enabling token rotation).

private class JavaNetCookieJar(private val cm: CookieManager) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = cm.getCookie(url.toString()) ?: return emptyList()
        return header.split("; ").mapNotNull { Cookie.parse(url, it) }
    }
}
