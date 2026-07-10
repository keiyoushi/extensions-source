package eu.kanade.tachiyomi.extension.all.deviantart

import android.content.SharedPreferences
import android.webkit.CookieManager
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
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.parser.Parser
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

// Search: gallery:{username}[/{folderId}] | {username} | full gallery URL
// Cookie login: paste browser-exported cookies.json in Filter, then enable the checkbox.
// DeviantArt uses Cloudflare + PerimeterX so WebView login doesn't work.

@Source
abstract class DeviantArt :
    HttpSource(),
    ConfigurableSource {

    override val supportsLatest = false
    private val preferences: SharedPreferences by getPreferencesLazy()

    // ── HTTP client ──────────────────────────────────────────────────────

    // Strip "wv" from User-Agent so Google login (used by DeviantArt) works.
    // Google denies login when the User-Agent contains the WebView token.
    override fun headersBuilder() = super.headersBuilder()
        .apply {
            build()["user-agent"]?.let { userAgent ->
                set("user-agent", removeWebViewToken(userAgent))
            }
        }

    private fun removeWebViewToken(userAgent: String): String = userAgent.replace(WEBVIEW_TOKEN_REGEX, ")")

    private val cookieManager by lazy { CookieManager.getInstance() }

    override val client: OkHttpClient by lazy {
        network.client.newBuilder()
            .cookieJar(WebViewCookieJar(cookieManager))
            .build()
    }

    // ── Cookie login ─────────────────────────────────────────────────────

    private fun seedCookies(json: String) {
        val cookies = parseCookieJson(json)
        val url = "https://$DOMAIN/"
        cookies.forEach { (k, v) ->
            cookieManager.setCookie(url, "$k=$v; Domain=$DOMAIN; Path=/")
        }
    }

    // Parse browser-exported cookies.json into name-value pairs.
    // Uses org.json for robust parsing; keeps only cookies whose domain
    // matches deviantart.com (or a subdomain) and defaults path to "/".
    private fun parseCookieJson(raw: String): List<Pair<String, String>> {
        val result = mutableListOf<Pair<String, String>>()
        val trimmed = raw.trim()
        val jsonArray = if (trimmed.startsWith("[")) {
            JSONArray(trimmed)
        } else {
            // Some exports wrap the array inside an object or have leading text.
            // Try to find the first '[' … last ']' as a fallback.
            val start = trimmed.indexOf('[')
            val end = trimmed.lastIndexOf(']')
            if (start == -1 || end <= start) return result
            JSONArray(trimmed.substring(start, end + 1))
        }
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val domain = obj.optString("domain", "")
            // The standard export field is "domain" (leading dot), but some
            // tools use "Domain" (capitalised).
            val domainClean = domain.removePrefix(".")
            if (!isDeviantArtDomain(domainClean)) continue
            val name = obj.optString("name", "")
            val value = obj.optString("value", "")
            if (name.isEmpty()) continue
            result += name to value
        }
        return result
    }

    private fun isDeviantArtDomain(domain: String): Boolean = domain == DOMAIN || domain.endsWith(".$DOMAIN")

    // ── Filter ───────────────────────────────────────────────────────────

    class CookieJsonFilter(value: String = "") :
        Filter.Text(
            "Cookies (JSON)\nFirst, login to your DeviantArt account in a real browser,\n" +
                "Then paste the cookies.json array exported from that browser.",
            value,
        )

    class CookieLoginFilter(state: Boolean = false) :
        Filter.CheckBox(
            "Use cookie to login\nEnable after pasting cookies above. Use this cookie only once." +
                "Warning: Cookie import equals session hijacking risk; treat like a password.",
            state,
        )

    override fun getFilterList(): FilterList = FilterList(
        CookieJsonFilter(),
        CookieLoginFilter(),
    )

    override fun popularMangaRequest(page: Int): Request = throw UnsupportedOperationException(SEARCH_HINT)
    override fun popularMangaParse(response: Response): MangasPage = throw UnsupportedOperationException(SEARCH_HINT)

    override fun latestUpdatesRequest(page: Int): Request = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            if (url.host != baseUrl.toHttpUrl().host) throw Exception("Unsupported URL")
            val username = url.pathSegments[0]
            val folderId = url.pathSegments.lastOrNull { it.all(Char::isDigit) }
                ?: url.pathSegments.getOrNull(2).orEmpty()
            // Preserve sub-gallery slug through to mangaDetailsParse
            // e.g. /username7/gallery/12345678/halloween-2024 → slug=halloween-2024
            val slug = url.pathSegments.lastOrNull()
                ?.takeUnless { it.all(Char::isDigit) || it == "all" || it == "featured" || it == "gallery" }
            val q = if (slug != null) "sub:$username/$folderId/$slug" else "gallery:$username/$folderId"
            return super.fetchSearchManga(page, q, filters)
        }
        // Plain username → list gallery folders
        if (query.matches(USERNAME_RE)) return fetchUsernameGalleries(page, query)
        return super.fetchSearchManga(page, query, filters)
    }

    // ── Username → folder list ──────────────────────────────────────────

    private fun fetchUsernameGalleries(page: Int, username: String) = Observable.fromCallable {
        val url = "$baseUrl/$username/gallery${if (page > 1) "?page=$page" else ""}"
        val response = client.newCall(GET(url, headers)).execute()
        response.use { parseFolderList(it, username, page) }
    }

    private fun parseFolderList(response: Response, username: String, page: Int): MangasPage {
        val doc = response.asJsoup()
        val realUser = resolveUsername(doc, username)
        val lowered = realUser.lowercase()
        val folders = mutableListOf<SManga>()
        val seen = mutableSetOf<String>()

        doc.select("a[href*=/gallery/]").forEach { link ->
            val href = link.absUrl("href")
            val parsed = try {
                href.toHttpUrl()
            } catch (_: Exception) {
                return@forEach
            }
            val segs = parsed.pathSegments
            if (segs.size < 3 || segs.size > 6) return@forEach
            if (segs[0].lowercase() != lowered) return@forEach
            if (segs[1] != "gallery") return@forEach
            val folderId = segs.getOrNull(2) ?: return@forEach
            if (folderId.isEmpty()) return@forEach
            val path = parsed.encodedPath
            if (!seen.add(path)) return@forEach

            val name = link.ownText().takeIf { it.isNotBlank() }
                ?: link.selectFirst("[title]")?.attr("title")?.takeIf { it.isNotBlank() }
                ?: segs.last().replace("-", " ")

            val isAll = folderId == "all" || name.equals("all", ignoreCase = true)
            val title = when {
                isAll -> "All"
                segs.last() == "featured" -> name.ifBlank { "Featured" }
                else -> name
            }
            val displayTitle = if (
                preferences.artistInTitle == ArtistInTitle.ALWAYS.name ||
                (preferences.artistInTitle == ArtistInTitle.ONLY_ALL_GALLERIES.name && isAll)
            ) {
                "$realUser - $title"
            } else {
                title
            }

            folders += createGalleryManga(realUser, displayTitle, path)
        }

        if (page <= 1 && folders.isEmpty()) {
            folders += createGalleryManga(realUser, "All", "/$realUser/gallery/all")
        }
        if (folders.none { it.url.endsWith("/all") }) {
            folders.add(0, createGalleryManga(realUser, "All", "/$realUser/gallery/all"))
        }

        val start = (page - 1) * 10
        val end = minOf(start + 10, folders.size)
        if (start >= folders.size && page > 1) return MangasPage(emptyList(), false)
        return MangasPage(folders.subList(start, end), end < folders.size)
    }

    private fun resolveUsername(doc: Document, fallback: String): String {
        val loweredFallback = fallback.lowercase()
        // Try h1 header containing a link with the correct display name
        doc.selectFirst("h1 a[href*=\"/$loweredFallback\"]")?.ownText()?.takeIf { it.isNotBlank() }?.let { return it }
        // Try any link whose href matches and whose display text equals the href username (lowercase)
        doc.select("a[href*=\"/$loweredFallback\"]").firstOrNull { link ->
            link.ownText().lowercase() == loweredFallback
        }?.ownText()?.takeIf { it.isNotBlank() }?.let { return it }
        // Fallback: use the text of any user-specific link whose text matches
        doc.select("a.user-link, a[href*=\"/$loweredFallback\"][href*=user]").firstOrNull { link ->
            link.text().equals(fallback, ignoreCase = true)
        }?.text()?.takeIf { it.isNotBlank() }?.let { return it }
        // Last resort: extract the lowercase username from the document's own URL.
        // DeviantArt URLs always use the canonical lowercase username as the first
        // path segment, e.g. /username7/gallery/...
        val docLocation = doc.location()
        if (docLocation.isNotBlank()) {
            try {
                val pathSeg = docLocation.toHttpUrl().pathSegments.getOrNull(0)
                if (!pathSeg.isNullOrBlank()) return pathSeg.lowercase()
            } catch (_: Exception) {}
        }
        return fallback
    }

    private fun createGalleryManga(user: String, title: String, url: String) = SManga.create().apply {
        this.url = url
        this.title = title
        author = user
    }

    // ── Search → single gallery ────────────────────────────────────────

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        // If the user enabled cookie login, seed cookies from the JSON text
        // filter, then clear both the text and checkbox so they only apply once.
        val loginFilter = filters.firstOrNull { it is CookieLoginFilter } as? CookieLoginFilter
        val jsonFilter = filters.firstOrNull { it is CookieJsonFilter } as? CookieJsonFilter
        if (loginFilter != null && loginFilter.state && jsonFilter != null && jsonFilter.state.isNotBlank()) {
            seedCookies(jsonFilter.state)
            loginFilter.state = false
            jsonFilter.state = ""
        }

        // sub: prefix preserves the slug for mangaDetailsParse fallback
        // e.g. sub:username7/98917131/halloween-2024
        val sub = SUB_RE.matchEntire(query)
        if (sub != null) {
            val (username, folderId, slug) = sub.destructured
            return GET("$baseUrl/$username/gallery/$folderId/$slug", headers)
        }
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
        val requestUrl = response.request.url
        val rawName = gallery?.selectFirst("[aria-haspopup=listbox] > div")?.ownText()
            ?.replace(Regex("^.+'s\\s"), "")
            ?: gallery?.selectFirst("._2vMZg + ._2vMZg")?.text()?.substringBeforeLast(" ")
            ?: gallery?.selectFirst("> div:nth-child(1) > div:nth-child(3)")?.text()?.substringBeforeLast(" ")
            ?: requestUrl.pathSegments.lastOrNull()
                ?.takeUnless { it.all(Char::isDigit) || it == "all" || it == "featured" }
            ?: throw Exception("Could not find gallery name")
        val galleryName = rawName.replace(Regex("\\s*-\\s*front page\\s*$", RegexOption.IGNORE_CASE), "")
        val artistInTitle = (preferences.artistInTitle == ArtistInTitle.ALWAYS.name) ||
            ((preferences.artistInTitle == ArtistInTitle.ONLY_ALL_GALLERIES.name) && galleryName == "All")

        return SManga.create().apply {
            setUrlWithoutDomain(requestUrl.toString())
            author = resolveUsername(doc, requestUrl.pathSegments[0])
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
            val rDoc = r.use { it.asJsoupXml() }
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
        list.forEachIndexed { i, ch -> ch.chapter_number = list.size - i.toFloat() }
    }

    // ══════════════════════════════════════════════════════════════════════
    // PAGES
    // ══════════════════════════════════════════════════════════════════════

    override fun pageListParse(response: Response): List<Page> {
        val doc = response.asJsoup()
        val buttons = doc.selectFirst("[draggable=false]")?.children()
        return if (buttons == null) {
            listOf(Page(0, imageUrl = doc.selectFirst("img[fetchpriority=high]")?.absUrl("src")))
        } else {
            extractMultiImageUrls(doc, response.request.url.toString())
        }
    }

    private fun extractMultiImageUrls(doc: Document, pageUrl: String): List<Page> {
        val script = doc.select("script").firstOrNull {
            it.data().contains("__INITIAL_STATE__")
        }?.data() ?: return legacyMultiImagePages(doc, pageUrl)

        // Extract the JSON string passed to JSON.parse() — use non-greedy
        // matching (.+?) so multiple calls in the same script don't overlap.
        val jsonStr = Regex("""JSON\.parse\("(.+?)"\)""").find(script)?.groupValues?.getOrNull(1)
            ?.replace("\\\"", "\"")
            ?.replace("\\\\", "\\")
            ?: return legacyMultiImagePages(doc, pageUrl)

        // Prefer decoding the __INITIAL_STATE__ JSON object when available.
        val pages = tryParseInitialStateImages(jsonStr, pageUrl)
            ?: extractImageUrlsFallback(jsonStr)
        return pages.ifEmpty { legacyMultiImagePages(doc, pageUrl) }
    }

    // Attempt to parse the decoded string as JSON and walk known paths for image URLs.
    private fun tryParseInitialStateImages(json: String, pageUrl: String): List<Page>? {
        return try {
            val root = JSONObject(json)
            val entities = root.optJSONObject("@@entities") ?: return null
            // DeviantArt nests deviation objects keyed by deviationId; each may
            // carry a media token with token[], each holding a url.
            val deviationIds = entities.names() ?: return null
            val urls = mutableListOf<String>()
            for (i in 0 until deviationIds.length()) {
                val entity = entities.optJSONObject(deviationIds.getString(i)) ?: continue
                val media = entity.optJSONObject("media") ?: continue
                val tokens = media.optJSONArray("token") ?: continue
                for (j in 0 until tokens.length()) {
                    val url = tokens.optString(j, "")
                    if (url.startsWith("https://images-wixmp-")) urls += url
                }
            }
            if (urls.isEmpty()) return null
            urls.mapIndexed { idx, url -> Page(idx, imageUrl = upgradeWixmpUrl(url)) }
        } catch (_: Exception) {
            null
        }
    }

    // Fallback: regex-scan the decoded JSON string for WixMP image URLs.
    private fun extractImageUrlsFallback(json: String): List<Page> {
        val urls = Regex("""https://images-wixmp-[^"\\]+""").findAll(json)
        return urls.mapIndexed { i, match ->
            Page(i, imageUrl = upgradeWixmpUrl(match.value))
        }.toList()
    }

    private fun upgradeWixmpUrl(url: String): String = if (url.contains("/v1/fit/")) {
        url.replace(Regex("""/v1/fit/w_\d+,h_\d+,q_\d+"""), "/v1/fit/w_1600,h_1600,q_80")
    } else {
        url.replaceFirst(Regex("""/v1(/.*)?(?=\?)"""), "")
    }

    private fun legacyMultiImagePages(doc: Document, pageUrl: String): List<Page> {
        val buttons = doc.selectFirst("[draggable=false]")?.children() ?: return emptyList()
        return buttons.mapIndexed { i, button ->
            val fileUrl = button.attr("abs:href").ifBlank {
                "$pageUrl?file=${i + 1}"
            }
            Page(i, url = fileUrl)
        }
    }

    override fun imageUrlParse(response: Response): String {
        val doc = response.asJsoup()
        return doc.selectFirst("img[fetchpriority=high]")?.absUrl("src")
            ?: doc.select("img[src*=wixmp]").firstOrNull()?.absUrl("src")
            ?: throw Exception("No image found on ${response.request.url}")
    }

    // WixMP CDN requires Referer header
    override fun imageRequest(page: Page): Request {
        val referer = "$baseUrl/"
        return GET(page.imageUrl!!, headersBuilder().add("Referer", referer).build())
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    // Use peekBody so the Response body is not consumed — the caller
    // (or the framework) retains ownership and can close the Response.
    private fun Response.asJsoupXml(): Document = Jsoup.parse(
        peekBody(Long.MAX_VALUE).string(),
        request.url.toString(),
        Parser.xmlParser(),
    )

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

    companion object {
        const val DOMAIN = "deviantart.com"
        private const val SEARCH_HINT = "Search by gallery:{username} or gallery:{username}/{folderId}, or an username, or paste gallery URL\nUse filter to login."
        private val USERNAME_RE = Regex("""^[\w-]+$""")
        private val GALLERY_RE = Regex("""gallery:([\w-]+)(?:/(\d+))?""")
        private val SUB_RE = Regex("""sub:([\w-]+)/(\d+)/(.+)""")
        private val WEBVIEW_TOKEN_REGEX = Regex(""";\s*wv\)""")
    }
}

// Bridges Android CookieManager into OkHttp so Set-Cookie responses persist.

private class WebViewCookieJar(private val cm: CookieManager) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { cm.setCookie(url.toString(), it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val header = cm.getCookie(url.toString()) ?: return emptyList()
        return header.split("; ").mapNotNull { Cookie.parse(url, it) }
    }
}
