package eu.kanade.tachiyomi.extension.fr.japscan

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.ListPreference
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
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.string
import keiyoushi.utils.tryParse
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.ByteArrayInputStream
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.mapIndexed
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Japscan :
    HttpSource(),
    ConfigurableSource {

    // Sometimes an adblock blocker will pop up, preventing the user from opening
    // a cloudflare protected page
    private val internalBaseUrl = "https://www.japscan.foo"

    override val supportsLatest = true

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client: OkHttpClient = network.client.newBuilder()
        // Pages from fetchPageList are decoded blobs cached on disk; their imageUrl is
        // `https://japscan-cache.local/<absolute-cache-path>`. We can't override
        // fetchImage (final), so an interceptor catches that sentinel host and serves
        // the file bytes synthetically.
        //
        // This must short-circuit *before* rateLimit: serving a local file is not a
        // network request, and letting it fall through would throttle page loads to one
        // per 2s. Files are left on disk (the reader may request the same page again,
        // e.g. on retry) and reaped by sweepPageCache on the next fetchPageList.
        .addInterceptor { chain ->
            val req = chain.request()
            if (req.url.host != JAPSCAN_CACHE_HOST) return@addInterceptor chain.proceed(req)
            val path = "/" + req.url.pathSegments.joinToString("/")
            val bytes = runCatching { File(path).readBytes() }.getOrNull()
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(if (bytes != null) 200 else 404)
                .message(if (bytes != null) "OK" else "Not Found")
                .body((bytes ?: ByteArray(0)).toResponseBody("image/jpeg".toMediaType()))
                .build()
        }
        // rateLimit returns a RateLimitBuilder; it must come last as all other
        // OkHttpClient.Builder configuration has to happen before it.
        .rateLimit(1, 2.seconds)
        .build()

    private val captchaRegex = """window\.__captcha\s*=\s*\{\s*needed\s*:\s*true\s*,?""".toRegex()

    companion object {
        private val CHAPTER_PATH_TYPES = setOf("manga", "manhua", "manhwa", "bd", "comic")
        private val HIDDEN_STYLE_TOKENS = listOf(
            "display:none",
            "visibility:hidden",
            "visibility:collapse",
            "content-visibility:hidden",
            "pointer-events:none",
            "clip-path:inset(100%",
            "clip-path:circle(0",
            "clip-path:ellipse(0",
            "clip-path:polygon(0,0,0,0",
            "clip:rect(0,0,0,0",
            "font-size:0",
            "line-height:0",
            "text-indent:-",
        )

        // Match styles that visually remove an element while leaving it in the DOM:
        //  - fully transparent via `opacity` or `filter:opacity()`
        //  - collapsed via `width` / `height` / `max-width` / `max-height`
        //  - large absolute offset (3+ digits) via top/bottom/left/right or `inset:` shorthand
        //  - `transform: translate / translateX / translateY / translated` with a 3+ digit offset
        //  - `transform: scale(0)` / `scale3d(0,...)` (collapsed to nothing)
        //  - `transform: matrix(0,0,0,0,...)` (also collapsed)
        //
        // 3 digits is enough to be off-screen even with viewport units (200vh, 999vw, …)
        // while still tolerating fine adjustments like top:-1px or right:99px.
        //
        // The zero-value alternatives are anchored on both sides, because a substring
        // match would also fire on values that are perfectly visible and very common on
        // real rows: `opacity:0.9`, `filter:opacity(0.5)`, `min-width:0`. A false positive
        // here drops a real chapter, so these must not match loosely. Matched against the
        // inline style with spaces stripped, hence `;` as the left boundary.
        private val HIDDEN_STYLE_REGEX = Regex(
            """(?:^|;)opacity:0(?![.\d])""" +
                """|filter:opacity\(0(?![.\d])""" +
                """|(?:^|;)(?:width|height):0(?![.\d])""" +
                """|max-(?:width|height):0(?![.\d])""" +
                """|(?:top|bottom|left|right|inset):-?\d{3,}""" +
                """|transform:translate(?:3d|x|y)?\([^)]*-?\d{3,}""" +
                """|transform:scale(?:3d|x|y)?\(0[,)]""" +
                """|transform:matrix\(0,0,0,0""",
        )
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

        // "Chapitre 1100.5: Title" -> 1100.5
        private val CHAPTER_NUM_REGEX = Regex("""(?i)chapitre\s+([\d.]+)""")

        private const val SHOW_SPOILER_CHAPTERS_TITLE = "Les chapitres en Anglais ou non traduit sont upload en tant que \" Spoilers \" sur Japscan"
        private const val SHOW_SPOILER_CHAPTERS = "JAPSCAN_SPOILER_CHAPTERS"

        // Sentinel host used to route page-image requests to a local cache file via
        // an OkHttp interceptor (see `client` builder).
        private const val JAPSCAN_CACHE_HOST = "japscan-cache.local"

        // Prefix for the spooled page files in the app cache dir, shared by the writer
        // (JsInterface.savePage) and the reaper (sweepPageCache).
        private const val CACHE_FILE_PREFIX = "japscan-"

        // How long fetchPageList waits with zero saved pages before deciding the
        // WebView driver is wedged rather than merely slow.
        private const val IDLE_TIMEOUT_MS = 45_000L

        // Synthetic viewport used to force-layout the detached WebView. Must stay
        // desktop-class: under ~1280 px wide the reader takes a "mobile" path that
        // materializes one tile-host per long page and lazy-loads the rest on scroll,
        // which a detached WebView can't reliably trigger. Above it, every tile-host
        // is created upfront.
        private const val WEBVIEW_VIEWPORT_WIDTH = 1920
        private const val WEBVIEW_VIEWPORT_HEIGHT = 16384

        // Desktop Chrome UA matching what the descrambler's working code path
        // expects (the source's own headersBuilder UA is Android-mobile flavored,
        // which makes the descrambler take a one-tile-per-page mobile path).
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"

        // The reader page pulls in rotating ad/tracker hosts (acscdn.com, jnbhi.com,
        // usrpubtrk.com, … — they change every few weeks) whose vignette and popunder
        // modals break the detached descrambler. A blocklist would rot, so allowlist
        // the handful of origins the reader actually needs and drop everything else.
        private val ALLOWED_HOSTS = listOf(
            "japscan.foo",
            "cdnjs.cloudflare.com",
            "code.jquery.com",
            "cdn.jsdelivr.net",
            "fonts.googleapis.com",
            "fonts.gstatic.com",
            "challenges.cloudflare.com",
        )
        private val prefsEntries = arrayOf("Montrer uniquement les chapitres traduit en Français", "Montrer les chapitres spoiler")
        private val prefsEntryValues = arrayOf("hide", "show")

        // Verbose tracing of the WebView drivers. Off by default; flip to true when the
        // reader breaks and the capture needs to be followed in Logcat.
        private const val DEBUG = false

        private fun debugLog(message: String) {
            if (DEBUG) Log.d("Japscan", message)
        }
    }

    private fun chapterListPref() = preferences.getString(SHOW_SPOILER_CHAPTERS, "hide")

    override fun headersBuilder() = super.headersBuilder()
        .add("referer", "$internalBaseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$internalBaseUrl/mangas/?sort=popular&p=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val manga = document.select(".mangas-list .manga-block:not(:has(a[href='']))").map { element ->
            SManga.create().apply {
                element.select("a").first()!!.let {
                    setUrlWithoutDomain(it.attr("href"))
                    title = it.text()
                    thumbnail_url = it.selectFirst("img")?.attr("abs:data-src")
                }
            }
        }
        val hasNextPage = document.selectFirst(".pagination > li:last-child:not(.disabled)") != null
        return MangasPage(manga, hasNextPage)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$internalBaseUrl/mangas/?sort=updated&p=$page", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isEmpty()) {
            val url = internalBaseUrl.toHttpUrl().newBuilder().apply {
                addPathSegment("mangas")

                filters.forEach { filter ->
                    when (filter) {
                        is TextField -> addPathSegment(((page - 1) + filter.state.toInt()).toString())
                        is PageList -> addPathSegment(((page - 1) + filter.values[filter.state]).toString())
                        else -> {}
                    }
                }
            }.build()

            return GET(url, headers)
        } else {
            val formBody = FormBody.Builder()
                .add("search", query)
                .build()
            val searchHeaders = headers.newBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .build()

            return POST("$internalBaseUrl/ls/", searchHeaders, formBody)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.first() == "ls") {
            val jsonResult = response.parseAs<JsonArray>()

            val mangaList = jsonResult.map { jsonEl -> searchMangaFromJson(jsonEl.jsonObject) }

            return MangasPage(mangaList, hasNextPage = false)
        }

        val baseUrlHost = internalBaseUrl.toHttpUrl().host
        val document = response.asJsoup()
        val manga = document
            .select("div.card div.p-2")
            .filter {
                // Filter out ads masquerading as search results
                it.select("p a").attr("abs:href").toHttpUrl().host == baseUrlHost
            }
            .map { element ->
                SManga.create().apply {
                    thumbnail_url = element.select("img").attr("abs:src")
                    element.select("p a").let {
                        title = it.text()
                        url = it.attr("href")
                    }
                }
            }
        val hasNextPage = document.selectFirst(".mangas-list .manga-block:not(:has(a[href='']))") != null

        return MangasPage(manga, hasNextPage)
    }

    private fun searchMangaFromJson(jsonObj: JsonObject): SManga = SManga.create().apply {
        url = jsonObj["url"]!!.string
        title = jsonObj["name"]!!.string
        thumbnail_url = internalBaseUrl + jsonObj["image"]!!.string
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(internalBaseUrl + manga.url, headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val infoElement = document.selectFirst("#main .card-body")!!
        val manga = SManga.create()

        manga.thumbnail_url = infoElement.selectFirst("img")?.attr("abs:src")

        val infoRows = infoElement.select(".row, .d-flex")
        infoRows.select("p").forEach { el ->
            when (el.select("span").text().trim()) {
                "Auteur(s):" -> manga.author = el.text().replace("Auteur(s):", "").trim()

                "Artiste(s):" -> manga.artist = el.text().replace("Artiste(s):", "").trim()

                "Genre(s):" -> manga.genre = el.text().replace("Genre(s):", "").trim()

                "Statut:" -> manga.status = el.text().replace("Statut:", "").trim().let {
                    parseStatus(it)
                }
            }
        }
        manga.description = infoElement.selectFirst("div:contains(Synopsis) + p")?.ownText().orEmpty()

        return manga
    }

    private fun parseStatus(status: String) = status.lowercase().let {
        when {
            it.contains("en cours") -> SManga.ONGOING
            it.contains("terminé") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = internalBaseUrl + chapter.url

    override fun chapterListRequest(manga: SManga): Request = GET(internalBaseUrl + manga.url, headers)

    private fun chapterListSelector() = "#list_chapters > div.collapse > div.list_chapters" +
        if (chapterListPref() == "hide") {
            ":not(:has(.badge:contains(SPOILER),.badge:contains(RAW),.badge:contains(VUS)))"
        } else {
            ""
        }
    // JapScan sometimes uploads some "spoiler preview" chapters, containing 2 or 3 untranslated pictures taken from a raw. Sometimes they also upload full RAWs/US versions and replace them with a translation as soon as available.
    // Those have a span.badge "SPOILER" or "RAW". The additional pseudo selector makes sure to exclude these from the chapter list.

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaSlug = extractMangaSlug(response.request.url)
        val chapters = document.select(chapterListSelector()).mapNotNull { el ->
            runCatching { parseChapter(el, mangaSlug) }.getOrNull()
        }
        return filterOutlierChapters(chapters)
    }

    // Backstop for a honeypot that clears the slug/number binding in parseChapter: its
    // number comes out wildly out of range (observed: 483181 among real 1174..1181). Real
    // chapter numbers never sit orders of magnitude above the median, so cap on that.
    // Low-side honeypots (000001..000008) are already rejected by the leading-zero check
    // on the URL id, so this only needs to cap the top.
    //
    // Compare chapter_number, NOT the trailing URL id: Japscan writes "Chapitre 1100.5" as
    // /11005/, which would read as a 10x outlier. By chapter number it is 0.5 from 1100.
    private fun filterOutlierChapters(chapters: List<SChapter>): List<SChapter> {
        val nums = chapters.map { it.chapter_number }.filter { it >= 0f }.sorted()
        if (nums.size < 3) return chapters
        val ceiling = nums[nums.size / 2] * 10 + 100
        return chapters.filter { it.chapter_number <= ceiling }
    }

    private fun extractMangaSlug(url: HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val typeIdx = segments.indexOfFirst { it in CHAPTER_PATH_TYPES }
        if (typeIdx == -1 || typeIdx + 1 >= segments.size) return null
        return segments[typeIdx + 1].takeIf { it.isNotEmpty() }
    }

    private fun isHidden(el: Element): Boolean {
        if (el.hasClass("d-none")) return true
        if (el.hasAttr("hidden")) return true
        if (el.attr("aria-hidden").equals("true", ignoreCase = true)) return true
        val style = el.attr("style").replace(" ", "").lowercase()
        if (HIDDEN_STYLE_TOKENS.any { style.contains(it) }) return true
        if (HIDDEN_STYLE_REGEX.containsMatchIn(style)) return true
        return false
    }

    private fun isHiddenWithin(el: Element, root: Element): Boolean {
        var cur: Element? = el
        while (cur != null && cur !== root) {
            if (isHidden(cur)) return true
            cur = cur.parent()
        }
        return false
    }

    private fun parseChapter(element: Element, mangaSlug: String?): SChapter {
        // Only search for a tag with any attribute containing manga/manhua/manhwa.
        // Skip elements that are visually hidden — Japscan hides honeypots with
        // class="d-none", inline display/visibility/opacity:0, zero size, or by
        // positioning them way off-screen. The visible chapter row never carries
        // any of these, so to evade detection Japscan would have to make the
        // honeypots visible to humans too.
        val allUrlPairs = (element.getElementsContainingText("Chapitre") + element.getElementsContainingText("Volume"))
            .filterNot { isHiddenWithin(it, element) }
            .mapNotNull { el ->
                // Find the first attribute whose value matches the chapter URL pattern
                val attrMatch = el.attributes().asList().firstOrNull { attr ->
                    CHAPTER_PATH_TYPES.any { attr.value.startsWith("/$it/") }
                }
                attrMatch?.let { Pair(el.ownText().ifBlank { el.text() }, it.value) }
            }
            .distinctBy { it.second }

        // Filter out anti-scraping honeypots by binding name, slug and URL number together:
        // a real chapter URL is /<type>/<mangaSlug>/<chapterNum>/, with the same slug as the
        // manga page and a chapter number that appears in the chapter's name ("Chapitre N: ...").
        // Stripping non-digits from the name handles half-chapters like "Chapitre 1100.5" + /11005/.
        // Honeypots use a different slug (e.g. /manga/cv/N/) with sequential numbers that match a
        // fake "Chapitre N" label, so name/number alone is not enough — slug check is what stops them.
        val filtered = allUrlPairs
            .filter { (name, url) ->
                val segments = url.split('/').filter { it.isNotEmpty() }
                if (segments.size != 3) return@filter false
                if (segments[0] !in CHAPTER_PATH_TYPES) return@filter false
                if (mangaSlug != null && segments[1] != mangaSlug) return@filter false
                val urlNum = url.trimEnd('/').substringAfterLast('/')
                if (!urlNum.all { it.isDigit() }) return@filter false
                if (urlNum.length > 1 && urlNum.startsWith('0')) return@filter false
                val chapterNum = CHAPTER_NUM_REGEX.find(name)
                    ?.groupValues?.get(1)?.replace(".", "")
                    ?: name.split(Regex("[^0-9.]+")).lastOrNull { it.isNotEmpty() }?.replace(".", "")
                    ?: return@filter false
                chapterNum == urlNum
            }

        // Fall back to the unfiltered list in case the heuristics are too aggressive. There
        // we prefer the longest URL — real slugs (e.g. "one-piece") are usually longer than
        // honeypot slugs (e.g. "cv"). The binding above admits at most one URL per row, so
        // nothing needs ordering on the normal path.
        val foundPair = filtered.firstOrNull()
            ?: allUrlPairs.maxByOrNull { it.second.length }
            ?: throw Exception("Impossible de trouver l'URL du chapitre")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(foundPair.second)
        chapter.name = foundPair.first
        // Half-chapters ("Chapitre 1100.5") keep their .5 here, unlike the URL id which
        // flattens to /11005/. filterOutlierChapters relies on that.
        chapter.chapter_number = CHAPTER_NUM_REGEX.find(chapter.name)
            ?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
        chapter.date_upload = element.selectFirst("span")?.text()?.let { parseChapterDate(it) } ?: 0L
        return chapter
    }

    private fun parseChapterDate(date: String) = dateFormat.tryParse(date)

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val interfaceName = randomString()
        val context = Injekt.get<Application>()
        sweepPageCache(context.cacheDir)
        val isReader = Exception().stackTrace.any { it.className.contains("reader") }

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch, context.cacheDir)
        var webView: WebView? = null
        var request: Response = client.newCall(GET("$internalBaseUrl${chapter.url}", headers)).execute()
        var pageContent = request.body.string()
        val matchResult = captchaRegex.find(pageContent)

        if (matchResult != null) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(context, "eu.kanade.tachiyomi.ui.webview.WebViewActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("url_key", "$internalBaseUrl${chapter.url}")
                    putExtra("source_key", id)
                    putExtra("title_key", "Résolvez le captcha, fermez la Webview et réouvrez le chapitre.")
                }

                context.startActivity(intent)
            } catch (_: Exception) {
                // Suwayomi etc.
                throw Exception("Résolvez le captcha de ce chapitre depuis la WebView et réouvrez le chapitre.")
            }
            var captchaWait = 0
            while (captchaWait < 15) {
                Thread.sleep(5000)
                request = client.newCall(GET("$internalBaseUrl${chapter.url}", headers)).execute()
                pageContent = request.body.string()
                val isGood = captchaRegex.find(pageContent)
                if (isGood == null) {
                    val closeIntent = Intent().apply {
                        val targetClass = if (isReader) {
                            "eu.kanade.tachiyomi.ui.reader.ReaderActivity"
                        } else {
                            "eu.kanade.tachiyomi.ui.main.MainActivity"
                        }
                        component = ComponentName(context, targetClass)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(closeIntent)
                    break
                } else {
                    captchaWait++
                }
            }
            if (captchaWait >= 15) {
                throw Exception("Résolvez le captcha, fermez la Webview et réouvrez le chapitre.")
            }
        }

        // Pick the reader driver from the chapter URL's first path segment. Japscan
        // shapes every chapter URL as `/<type>/<slug>/<num>/` (the `CHAPTER_PATH_TYPES`
        // set above curates the valid types), and the type maps cleanly to a reader
        // format: `manhwa` / `manhua` are vertical/long-strip (webtoon); `manga`,
        // `bd`, `comic` are paginated.
        //
        // We do NOT detect from the server-rendered HTML: the reader DOM
        // (`#full-reader` / `#single-reader` and the `d-none` toggle) is mounted
        // client-side by the reader JS, so Jsoup on the initial HTML returns
        // "paginated" for every chapter — including manhwa.
        //
        // The two drivers need incompatible JS hooks: paginated uses a minimal
        // `URL.createObjectURL` capture and would be tripped by the shadow-DOM /
        // drawImage / RAF / viewport overrides the webtoon driver needs, so we
        // MUST commit to one driver before any hook is installed.
        val urlSegment = chapter.url.trimStart('/').substringBefore('/').lowercase()
        val isWebtoon = urlSegment == "manhwa" || urlSegment == "manhua"
        debugLog("[wv-kt] reader mode hint from URL segment='$urlSegment' → ${if (isWebtoon) "webtoon" else "paginated"}")

        handler.post {
            val innerWv = WebView(context)

            webView = innerWv
            innerWv.settings.domStorageEnabled = true
            innerWv.settings.javaScriptEnabled = true
            // The reader fetches scrambled tiles and reassembles them onto canvases;
            // images must be allowed to load for the descrambling to run.
            innerWv.settings.blockNetworkImage = false
            // Keep the WebSettings UA matched to the rest of mihon's traffic so
            // Cloudflare doesn't issue a fresh challenge for this request.
            // The "desktop" appearance the descrambler needs is faked at the
            // JS layer (navigator.userAgent / innerWidth / matchMedia / etc.)
            // in the page-started hook below.
            innerWv.settings.userAgentString = headers["User-Agent"]
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            if (isWebtoon) {
                // Webtoon-only: wide viewport so window.innerWidth reflects the
                // layout width we force on the WebView. Without this the
                // descrambler picks a mobile-tile-per-page path that only
                // materialises one tile and stalls.
                innerWv.settings.useWideViewPort = true
                innerWv.settings.loadWithOverviewMode = false
                // Force a real viewport size on the detached WebView so the page actually
                // gets a non-zero layout. Without this, `document.documentElement.clientWidth`
                // is 0, no element gets bounding-rect dimensions, and the descrambler only
                // renders the top tile of each page (everything past the first viewport
                // height stays unmaterialized).
                innerWv.measure(
                    View.MeasureSpec.makeMeasureSpec(WEBVIEW_VIEWPORT_WIDTH, View.MeasureSpec.EXACTLY),
                    View.MeasureSpec.makeMeasureSpec(WEBVIEW_VIEWPORT_HEIGHT, View.MeasureSpec.EXACTLY),
                )
                innerWv.layout(0, 0, WEBVIEW_VIEWPORT_WIDTH, WEBVIEW_VIEWPORT_HEIGHT)
            }
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            // Forward in-page console.log calls to Logcat (tag "Japscan") so the JS
            // pagination driver's progress is visible during debugging.
            innerWv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    debugLog("[wv] ${msg.message()}")
                    return true
                }
            }

            innerWv.webViewClient = object : WebViewClient() {
                // Called off the UI thread for every subresource — keep it to the
                // string comparison, no logging in this hot path.
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest,
                ): WebResourceResponse? {
                    val host = request.url.host ?: return null
                    val allowed = ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }
                    // Empty stream rather than a null one: a null body makes some WebView
                    // builds keep the request pending, which would stall the whole capture.
                    return if (allowed) {
                        null
                    } else {
                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    debugLog("[wv-kt] onPageStarted url=$url")
                    // Both modes: satisfy the reader's anti-adblock check before it runs.
                    // ~3s after load it looks for `window.aclib` (needs 3+ own keys and 2+
                    // real functions among runPop/runBanner/runNative) and, if it's missing,
                    // calls triggerProtection() -> readerArea.replaceChildren(), which wipes
                    // the descrambled canvases both drivers read. Blocking acscdn.com in
                    // shouldInterceptRequest is exactly what makes the real aclib absent, so
                    // this is required alongside the allowlist, not instead of it. Regular
                    // `function` declarations (not arrows) so `.prototype` is truthy.
                    view?.evaluateJavascript(
                        """
                            (function(){
                                if (window.aclib) return;
                                window.aclib = {
                                    runPop: function runPop(){},
                                    runBanner: function runBanner(){},
                                    runNative: function runNative(){},
                                    runInPagePush: function runInPagePush(){},
                                    isShowingPop: false,
                                };
                            })();
                        """.trimIndent(),
                    ) {}
                    // Pierce closed shadow roots BEFORE any reader script runs: the descrambler
                    // composites each chapter page into canvases hosted by `<w-f1db5>` elements
                    // whose shadow root is `attachShadow({ mode: 'closed' })`. Forcing every
                    // attachShadow call to `open` lets us read `.shadowRoot` from outside and
                    // grab the painted canvases.
                    //
                    // We must not mutate behavior of any other built-in. The site's bot
                    // detection trips on `String.prototype.replace.toString().includes('[native code]')`
                    // and sets `__poisoned = true`, which makes the reader refuse to render.
                    // We therefore (a) override attachShadow only, (b) mask its `.toString` so it
                    // still reads as native, and (c) leave atob/replace/fetch/drawImage alone.
                    //
                    // Webtoon-only payload below: the wide-spectrum hook set is required for the
                    // descrambler to paint every tile of a long-strip page. The paginated reader
                    // refuses to deliver its payload if these same hooks are present (the
                    // `__poisoned = true` bot-detect trips on too many tampered built-ins), so
                    // the paginated path uses a separate minimal hook installed in the `else`
                    // branch further down.
                    if (!isWebtoon) {
                        // Paginated mode: minimal createObjectURL capture. The descrambler
                        // surfaces each composited page as a `blob:` URL via
                        // URL.createObjectURL — capturing the latest one per page-click
                        // is enough.
                        view?.evaluateJavascript(
                            $$"""
                                (function(){
                                    if (window.__japscanHooked) return;
                                    window.__japscanHooked = true;
                                    window.__japscanLastBlob = null;
                                    var _orig = URL.createObjectURL.bind(URL);
                                    URL.createObjectURL = function(obj){
                                        var u = _orig(obj);
                                        try {
                                            if (obj && obj.type && /^image\//.test(obj.type)) {
                                                window.__japscanLastBlob = u;
                                            }
                                        } catch(e) {}
                                        return u;
                                    };
                                    try {
                                        URL.createObjectURL.toString = function(){
                                            return 'function createObjectURL() { [native code] }';
                                        };
                                    } catch(e) {}
                                })();
                            """.trimIndent(),
                        ) {}
                        return
                    }
                    view?.evaluateJavascript(
                        $$"""
                            (function(){
                                // Pin a logging channel through the Java bridge BEFORE the
                                // site has a chance to silence `console.log` (some pages do
                                // `console.log = function(){}` once their reader scripts run,
                                // which would swallow every diagnostic the rest of the driver
                                // emits via `console.log`).
                                try {
                                    var jl = window.$$interfaceName && window.$$interfaceName.log;
                                    if (jl) {
                                        window.__jlog = function(){
                                            try {
                                                var parts = [];
                                                for (var i = 0; i < arguments.length; i++) parts.push(String(arguments[i]));
                                                window.$$interfaceName.log(parts.join(' '));
                                            } catch(e) {}
                                        };
                                    } else {
                                        window.__jlog = function(){ try { console.log.apply(console, arguments); } catch(e) {} };
                                    }
                                } catch(e) {
                                    window.__jlog = function(){};
                                }
                                window.__jlog('[japscan] onPageStarted JS entered, alreadyHooked=' + (window.__japscanHooked === true) + ' url=' + location.href);
                                if (window.__japscanHooked) return;
                                window.__japscanHooked = true;

                                // Patch table: maps each patched function to the canonical
                                // native-code string for its name. We override
                                // Function.prototype.toString so the site sees the original
                                // native source whenever it stringifies one of our hooks —
                                // the reader trips `__poisoned = true` if it spots a
                                // tampered built-in (it explicitly checks `replace`,
                                // `atob` and friends, and likely checks `attachShadow`
                                // and the `shadowRoot` getter as well).
                                var masked = new WeakMap();
                                var origFnToString = Function.prototype.toString;
                                function mask(fn, name){
                                    try { masked.set(fn, 'function ' + name + '() { [native code] }'); } catch(e) {}
                                    return fn;
                                }
                                var patchedToString = function toString(){
                                    var m = masked.get(this);
                                    if (m !== undefined) return m;
                                    return origFnToString.call(this);
                                };
                                Function.prototype.toString = patchedToString;
                                mask(patchedToString, 'toString');

                                // Force every shadow root open at attach time so we can
                                // read its canvases later — but track which ones the
                                // caller *requested* be closed, and hide those from the
                                // public `Element.prototype.shadowRoot` getter. The site
                                // sees `el.shadowRoot === null` for its `<w-f1db5>` hosts
                                // (matching the unpatched behavior); only our compositor,
                                // via `window.__japscanShadowRootFor(el)`, can reach them.
                                try {
                                    var closedRoots = new WeakMap();
                                    var _attach = Element.prototype.attachShadow;
                                    var newAttach = function attachShadow(init){
                                        var requested = (init && init.mode) || 'open';
                                        var opts = {};
                                        if (init) { for (var k in init) opts[k] = init[k]; }
                                        opts.mode = 'open';
                                        var sr = _attach.call(this, opts);
                                        if (requested === 'closed') {
                                            try { closedRoots.set(this, sr); } catch(e) {}
                                        }
                                        return sr;
                                    };
                                    Element.prototype.attachShadow = newAttach;
                                    mask(newAttach, 'attachShadow');

                                    var origDesc = Object.getOwnPropertyDescriptor(Element.prototype, 'shadowRoot');
                                    var origGetter = origDesc && origDesc.get;
                                    var newGetter = function get(){
                                        if (closedRoots.has(this)) return null;
                                        return origGetter ? origGetter.call(this) : null;
                                    };
                                    Object.defineProperty(Element.prototype, 'shadowRoot', {
                                        get: newGetter,
                                        configurable: true,
                                    });
                                    mask(newGetter, 'get shadowRoot');

                                    window.__japscanShadowRootFor = function(el){
                                        try {
                                            if (closedRoots.has(el)) return closedRoots.get(el);
                                            return origGetter ? origGetter.call(el) : el.shadowRoot;
                                        } catch(e) { return null; }
                                    };
                                } catch(e) {
                                    window.__jlog('[japscan] shadow hook failed: ' + e);
                                }

                                // Android throttles requestAnimationFrame for non-visible WebViews
                                // (our WebView is detached, so it's never visible). The reader
                                // drives its tile-creation loop on RAF; without ticks it renders
                                // only the first tile per page and stalls. Force RAF to fire via
                                // setTimeout so the loop keeps progressing.
                                try {
                                    var _rAF = window.requestAnimationFrame;
                                    var newRAF = function requestAnimationFrame(cb){
                                        return setTimeout(function(){
                                            try { cb(performance.now()); } catch(e) {}
                                        }, 16);
                                    };
                                    window.requestAnimationFrame = newRAF;
                                    mask(newRAF, 'requestAnimationFrame');
                                } catch(e) {
                                    window.__jlog('[japscan] rAF hook failed: ' + e);
                                }

                                // Track canvas drawing activity so the compositor can wait for
                                // descrambling to settle before reading pixels. Wrapping
                                // drawImage is detectable via .toString — masked above.
                                try {
                                    window.__japscanLastDraw = 0;
                                    var _drawImage = CanvasRenderingContext2D.prototype.drawImage;
                                    var newDrawImage = function drawImage(){
                                        try {
                                            window.__japscanLastDraw = Date.now();
                                        } catch(e) {}
                                        return _drawImage.apply(this, arguments);
                                    };
                                    CanvasRenderingContext2D.prototype.drawImage = newDrawImage;
                                    mask(newDrawImage, 'drawImage');
                                } catch(e) {
                                    window.__jlog('[japscan] draw hook failed: ' + e);
                                }

                                // Spoof JS-side environment to match a desktop Chrome on this
                                // chapter. The WebView's native viewport/screen/touch signals
                                // tell the descrambler "you're a phone" and it takes the
                                // single-tile-per-page mobile path. Override the values that
                                // the differential probe identified:
                                //   innerWidth/innerHeight, devicePixelRatio,
                                //   screen.width/height, navigator.platform/maxTouchPoints,
                                //   matchMedia for (pointer: fine), (hover: hover),
                                //   and (min-width: <N>px).
                                //
                                // The UA is spoofed HERE, not at the WebSettings level:
                                // WebSettings keeps the Android UA on purpose so Cloudflare
                                // doesn't issue a fresh challenge for this request, and only
                                // the JS-visible navigator.userAgent reads as desktop.
                                // Anything not in the list above was tried and dropped —
                                // don't re-add fake plugins/chrome/webdriver on spec.
                                try {
                                    var defineGetter = function(obj, name, val){
                                        try {
                                            Object.defineProperty(obj, name, {
                                                get: function(){ return val; },
                                                configurable: true,
                                            });
                                        } catch(e) { window.__jlog('[japscan] defineGetter ' + name + ' failed: ' + e); }
                                    };
                                    defineGetter(window, 'innerWidth', 2560);
                                    defineGetter(window, 'innerHeight', 1214);
                                    defineGetter(window, 'devicePixelRatio', 0.75);
                                    defineGetter(window.screen, 'width', 1920);
                                    defineGetter(window.screen, 'height', 1080);
                                    defineGetter(window.screen, 'availWidth', 1920);
                                    defineGetter(window.screen, 'availHeight', 1040);
                                    defineGetter(navigator, 'userAgent', '$$DESKTOP_USER_AGENT');
                                    defineGetter(navigator, 'appVersion', '$$DESKTOP_USER_AGENT'.substring(8));
                                    defineGetter(navigator, 'platform', 'Win32');
                                    defineGetter(navigator, 'maxTouchPoints', 0);
                                    defineGetter(navigator, 'hardwareConcurrency', 12);
                                    // Remove ontouchstart so feature-detect-based mobile
                                    // branches see a non-touch device.
                                    try { delete window.ontouchstart; } catch(e) {}

                                    var _mm = window.matchMedia.bind(window);
                                    var newMM = function matchMedia(q){
                                        var orig = _mm(q);
                                        var shouldForceTrue = (
                                            q === '(pointer: fine)' ||
                                            q === '(hover: hover)' ||
                                            /\(min-width:\s*\d+px\)/.test(q) ||
                                            /\(min-device-width:\s*\d+px\)/.test(q)
                                        );
                                        if (!shouldForceTrue) return orig;
                                        return new Proxy(orig, {
                                            get: function(t, k){
                                                if (k === 'matches') return true;
                                                var v = t[k];
                                                return typeof v === 'function' ? v.bind(t) : v;
                                            },
                                        });
                                    };
                                    window.matchMedia = newMM;
                                    mask(newMM, 'matchMedia');
                                } catch(e) {
                                    window.__jlog('[japscan] env spoof failed: ' + e);
                                }

                                window.__jlog('[japscan] hooks installed');
                            })();
                        """.trimIndent(),
                    ) {}
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    debugLog("[wv-kt] onPageFinished url=$url")
                    // Force-keep the WebView "alive" so its JS timers don't get suspended while
                    // we're detached. resumeTimers is global to all WebViews in the process.
                    view?.onResume()
                    view?.resumeTimers()
                    if (!isWebtoon) {
                        // Paginated driver: walks the reader through every page via the
                        // #block-right / #block-left click-zones; the createObjectURL hook
                        // installed in onPageStarted leaves one blob per page in
                        // `__japscanLastBlob`.
                        view?.evaluateJavascript(
                            $$"""
                                (async function(){
                                    // onPageFinished fires more than once per load (redirects,
                                    // iframes, in-page navigations). Without this guard a second
                                    // driver races the first and saves every page twice.
                                    if (window.__japscanDriverStarted) return;
                                    window.__japscanDriverStarted = true;
                                    var sleep = function(ms){ return new Promise(function(r){ setTimeout(r, ms); }); };

                                    async function saveBlobUrl(u){
                                        if (!u) return false;
                                        try {
                                            var r = await fetch(u);
                                            var b = await r.blob();
                                            var d = await new Promise(function(res, rej){
                                                var fr = new FileReader();
                                                fr.onload = function(){ res(fr.result); };
                                                fr.onerror = rej;
                                                fr.readAsDataURL(b);
                                            });
                                            if (typeof d === 'string' && d.indexOf('data:image/') === 0) {
                                                window.$$interfaceName.savePage(d);
                                                return true;
                                            }
                                        } catch(e) {
                                            console.log('[japscan] saveBlobUrl failed: ' + e);
                                        } finally {
                                            try { URL.revokeObjectURL(u); } catch(e) {}
                                        }
                                        return false;
                                    }

                                    async function waitForBlob(timeoutMs){
                                        var w = 0;
                                        while (!window.__japscanLastBlob && w < timeoutMs) {
                                            await sleep(100); w += 100;
                                        }
                                        if (!window.__japscanLastBlob) return -1;
                                        var lastUrl = window.__japscanLastBlob;
                                        var stable = 0;
                                        while (stable < 400) {
                                            await sleep(100);
                                            if (window.__japscanLastBlob !== lastUrl) {
                                                lastUrl = window.__japscanLastBlob;
                                                stable = 0;
                                            } else {
                                                stable += 100;
                                            }
                                        }
                                        return w;
                                    }

                                    var sel = document.getElementById('pages');
                                    var total = sel ? sel.options.length : 1;
                                    var nextBtn = document.getElementById('block-right');
                                    var prevBtn = document.getElementById('block-left');
                                    console.log('[japscan] paginated mode, total = ' + total + ', next=' + !!nextBtn + ', prev=' + !!prevBtn);

                                    function nav(btn, fallbackKey){
                                        try {
                                            if (btn) { btn.click(); return; }
                                            document.dispatchEvent(new KeyboardEvent('keydown', {
                                                key: fallbackKey, code: fallbackKey,
                                                which: fallbackKey === 'ArrowRight' ? 39 : 37,
                                                keyCode: fallbackKey === 'ArrowRight' ? 39 : 37,
                                                bubbles: true,
                                            }));
                                        } catch(e) {
                                            console.log('[japscan] nav failed: ' + e);
                                        }
                                    }

                                    // The reader pre-renders pages on load (the final pre-render is
                                    // the *last* page, not page 1), so the very first blob the hook
                                    // sees is unreliable. Wait for the pre-render to settle, discard
                                    // whatever was captured, then bounce next->prev to force a clean
                                    // page-1 render before we start harvesting.
                                    //
                                    // waitForBlob already means "a blob arrived and stopped changing
                                    // for 400ms", which is exactly the settle condition — a fixed
                                    // sleep was both slower here and too short on a loaded device.
                                    await waitForBlob(10000);
                                    window.__japscanLastBlob = null;

                                    nav(nextBtn, 'ArrowRight');
                                    await waitForBlob(8000);
                                    window.__japscanLastBlob = null;

                                    nav(prevBtn, 'ArrowLeft');
                                    var t0 = await waitForBlob(8000);
                                    var u0 = window.__japscanLastBlob;
                                    window.__japscanLastBlob = null;
                                    if (total > 1) nav(nextBtn, 'ArrowRight');
                                    var saved = u0 ? ((await saveBlobUrl(u0)) ? 1 : 0) : 0;
                                    console.log('[japscan] page 1 captured after ' + t0 + 'ms (saved=' + saved + ')');

                                    for (var i = 1; i < total; i++) {
                                        var t = await waitForBlob(8000);
                                        var u = window.__japscanLastBlob;
                                        window.__japscanLastBlob = null;
                                        if (i < total - 1) nav(nextBtn, 'ArrowRight');
                                        var ok = u ? await saveBlobUrl(u) : false;
                                        if (ok) saved++;
                                        console.log('[japscan] page ' + (i + 1) + ' captured after ' + t + 'ms (saved=' + saved + ')');
                                    }

                                    console.log('[japscan] done, ' + saved + ' / ' + total + ' pages saved');
                                    try {
                                        window.$$interfaceName.passDone();
                                    } catch(e) {
                                        console.log('[japscan] passDone failed: ' + e);
                                    }
                                })();
                            """.trimIndent(),
                        ) { result -> debugLog("[wv-kt] paginated driver eval result=$result") }
                        return
                    }
                    // Webtoon driver: drives the reader through every chapter page and composites
                    // each page out of the shadow-DOM canvases. The page is rendered as a stack
                    // of <canvas> tiles inside `<w-f1db5>` elements with a (formerly closed, now
                    // forced open by the attachShadow hook) shadow root.
                    view?.evaluateJavascript(
                        $$"""
                            (async function(){
                                // onPageFinished fires more than once per load (redirects,
                                // iframes, in-page navigations). Without this guard a second
                                // driver races the first and saves every tile twice.
                                if (window.__japscanDriverStarted) return;
                                window.__japscanDriverStarted = true;
                                var sleep = function(ms){ return new Promise(function(r){ setTimeout(r, ms); }); };
                                window.__jlog('[japscan] driver start');

                                // Collect every <canvas> reachable from `root`, descending
                                // through shadow roots (including the formerly-closed ones
                                // the attachShadow hook stashed in __japscanShadowRootFor).
                                var shadowFor = window.__japscanShadowRootFor || function(){ return null; };
                                function gatherCanvases(root, out) {
                                    if (!root) return;
                                    if (root.nodeType === 1 && root.tagName === 'CANVAS') {
                                        out.push(root);
                                    }
                                    var sr = null;
                                    try { sr = shadowFor(root); } catch(e) {}
                                    if (sr) {
                                        var sk = sr.children;
                                        if (sk) {
                                            for (var i = 0; i < sk.length; i++) gatherCanvases(sk[i], out);
                                        }
                                    }
                                    var ch = root.children;
                                    if (ch) {
                                        for (var j = 0; j < ch.length; j++) gatherCanvases(ch[j], out);
                                    }
                                }

                                // Long-page layout: `#d-img-N` holds a `cc…`-class wrapper whose
                                // direct <canvas> children are the descrambled vertical slices.
                                // Single source of truth for finding it — discoverTiles and
                                // expectedTileCount must agree, or a class rename desyncs them.
                                function findTileWrapper(container){
                                    if (!container) return null;
                                    for (var ci = 0; ci < container.children.length; ci++) {
                                        var ch = container.children[ci];
                                        if (ch.tagName === 'DIV' && ('' + ch.className).indexOf('cc') === 0) {
                                            return ch;
                                        }
                                    }
                                    return null;
                                }

                                // Discover tile groups in `#d-img-N`'s subtree. Canvases-first:
                                // walk canvases (works regardless of whether the host custom
                                // element is exposed in light DOM), group them by their shadow-root
                                // host, and treat each unique host as one tile.
                                //
                                // Returns `{ host, canvases }` records in stable order of first
                                // appearance. The host is the custom element (e.g. <b-123e6>,
                                // <w-f1db5> — the tag is randomized per series) whose `.canvas`
                                // own-property, when set, gives the descrambled bitmap; `canvases`
                                // is the layered set inside its shadow root.
                                //
                                // Discovery is STRUCTURAL, not readiness-based: we deliberately do
                                // NOT require `.canvas` to exist yet, because the descrambler is
                                // lazy and only renders a tile once it scrolls into view —
                                // chicken-and-egg if we filtered on `.canvas` here.
                                function discoverTiles(container){
                                    if (!container) return [];
                                    // Long pages: emit each slice as a standalone tile so
                                    // toDataURL runs per slice and the webtoon reader stitches them.
                                    var wrap = findTileWrapper(container);
                                    if (wrap) {
                                        var direct = [];
                                        for (var di = 0; di < wrap.children.length; di++) {
                                            var w = wrap.children[di];
                                            if (w.tagName === 'CANVAS' && w.width > 0 && w.height > 0) {
                                                direct.push(w);
                                            }
                                        }
                                        if (direct.length > 0) {
                                            var outArr = [];
                                            for (var di2 = 0; di2 < direct.length; di2++) {
                                                outArr.push({ host: direct[di2], canvases: [direct[di2]] });
                                            }
                                            return outArr;
                                        }
                                    }
                                    // Short-page / single-tile layout: canvases live inside a
                                    // custom-element host (e.g. <t-b8432>). Group them by
                                    // shadow-root host so each host yields one tile.
                                    var canvases = [];
                                    gatherCanvases(container, canvases);
                                    if (canvases.length === 0) return [];
                                    var map = new Map();
                                    var order = [];
                                    for (var i = 0; i < canvases.length; i++) {
                                        var c = canvases[i];
                                        if (c.width <= 0 || c.height <= 0) continue;
                                        var rn = c.getRootNode && c.getRootNode();
                                        var host = (rn && rn.host) ? rn.host : container;
                                        if (!map.has(host)) {
                                            map.set(host, []);
                                            order.push(host);
                                        }
                                        map.get(host).push(c);
                                    }
                                    var out = [];
                                    for (var k = 0; k < order.length; k++) {
                                        out.push({ host: order[k], canvases: map.get(order[k]) });
                                    }
                                    return out;
                                }

                                // Resolve a tile to a single canvas for export. Preference order:
                                //   1. `host.canvas` own-property — the descrambler's explicit
                                //      pointer to the final composited bitmap.
                                //   2. The lone canvas with `position: relative` in the layered
                                //      set — it establishes the visible tile and on inspection
                                //      consistently holds the real bitmap; siblings are decoys.
                                //   3. The first non-empty canvas — last-resort.
                                function pickTileCanvas(tile){
                                    // cc-wrapper slices are their own host — nothing to resolve.
                                    if (tile.host instanceof HTMLCanvasElement) return tile.host;
                                    try {
                                        if (tile.host && tile.host.canvas instanceof HTMLCanvasElement) {
                                            return tile.host.canvas;
                                        }
                                    } catch(e) {}
                                    var arr = tile.canvases || [];
                                    for (var i = 0; i < arr.length; i++) {
                                        var s = null;
                                        try { s = window.getComputedStyle(arr[i]); } catch(e) {}
                                        if (s && s.position === 'relative') return arr[i];
                                    }
                                    return arr[0] || null;
                                }

                                // Wait until at least one of the tile's canvases (or its
                                // host.canvas) has non-zero dimensions and the descrambler has
                                // gone quiet (no drawImage calls for ≥ quietMs).
                                async function waitForTileReady(tile, timeoutMs){
                                    var w = 0;
                                    var quietMs = 600;
                                    while (w < timeoutMs) {
                                        var anySized = false;
                                        try {
                                            if (tile.host && tile.host.canvas && tile.host.canvas.width > 0) anySized = true;
                                        } catch(e) {}
                                        if (!anySized) {
                                            var arr = tile.canvases || [];
                                            for (var i = 0; i < arr.length; i++) {
                                                if (arr[i].width > 0 && arr[i].height > 0) { anySized = true; break; }
                                            }
                                        }
                                        if (anySized) {
                                            // Honor the host's own ready flags if present.
                                            var hostReady = true;
                                            try {
                                                if (tile.host && ('cw' in tile.host)) {
                                                    hostReady = !!(tile.host.cw && tile.host.ch);
                                                }
                                            } catch(e) {}
                                            var sinceDraw = Date.now() - (window.__japscanLastDraw || 0);
                                            if (hostReady && sinceDraw >= quietMs) return w;
                                        }
                                        await sleep(150);
                                        w += 150;
                                    }
                                    return -1;
                                }

                                function tileToDataUri(tile){
                                    try {
                                        var c = pickTileCanvas(tile);
                                        if (!c || !c.width || !c.height) return null;
                                        return c.toDataURL('image/jpeg', 0.92);
                                    } catch(e) { return null; }
                                }

                                // Detect reader mode. Webtoon (long-strip) puts everything in
                                // `#full-reader` with one `<img id="img-N">` per page (each in
                                // its `<div id="d-img-N">` container) and hides `#single-reader`
                                // (class `d-none`); paginated mode is the opposite.
                                var fullReader = document.getElementById('full-reader');
                                var singleReader = document.querySelector('[id^="single-reader"]');
                                var dImgContainers = fullReader
                                    ? Array.from(fullReader.querySelectorAll('div[id^="d-img-"]'))
                                          .sort(function(a, b){
                                              return parseInt(a.id.replace('d-img-', ''), 10)
                                                   - parseInt(b.id.replace('d-img-', ''), 10);
                                          })
                                    : [];
                                var singleHidden = singleReader && singleReader.classList.contains('d-none');
                                var isWebtoon = dImgContainers.length > 0 && (singleHidden || !singleReader);
                                // The Kotlin side picked us based on the URL segment ($$urlSegment).
                                // Compare against what the DOM actually mounted so a misclassified
                                // series shows up in Logcat instead of producing silent garbage.
                                var urlHint = '$$urlSegment';
                                var hintMode = (urlHint === 'manhwa' || urlHint === 'manhua') ? 'webtoon' : 'paginated';
                                var domMode = isWebtoon ? 'webtoon' : 'paginated';
                                window.__jlog('[japscan] mode: webtoon=' + isWebtoon + ' pages=' + dImgContainers.length + ' urlHint=' + hintMode + ' dom=' + domMode + (hintMode !== domMode ? ' MISMATCH' : ''));



                                if (isWebtoon) {
                                    var total = dImgContainers.length;
                                    // Drop the hidden single-reader subtree to keep its stale
                                    // layer-canvases from adding memory pressure.
                                    if (singleReader && singleReader.parentNode) {
                                        try { singleReader.parentNode.removeChild(singleReader); } catch(e) {}
                                    }
                                    // For each `#d-img-N`: determine the expected tile count up
                                    // front (number of direct <canvas> children of the cc...
                                    // wrapper for long pages, else 1 for single-tile pages).
                                    // Try a one-pass capture first; if all expected tiles are
                                    // already drawn, we're done in <1s. Only fall back to the
                                    // slow scroll-and-discover loop if a tile is still missing.
                                    var tilesEmitted = 0;
                                    function expectedTileCount(container){
                                        var wrap = findTileWrapper(container);
                                        if (!wrap) return 1;
                                        var n = 0;
                                        for (var ki = 0; ki < wrap.children.length; ki++) {
                                            if (wrap.children[ki].tagName === 'CANVAS') n++;
                                        }
                                        return n > 0 ? n : 1;
                                    }
                                    async function captureOnce(container, pageLabel, savedHosts){
                                        var saved = 0;
                                        var tiles = discoverTiles(container);
                                        for (var ti = 0; ti < tiles.length; ti++) {
                                            var tile = tiles[ti];
                                            if (!tile.host || savedHosts.has(tile.host)) continue;
                                            var ready = await waitForTileReady(tile, 6000);
                                            if (ready < 0) continue;
                                            var picked = pickTileCanvas(tile);
                                            if (!picked || !picked.width || !picked.height) continue;
                                            var uri = tileToDataUri(tile);
                                            if (!uri) continue;
                                            try {
                                                window.$$interfaceName.savePage(uri);
                                                savedHosts.add(tile.host);
                                                saved++;
                                                tilesEmitted++;
                                                window.__jlog('[japscan] ' + pageLabel + ' tile #' + savedHosts.size + ' saved (' + picked.width + 'x' + picked.height + ', ' + uri.length + ' chars)');
                                            } catch(e) {
                                                window.__jlog('[japscan] ' + pageLabel + ' savePage failed: ' + e);
                                            }
                                        }
                                        return saved;
                                    }
                                    for (var i = 0; i < total; i++) {
                                        var container = dImgContainers[i];
                                        var pageLabel = 'd-img-' + i;
                                        var expected = expectedTileCount(container);
                                        // Bring the container into view so any layout-dependent
                                        // measurements settle, then attempt a one-pass capture.
                                        try { container.scrollIntoView({ block: 'start' }); } catch(e) {}
                                        await sleep(100);
                                        var savedHosts = new Set();
                                        await captureOnce(container, pageLabel, savedHosts);
                                        // If something is still missing (rare — only the lazy
                                        // single-tile path), fall back to the scroll loop with
                                        // a short total budget.
                                        if (savedHosts.size < expected) {
                                            var contRect = container.getBoundingClientRect();
                                            var baseY = (window.scrollY || 0) + contRect.top;
                                            var contH = container.offsetHeight || contRect.height || 2100;
                                            var step = Math.max(400, Math.floor((window.innerHeight || 4096) * 0.5));
                                            for (var sy = 0; sy <= contH + step && savedHosts.size < expected; sy += step) {
                                                window.scrollTo(0, baseY + sy - 100);
                                                await sleep(400);
                                                await captureOnce(container, pageLabel, savedHosts);
                                            }
                                        }
                                        window.__jlog('[japscan] ' + pageLabel + ' completed: ' + savedHosts.size + '/' + expected + ' tile(s)');
                                        try {
                                            if (container.parentNode) container.parentNode.removeChild(container);
                                        } catch(e) {}
                                        dImgContainers[i] = null;
                                    }
                                    window.__jlog('[japscan] webtoon done, ' + tilesEmitted + ' tile(s) saved across ' + total + ' d-img containers');
                                    try { window.$$interfaceName.passDone(); } catch(e) {}
                                    return;
                                }

                                // Kotlin committed to the webtoon hook set before this page loaded,
                                // and the paginated reader refuses to deliver its payload with those
                                // hooks installed — so there is nothing useful to do from here.
                                // Report and bail instead of grinding through a driver that cannot
                                // work; if this ever fires, the fix is to reload the WebView with
                                // the paginated hooks, not a third capture technique.
                                window.__jlog('[japscan] MISMATCH: url hinted webtoon but the DOM mounted the paginated reader — giving up');
                                try { window.$$interfaceName.passDone(); } catch(e) {}
                            })();
                        """.trimIndent(),
                    ) { result -> debugLog("[wv-kt] driver eval result=$result") }
                }
            }

            innerWv.loadUrl(
                "$internalBaseUrl${chapter.url}",
                headers.toMap(),
            )
        }

        // Generous ceiling: the JS sequentially drives every page through the
        // reader's pagination and waits for each blob, which can easily exceed
        // a minute on long chapters. But a wedged driver would otherwise burn the
        // whole ceiling doing nothing, so also give up after IDLE_TIMEOUT_MS
        // without a saved page — each save refreshes the clock, so a healthy long
        // chapter still gets the full three minutes.
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(3)
        var done = false
        while (!done && System.currentTimeMillis() < deadline) {
            done = latch.await(15, TimeUnit.SECONDS)
            if (!done && System.currentTimeMillis() - jsInterface.lastActivity > IDLE_TIMEOUT_MS) {
                debugLog("[wv-kt] no page saved in ${IDLE_TIMEOUT_MS}ms, giving up")
                break
            }
        }
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception("Erreur lors de la récupération des pages")
        }
        // Wrap each absolute cache path in the sentinel host so OkHttp accepts it
        // and our interceptor serves the file. Paths under /data/data/... are
        // already URL-safe (alphanumerics, dots, slashes, hyphens).
        val images = jsInterface.snapshot()
            .mapIndexed { i, path -> Page(i, imageUrl = "https://$JAPSCAN_CACHE_HOST$path") }
        return Observable.just(images)
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException("Not used")

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used")

    // Filters
    private class TextField(name: String) : Filter.Text(name)

    private class PageList(pages: Array<Int>) : Filter.Select<Int>("Page #", arrayOf(0, *pages))

    // Prefs
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val chapterListPref = ListPreference(screen.context).apply {
            key = SHOW_SPOILER_CHAPTERS
            title = SHOW_SPOILER_CHAPTERS_TITLE
            entries = prefsEntries
            entryValues = prefsEntryValues
            summary = "%s"
            setDefaultValue("hide")
        }
        screen.addPreference(chapterListPref)
    }

    // Spooled page files outlive the interceptor that serves them, so the reader can
    // re-request a page (retry, re-open). Nothing else deletes them, and a chapter that
    // is opened but not read through leaves the rest behind, so reap the stale ones here.
    //
    // The cutoff is deliberately far longer than a read session: the reader preloads the
    // next chapter's page list while the current chapter is still open, so this runs with
    // an in-progress read's files on disk and must not touch them.
    private fun sweepPageCache(cacheDir: File) {
        val cutoff = System.currentTimeMillis() - TimeUnit.HOURS.toMillis(24)
        cacheDir.listFiles()?.forEach {
            if (it.name.startsWith(CACHE_FILE_PREFIX) && it.lastModified() < cutoff) it.delete()
        }
    }

    private fun randomString(length: Int = 10): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    internal class JsInterface(
        private val latch: CountDownLatch,
        private val cacheDir: File,
    ) {
        // Last time the driver made progress, for fetchPageList's idle watchdog.
        @Volatile
        var lastActivity: Long = System.currentTimeMillis()
            private set

        private val savedPaths = mutableListOf<String>()
        private val sessionTag = "$CACHE_FILE_PREFIX${System.currentTimeMillis()}"

        // Absolute file paths in the app's cache dir, in capture order.
        fun snapshot(): List<String> = synchronized(savedPaths) { savedPaths.toList() }

        @JavascriptInterface
        @Suppress("UNUSED")
        fun savePage(dataUri: String) {
            lastActivity = System.currentTimeMillis()
            try {
                val commaIdx = dataUri.indexOf(',')
                if (commaIdx <= 0) return
                val base64 = dataUri.substring(commaIdx + 1)
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                synchronized(savedPaths) {
                    val file = File(cacheDir, "$sessionTag-${savedPaths.size}.bin")
                    file.writeBytes(bytes)
                    savedPaths.add(file.absolutePath)
                }
            } catch (_: Exception) {
                // best effort — page just won't be in the list
            }
        }

        @JavascriptInterface
        @Suppress("UNUSED")
        fun log(message: String) {
            debugLog("[js] $message")
        }

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passDone() {
            latch.countDown()
        }
    }
}
