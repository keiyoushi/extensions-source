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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
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
        .rateLimit(1, 2.seconds)
        // Pages from fetchPageList are decoded blobs cached on disk; their imageUrl is
        // `https://japscan-cache.local/<absolute-cache-path>`. We can't override
        // fetchImage (final), so an interceptor catches that sentinel host and serves
        // the file bytes synthetically.
        .addInterceptor { chain ->
            val req = chain.request()
            if (req.url.host != JAPSCAN_CACHE_HOST) return@addInterceptor chain.proceed(req)
            val path = "/" + req.url.pathSegments.joinToString("/")
            val file = File(path)
            val bytes = file.readBytes()
            try {
                file.delete()
            } catch (_: Exception) {}
            Response.Builder()
                .request(req)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .body(bytes.toResponseBody("image/jpeg".toMediaType()))
                .build()
        }
        .build()

    private val captchaRegex = """window\.__captcha\s*=\s*\{\s*needed\s*:\s*true\s*,?""".toRegex()

    companion object {
        private val CHAPTER_PATH_TYPES = setOf("manga", "manhua", "manhwa", "bd", "comic")
        private val HIDDEN_STYLE_TOKENS = listOf(
            "display:none",
            "visibility:hidden",
            "visibility:collapse",
            "content-visibility:hidden",
            "opacity:0",
            "filter:opacity(0",
            "width:0",
            "height:0",
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
        //  - large absolute offset (3+ digits) via top/bottom/left/right or `inset:` shorthand
        //  - `transform: translate / translateX / translateY / translated` with a 3+ digit offset
        //  - `transform: scale(0)` / `scale3d(0,...)` (collapsed to nothing)
        //  - `transform: matrix(0,0,0,0,...)` (also collapsed)
        //  - `max-width:0` / `max-height:0` (mirror of the existing width:0/height:0 tokens)
        // 3 digits is enough to be off-screen even with viewport units (200vh, 999vw, …)
        // while still tolerating fine adjustments like top:-1px or right:99px.
        private val OFFSCREEN_OFFSET_REGEX = Regex(
            """(?:top|bottom|left|right|inset):-?\d{3,}""" +
                """|transform:translate(?:3d|x|y)?\([^)]*-?\d{3,}""" +
                """|transform:scale(?:3d|x|y)?\(0[,)]""" +
                """|transform:matrix\(0,0,0,0""" +
                """|max-(?:width|height):0""",
        )
        val dateFormat = SimpleDateFormat("dd MMM yyyy", Locale.US)

        private const val SHOW_SPOILER_CHAPTERS_TITLE = "Les chapitres en Anglais ou non traduit sont upload en tant que \" Spoilers \" sur Japscan"
        private const val SHOW_SPOILER_CHAPTERS = "JAPSCAN_SPOILER_CHAPTERS"

        // Sentinel host used to route page-image requests to a local cache file via
        // an OkHttp interceptor (see `client` builder).
        private const val JAPSCAN_CACHE_HOST = "japscan-cache.local"

        // Synthetic viewport used to force-layout the detached WebView. We use a
        // desktop-class width because the reader picks a "mobile" rendering path
        // for viewports under ~1280 px wide that only materializes one tile-host
        // per long page (then lazy-loads more on scroll, which our detached WebView
        // can't reliably trigger). With a desktop-class width the reader pre-creates
        // every tile-host upfront, exactly like it does in a real Chrome window —
        // see live-Chrome inspection: innerWidth=5468 yields 7-8 hosts per long
        // page; innerWidth=1080 yields 1.
        private const val WEBVIEW_VIEWPORT_WIDTH = 1920
        private const val WEBVIEW_VIEWPORT_HEIGHT = 16384

        // Desktop Chrome UA matching what the descrambler's working code path
        // expects (the source's own headersBuilder UA is Android-mobile flavored,
        // which makes the descrambler take a one-tile-per-page mobile path).
        private const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
                "(KHTML, like Gecko) Chrome/148.0.0.0 Safari/537.36"
        private val prefsEntries = arrayOf("Montrer uniquement les chapitres traduit en Français", "Montrer les chapitres spoiler")
        private val prefsEntryValues = arrayOf("hide", "show")
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

    // Defense in depth: if a honeypot ever slips past the per-row hidden-style
    // heuristics, its URL number tends to be wildly out of range (e.g. 483181 vs.
    // real 1181, or 000001..000008 vs. real 1174..1181). Real chapter URL ids are
    // near-consecutive, so split sorted ids on gaps that are much larger than the
    // typical spacing and keep only the largest cluster — agnostic to whether the
    // honeypots sit above or below the real range.
    private fun filterOutlierChapters(chapters: List<SChapter>): List<SChapter> {
        val withNum = chapters.mapNotNull { ch ->
            val n = ch.url.trimEnd('/').substringAfterLast('/').toLongOrNull() ?: return@mapNotNull null
            ch to n
        }
        if (withNum.size < 2) return chapters
        val sorted = withNum.sortedBy { it.second }
        val gaps = (1 until sorted.size).map { sorted[it].second - sorted[it - 1].second }
        val medianGap = gaps.sorted()[gaps.size / 2].coerceAtLeast(1L)
        // Treat a gap as a cluster boundary when it dwarfs the typical spacing.
        // The absolute floor (100) keeps short lists with tiny medians from
        // splitting on noise; the 50x ratio handles dense lists where the
        // median is already large.
        val threshold = maxOf(medianGap * 50, 100L)
        val clusters = mutableListOf<MutableList<Pair<SChapter, Long>>>(mutableListOf(sorted[0]))
        for (i in 1 until sorted.size) {
            if (sorted[i].second - sorted[i - 1].second > threshold) {
                clusters.add(mutableListOf())
            }
            clusters.last().add(sorted[i])
        }
        if (clusters.size == 1) return chapters
        val keep = clusters.maxBy { it.size }.map { it.first }.toSet()
        return chapters.filter { it in keep }
    }

    private fun extractMangaSlug(url: okhttp3.HttpUrl): String? {
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
        if (OFFSCREEN_OFFSET_REGEX.containsMatchIn(style)) return true
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
                    val value = attr.value
                    value.startsWith("/manga/") || value.startsWith("/manhua/") || value.startsWith("/manhwa/") || value.startsWith("/bd/") || value.startsWith("/comic/")
                }
                if (attrMatch != null) {
                    val name = el.ownText().ifBlank { el.text() }
                    // Mark if the attribute is not "href"
                    val isNonHref = attrMatch.key != "href"
                    Triple(name, attrMatch.value, isNonHref)
                } else {
                    null
                }
            }
            .distinctBy { it.second }

        // Filter out anti-scraping honeypots by binding name, slug and URL number together:
        // a real chapter URL is /<type>/<mangaSlug>/<chapterNum>/, with the same slug as the
        // manga page and a chapter number that appears in the chapter's name ("Chapitre N: ...").
        // Stripping non-digits from the name handles half-chapters like "Chapitre 1100.5" + /11005/.
        // Honeypots use a different slug (e.g. /manga/cv/N/) with sequential numbers that match a
        // fake "Chapitre N" label, so name/number alone is not enough — slug check is what stops them.
        val filtered = allUrlPairs
            .filter { (name, url, _) ->
                val segments = url.split('/').filter { it.isNotEmpty() }
                if (segments.size != 3) return@filter false
                if (segments[0] !in CHAPTER_PATH_TYPES) return@filter false
                if (mangaSlug != null && segments[1] != mangaSlug) return@filter false
                val urlNum = url.trimEnd('/').substringAfterLast('/')
                if (!urlNum.all { it.isDigit() }) return@filter false
                if (urlNum.length > 1 && urlNum.startsWith('0')) return@filter false
                val chapterNum = Regex("""(?i)chapitre\s+([\d.]+)""").find(name)
                    ?.groupValues?.get(1)?.replace(".", "")
                    ?: name.split(Regex("[^0-9.]+")).lastOrNull { it.isNotEmpty() }?.replace(".", "")
                    ?: return@filter false
                chapterNum == urlNum
            }

        // Fall back to the unfiltered list in case the heuristics are too aggressive.
        // Defense in depth: when the slug filter is unavailable, prefer the longest URL — real
        // slugs (e.g. "one-piece") are usually longer than honeypot slugs (e.g. "cv").
        val urlPairs = (filtered.ifEmpty { allUrlPairs })
            .sortedWith(
                compareByDescending<Triple<String, String, Boolean>> { it.third }
                    .thenByDescending { it.second.length },
            ) // Prefer non-href first, then longer URLs
            .map { Pair(it.first, it.second) }

        val foundPair = urlPairs.firstOrNull()
            ?: throw Exception("Impossible de trouver l'URL du chapitre")

        val chapter = SChapter.create()
        chapter.setUrlWithoutDomain(foundPair.second)
        chapter.name = foundPair.first
        chapter.date_upload = element.selectFirst("span")?.text()?.let { parseChapterDate(it) } ?: 0L
        return chapter
    }

    private fun parseChapterDate(date: String) = dateFormat.tryParse(date)

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val interfaceName = randomString()
        val context = Injekt.get<Application>()
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
            // Use wide viewport so window.innerWidth reflects the layout width
            // we force on the WebView (instead of the device's mobile width).
            innerWv.settings.useWideViewPort = true
            innerWv.settings.loadWithOverviewMode = false
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
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
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            // Forward in-page console.log calls to Logcat (tag "Japscan") so the JS
            // pagination driver's progress is visible during debugging.
            innerWv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage): Boolean {
                    Log.d("Japscan", "[wv] ${msg.message()}")
                    return true
                }
            }

            innerWv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    Log.d("Japscan", "[wv-kt] onPageStarted url=$url")
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
                                    window.__japscanDrawCount = 0;
                                    var _drawImage = CanvasRenderingContext2D.prototype.drawImage;
                                    var newDrawImage = function drawImage(){
                                        try {
                                            window.__japscanLastDraw = Date.now();
                                            window.__japscanDrawCount++;
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
                                //   and (min-width: <N>px). UA is already set at the
                                //   WebSettings level.
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

                                    // Stub window.chrome with the shape a real desktop Chrome has.
                                    // The descrambler's anti-bot path checks for window.chrome and
                                    // treats its absence as "this is a headless/automated browser",
                                    // then renders only 1 tile per page as a defense.
                                    try {
                                        if (typeof window.chrome === 'undefined' || !window.chrome.loadTimes) {
                                            var chromeStub = {
                                                loadTimes: function loadTimes(){ return {}; },
                                                csi: function csi(){ return {}; },
                                                app: { isInstalled: false, InstallState: { DISABLED: 'disabled', INSTALLED: 'installed', NOT_INSTALLED: 'not_installed' }, RunningState: { CANNOT_RUN: 'cannot_run', READY_TO_RUN: 'ready_to_run', RUNNING: 'running' } },
                                            };
                                            mask(chromeStub.loadTimes, 'loadTimes');
                                            mask(chromeStub.csi, 'csi');
                                            Object.defineProperty(window, 'chrome', { get: function(){ return chromeStub; }, configurable: true });
                                        }
                                    } catch(e) { window.__jlog('[japscan] chrome stub failed: ' + e); }

                                    // Fake plugins/mimeTypes — WebView reports 0 for both, real
                                    // Chrome reports 5/2. Bot-detection scripts flag length=0.
                                    try {
                                        var fakeMime = function(type, suffixes, desc){
                                            return { type: type, suffixes: suffixes, description: desc, enabledPlugin: null };
                                        };
                                        var fakePlugin = function(name, filename, desc, mimes){
                                            var p = { name: name, filename: filename, description: desc, length: mimes.length };
                                            for (var i = 0; i < mimes.length; i++) { p[i] = mimes[i]; mimes[i].enabledPlugin = p; }
                                            return p;
                                        };
                                        var pdfMime = fakeMime('application/pdf', 'pdf', 'Portable Document Format');
                                        var pdfViewerMime = fakeMime('text/pdf', 'pdf', 'Portable Document Format');
                                        var plugins = [
                                            fakePlugin('PDF Viewer', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime, pdfViewerMime]),
                                            fakePlugin('Chrome PDF Viewer', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime, pdfViewerMime]),
                                            fakePlugin('Chromium PDF Viewer', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime, pdfViewerMime]),
                                            fakePlugin('Microsoft Edge PDF Viewer', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime, pdfViewerMime]),
                                            fakePlugin('WebKit built-in PDF', 'internal-pdf-viewer', 'Portable Document Format', [pdfMime, pdfViewerMime]),
                                        ];
                                        plugins.length = 5;
                                        plugins.item = function(i){ return plugins[i] || null; };
                                        plugins.namedItem = function(n){ for (var i=0;i<plugins.length;i++) if (plugins[i].name===n) return plugins[i]; return null; };
                                        plugins.refresh = function(){};
                                        Object.defineProperty(navigator, 'plugins', { get: function(){ return plugins; }, configurable: true });

                                        var mimeTypes = [pdfMime, pdfViewerMime];
                                        mimeTypes.length = 2;
                                        mimeTypes.item = function(i){ return mimeTypes[i] || null; };
                                        mimeTypes.namedItem = function(n){ for (var i=0;i<mimeTypes.length;i++) if (mimeTypes[i].type===n) return mimeTypes[i]; return null; };
                                        Object.defineProperty(navigator, 'mimeTypes', { get: function(){ return mimeTypes; }, configurable: true });
                                    } catch(e) { window.__jlog('[japscan] plugins stub failed: ' + e); }

                                    // languages: real Chrome has multiple, WebView has 1.
                                    try {
                                        Object.defineProperty(navigator, 'languages', { get: function(){ return ['fr', 'fr-FR', 'en', 'en-US']; }, configurable: true });
                                        Object.defineProperty(navigator, 'language', { get: function(){ return 'fr'; }, configurable: true });
                                    } catch(e) {}

                                    // Some bot-detection scripts check navigator.webdriver explicitly;
                                    // ensure it reads as undefined (default, but defensive).
                                    try {
                                        Object.defineProperty(navigator, 'webdriver', { get: function(){ return undefined; }, configurable: true });
                                    } catch(e) {}

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
                    Log.d("Japscan", "[wv-kt] onPageFinished url=$url")
                    // Force-keep the WebView "alive" so its JS timers don't get suspended while
                    // we're detached. resumeTimers is global to all WebViews in the process.
                    view?.onResume()
                    view?.resumeTimers()
                    // Drive the reader through every chapter page and composite each page out
                    // of the shadow-DOM canvases. The page is rendered as a stack of <canvas>
                    // tiles inside `<w-f1db5>` elements with a (formerly closed, now forced
                    // open by the attachShadow hook) shadow root. We walk every canvas in the
                    // page container, project each onto a fresh master canvas at the natural
                    // tile resolution using bounding-rect coordinates, and ship the result
                    // through the JS interface as a data: URI.
                    view?.evaluateJavascript(
                        $$"""
                            (async function(){
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

                                // Discover the tile hosts that make up `#d-img-N`. The reader uses
                                // a custom element (e.g. <b-123e6>, <w-f1db5> — the tag name is
                                // randomized per series) that exposes the composited tile bitmap
                                // as `tile.canvas` (an own-property set in the custom element's
                                // constructor when it has actually rendered).
                                //
                                // STRUCTURAL discovery, not readiness: we identify a tile by its
                                // tag (any hyphenated custom-element tag) that is a direct child
                                // either of #d-img-N or of #d-img-N > div.cc80466. We deliberately
                                // do NOT require `.canvas` to exist yet, because the descrambler
                                // is lazy and only renders tiles after they are scrolled into the
                                // viewport — chicken-and-egg if we filter on `.canvas` here.
                                // Walk every node reachable from `root` (including across open and
                                // recovered-closed shadow roots) and return the unique hyphenated-
                                // tag custom elements that have a `.canvas` HTMLCanvasElement own-
                                // property — or, structurally, ANY hyphenated-tag element if no
                                // `.canvas`-bearing one exists yet (the constructor sets `.canvas`
                                // only after the descrambler renders).
                                // Discover tile groups in `#d-img-N`'s subtree. Canvases-first
                                // approach: walk canvases (works regardless of whether the host
                                // custom element is exposed in light DOM), group them by their
                                // shadow-root host, and treat each unique host as one tile.
                                //
                                // Returns an array of `{ host, canvases }` records in stable order
                                // of first appearance. The host is the custom element (whose
                                // `.canvas` own-property, when set, gives the descrambled bitmap);
                                // `canvases` is the layered set inside its shadow root.
                                function discoverTiles(container){
                                    if (!container) return [];
                                    // Long-page WebView layout: container has a `cc...`-class
                                    // wrapper holding 7-8 direct <canvas> children (one per
                                    // descrambled vertical slice). Emit each canvas as a
                                    // standalone tile so toDataURL is called per slice and
                                    // the webtoon reader stitches them.
                                    var wrap = null;
                                    for (var ci = 0; ci < container.children.length; ci++) {
                                        var ch = container.children[ci];
                                        if (ch.tagName === 'DIV' && ('' + ch.className).indexOf('cc') === 0) {
                                            wrap = ch; break;
                                        }
                                    }
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

                                // Paginated mode only: composite the (#single-reader-XXX) container's
                                // direct canvas children. Layered canvases at the same position, so
                                // we draw them all at (0, 0) in DOM order. No shadow roots, no
                                // custom-element tiles in this layout — it's the simple legacy form.
                                function paginatedComposite(container, label) {
                                    if (!container) return null;
                                    var canvases = container.querySelectorAll(':scope > canvas');
                                    if (canvases.length === 0) {
                                        window.__jlog('[japscan] ' + label + ' compose: no direct canvas children');
                                        return null;
                                    }
                                    var W = 0, H = 0;
                                    for (var i = 0; i < canvases.length; i++) {
                                        if (canvases[i].width  > W) W = canvases[i].width;
                                        if (canvases[i].height > H) H = canvases[i].height;
                                    }
                                    if (W === 0 || H === 0) return null;
                                    var master = document.createElement('canvas');
                                    master.width = W; master.height = H;
                                    var mctx = master.getContext('2d');
                                    if (!mctx) return null;
                                    var drawn = 0;
                                    for (var k = 0; k < canvases.length; k++) {
                                        try { mctx.drawImage(canvases[k], 0, 0); drawn++; } catch(e) {}
                                    }
                                    window.__jlog('[japscan] ' + label + ' compose: ' + W + 'x' + H + ' canvases=' + canvases.length + ' drawn=' + drawn);
                                    var uri = null;
                                    try { uri = master.toDataURL('image/jpeg', 0.92); } catch(e) {}
                                    master.width = 0; master.height = 0;
                                    return uri;
                                }

                                // Wait for the descrambler to populate the container's shadow-DOM
                                // canvases, then settle until the canvas count is stable for a bit.
                                // Settle on TWO independent signals:
                                //  - canvas count is non-zero and stable (DOM structure done),
                                //  - no drawImage calls for `quietMs` (descrambler done painting).
                                // Without the second signal we capture mid-paint and only the
                                // first few layered passes have been drawn, so the rendered page
                                // is incomplete.
                                async function waitForRender(container, timeoutMs, label) {
                                    var quietMs = 800;
                                    var minTotal = 1500;
                                    var pollMs = 150;
                                    var w = 0;
                                    var prevCount = -1;
                                    var structuralStable = 0;
                                    var lastLogN = -1;
                                    while (w < timeoutMs) {
                                        var arr = [];
                                        gatherCanvases(container, arr);
                                        var n = arr.length;
                                        var hasContent = false;
                                        var maxW = 0, maxH = 0;
                                        for (var i = 0; i < arr.length; i++) {
                                            if (arr[i].width > maxW) maxW = arr[i].width;
                                            if (arr[i].height > maxH) maxH = arr[i].height;
                                            if (arr[i].width > 0 && arr[i].height > 0) hasContent = true;
                                        }
                                        var now = Date.now();
                                        var sinceDraw = now - (window.__japscanLastDraw || 0);
                                        if (label && (n !== lastLogN) && (w === 0 || w >= 500)) {
                                            window.__jlog('[japscan] ' + label + ' poll @' + w + 'ms canvases=' + n + ' hasContent=' + hasContent + ' max=' + maxW + 'x' + maxH + ' sinceDraw=' + sinceDraw + 'ms drawCount=' + (window.__japscanDrawCount || 0));
                                            lastLogN = n;
                                        }
                                        if (hasContent && n === prevCount) {
                                            structuralStable += pollMs;
                                        } else {
                                            structuralStable = 0;
                                            prevCount = n;
                                        }
                                        if (structuralStable >= 600 && sinceDraw >= quietMs && w >= minTotal) {
                                            if (label) window.__jlog('[japscan] ' + label + ' settled @' + w + 'ms (sinceDraw=' + sinceDraw + 'ms drawCount=' + (window.__japscanDrawCount || 0) + ')');
                                            return w;
                                        }
                                        await sleep(pollMs);
                                        w += pollMs;
                                    }
                                    if (label) window.__jlog('[japscan] ' + label + ' settle timed out @' + w + 'ms');
                                    return -1;
                                }

                                async function savePaginated(container, label) {
                                    var t = await waitForRender(container, 20000, label);
                                    if (t < 0) {
                                        window.__jlog('[japscan] ' + label + ' render timed out');
                                        return false;
                                    }
                                    var uri = paginatedComposite(container, label);
                                    if (!uri || uri.indexOf('data:image/') !== 0) {
                                        window.__jlog('[japscan] ' + label + ' composite failed');
                                        return false;
                                    }
                                    try {
                                        window.$$interfaceName.savePage(uri);
                                        window.__jlog('[japscan] ' + label + ' saved after ' + t + 'ms (' + uri.length + ' chars)');
                                        return true;
                                    } catch(e) {
                                        window.__jlog('[japscan] savePage failed: ' + e);
                                        return false;
                                    }
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
                                window.__jlog('[japscan] mode: webtoon=' + isWebtoon + ' pages=' + dImgContainers.length);



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
                                        for (var ci = 0; ci < container.children.length; ci++) {
                                            var ch = container.children[ci];
                                            if (ch.tagName === 'DIV' && ('' + ch.className).indexOf('cc') === 0) {
                                                var n = 0;
                                                for (var ki = 0; ki < ch.children.length; ki++) {
                                                    if (ch.children[ki].tagName === 'CANVAS') n++;
                                                }
                                                return n > 0 ? n : 1;
                                            }
                                        }
                                        return 1;
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

                                // ---- Paginated mode ----
                                // The currently-displayed page lives inside the single-reader
                                // container. Total page count comes from the (hidden) <select>;
                                // navigation uses the reader's click-zones (#block-left/right) —
                                // slimselect option clicks don't work because `.ss-content`
                                // is hidden until the dropdown opens.
                                var sel = document.getElementById('pages');
                                var total = sel ? sel.options.length : 1;
                                var nextBtn = document.getElementById('block-right');
                                var prevBtn = document.getElementById('block-left');
                                window.__jlog('[japscan] paginated mode, total = ' + total + ', next=' + !!nextBtn + ', prev=' + !!prevBtn);

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
                                        window.__jlog('[japscan] nav failed: ' + e);
                                    }
                                }

                                // Wait until the canvas content inside the single-reader container
                                // changes compared to the previous page (compared via a tiny pixel
                                // fingerprint of the widest canvas).
                                function fingerprint(container) {
                                    var arr = [];
                                    gatherCanvases(container, arr);
                                    if (arr.length === 0) return '';
                                    var ref = arr[0];
                                    for (var i = 1; i < arr.length; i++) {
                                        if (arr[i].width > ref.width) ref = arr[i];
                                    }
                                    try {
                                        var ctx = ref.getContext('2d');
                                        if (!ctx) return '';
                                        var pts = [
                                            [0, 0],
                                            [Math.max(0, ref.width - 1), 0],
                                            [Math.floor(ref.width / 2), Math.floor(ref.height / 2)],
                                            [0, Math.max(0, ref.height - 1)],
                                            [Math.max(0, ref.width - 1), Math.max(0, ref.height - 1)],
                                        ];
                                        var s = '';
                                        for (var i = 0; i < pts.length; i++) {
                                            var d = ctx.getImageData(pts[i][0], pts[i][1], 1, 1).data;
                                            s += d[0] + ',' + d[1] + ',' + d[2] + ',' + d[3] + ';';
                                        }
                                        return s;
                                    } catch(e) { return ''; }
                                }

                                async function waitForPageChange(container, prevFp, timeoutMs) {
                                    var w = 0;
                                    while (w < timeoutMs) {
                                        var fp = fingerprint(container);
                                        if (fp && fp !== prevFp) {
                                            // Settle: keep checking until fp stops changing.
                                            var lastFp = fp;
                                            var stable = 0;
                                            while (stable < 600 && w < timeoutMs) {
                                                await sleep(200); w += 200;
                                                var nfp = fingerprint(container);
                                                if (nfp !== lastFp) { lastFp = nfp; stable = 0; }
                                                else { stable += 200; }
                                            }
                                            return w;
                                        }
                                        await sleep(200); w += 200;
                                    }
                                    return -1;
                                }

                                var paginatedContainer = singleReader;
                                if (!paginatedContainer) {
                                    window.__jlog('[japscan] no single-reader container found');
                                    try { window.$$interfaceName.passDone(); } catch(e) {}
                                    return;
                                }

                                // The reader pre-renders pages on load (the final pre-render is
                                // the *last* page, not page 1). Give it time to settle, then
                                // bounce next->prev to force a clean page-1 render.
                                await sleep(3000);
                                if (total > 1) {
                                    nav(nextBtn, 'ArrowRight');
                                    await sleep(1500);
                                    nav(prevBtn, 'ArrowLeft');
                                    await sleep(1500);
                                }

                                var savedP = 0;
                                var prevFp = '';
                                for (var p = 0; p < total; p++) {
                                    var t = await waitForRender(paginatedContainer, 15000);
                                    if (t < 0) {
                                        window.__jlog('[japscan] paginated page ' + (p + 1) + ' render timed out');
                                    } else {
                                        var uri = paginatedComposite(paginatedContainer, 'paginated page ' + (p + 1));
                                        if (uri && uri.indexOf('data:image/') === 0) {
                                            try { window.$$interfaceName.savePage(uri); savedP++; } catch(e) {}
                                            window.__jlog('[japscan] paginated page ' + (p + 1) + '/' + total + ' saved after ' + t + 'ms');
                                        } else {
                                            window.__jlog('[japscan] paginated page ' + (p + 1) + ' composite failed');
                                        }
                                    }
                                    prevFp = fingerprint(paginatedContainer);
                                    if (p < total - 1) {
                                        nav(nextBtn, 'ArrowRight');
                                        await waitForPageChange(paginatedContainer, prevFp, 15000);
                                    }
                                }

                                window.__jlog('[japscan] paginated done, ' + savedP + ' / ' + total + ' pages saved');
                                try { window.$$interfaceName.passDone(); } catch(e) {}
                            })();
                        """.trimIndent(),
                    ) { result -> Log.d("Japscan", "[wv-kt] driver eval result=$result") }
                }
            }

            innerWv.loadUrl(
                "$internalBaseUrl${chapter.url}",
                headers.toMap(),
            )
        }

        // Generous timeout: the JS sequentially drives every page through the
        // reader's pagination and waits for each blob, which can easily exceed
        // a minute on long chapters.
        latch.await(3, TimeUnit.MINUTES)
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception("Erreur lors de la récupération des pages")
        }
        // Wrap each absolute cache path in the sentinel host so OkHttp accepts it
        // and our interceptor serves the file. Paths under /data/data/... are
        // already URL-safe (alphanumerics, dots, slashes, hyphens).
        val images = jsInterface.images
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

    private fun randomString(length: Int = 10): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    internal class JsInterface(
        private val latch: CountDownLatch,
        private val cacheDir: File,
    ) {
        // Absolute file paths in the app's cache dir, in capture order.
        var images: List<String> = listOf()
            private set
        private val savedPaths = mutableListOf<String>()
        private val sessionTag = "japscan-${System.currentTimeMillis()}"

        @JavascriptInterface
        @Suppress("UNUSED")
        fun savePage(dataUri: String) {
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
            Log.d("Japscan", "[js] $message")
        }

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passDone() {
            images = synchronized(savedPaths) { savedPaths.toList() }
            latch.countDown()
        }
    }
}
