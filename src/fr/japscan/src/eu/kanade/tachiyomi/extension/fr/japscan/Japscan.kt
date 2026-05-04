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
            innerWv.settings.userAgentString = headers["User-Agent"]
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
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
                    // Install the createObjectURL hook as early as possible. Each chapter page is
                    // descrambled onto canvases by the page's own JS and surfaced as a `blob:` URL
                    // via URL.createObjectURL — capturing those gives us the final rendered images.
                    // We deliberately avoid overriding String.prototype.replace / atob / fetch:
                    // the new bot detection checks `replace.toString().includes('[native code]')`
                    // and trips `__poisoned = true` if any built-in is monkey-patched, which
                    // makes the page refuse to deliver the payload.
                    //
                    // We stream the conversion (blob -> base64 data URI) as soon as each blob is
                    // created and revoke the original blob right after, so memory doesn't grow
                    // unboundedly across the 13+ pages we drive through (otherwise the renderer
                    // OOMs with all the page canvases held simultaneously).
                    view?.evaluateJavascript(
                        $$"""
                            (function(){
                                if (window.__japscanHooked) return;
                                window.__japscanHooked = true;
                                // Single slot tracking the most-recent image blob. Used by the
                                // paginated-reader driver, which harvests one blob per page
                                // navigation. The webtoon driver ignores this slot and instead
                                // iterates `<img id="img-N">.src` in DOM order. We do NOT
                                // auto-revoke the previous blob here: in webtoon mode all
                                // blobs are pinned to <img> elements, and revoking them
                                // before we fetch their bytes makes the URL unfetchable.
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
                                // Make our hook look like a native function in case the site ever
                                // adds the same `[native code]` tripwire to createObjectURL.
                                try {
                                    URL.createObjectURL.toString = function(){
                                        return 'function createObjectURL() { [native code] }';
                                    };
                                } catch(e) {}
                            })();
                        """.trimIndent(),
                    ) {}
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Force-keep the WebView "alive" so its JS timers don't get suspended while
                    // we're detached. resumeTimers is global to all WebViews in the process.
                    view?.onResume()
                    view?.resumeTimers()
                    // Drive the reader through every chapter page (the `<select id="pages">`
                    // element drives navigation). The createObjectURL hook installed in
                    // onPageStarted converts each blob to a data URI as soon as it's produced,
                    // so we just drive the UI and wait for window.__japscanImages to stabilize.
                    view?.evaluateJavascript(
                        $$"""
                            (async function(){
                                var sleep = function(ms){ return new Promise(function(r){ setTimeout(r, ms); }); };

                                // Convert a blob URL to a base64 data URI and save it via the
                                // JS interface. Returns true on success.
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

                                // Paginated mode: harvest the latest blob from the slot.
                                async function harvest(){
                                    var u = window.__japscanLastBlob;
                                    if (!u) return false;
                                    window.__japscanLastBlob = null;
                                    return await saveBlobUrl(u);
                                }

                                // Wait for a fresh blob to appear (slot starts null after each
                                // harvest). After it appears, settle for a bit so any later
                                // re-render of the same page replaces the slot with the final
                                // composited image.
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

                                // Detect reader mode. Webtoon (long-strip) puts everything in
                                // `#full-reader` with one `<img id="img-N">` per page and hides
                                // `#single-reader` (class `d-none`); paginated mode is the
                                // opposite. We use a different driver per mode.
                                var fullReader = document.getElementById('full-reader');
                                var singleReader = document.querySelector('[id^="single-reader"]');
                                var webtoonImgs = fullReader
                                    ? Array.from(fullReader.querySelectorAll('img[id^="img-"]'))
                                          .sort(function(a, b){
                                              return parseInt(a.id.replace('img-', ''), 10)
                                                   - parseInt(b.id.replace('img-', ''), 10);
                                          })
                                    : [];
                                var singleHidden = singleReader && singleReader.classList.contains('d-none');
                                var isWebtoon = webtoonImgs.length > 0 && (singleHidden || !singleReader);

                                if (isWebtoon) {
                                    var total = webtoonImgs.length;
                                    console.log('[japscan] webtoon mode, total = ' + total);
                                    var saved = 0;
                                    // Each webtoon page is rendered into ~8 layered canvases
                                    // inside a closed shadow root and then composited to a
                                    // pinned `<img>.src` blob. Holding all 14 pages worth of
                                    // canvases and bitmaps in the DOM concurrently OOMs the
                                    // renderer (≈ 14 × 8 × 940 × 2100 × 4 ≈ 880 MB), so we
                                    // tear down each `#d-img-N` container as soon as we've
                                    // grabbed the bytes for that page.
                                    //
                                    // We also remove the (hidden) single-reader subtree up
                                    // front to drop its stale layer-canvases; in webtoon mode
                                    // it's `d-none` and only adds memory pressure.
                                    if (singleReader && singleReader.parentNode) {
                                        try { singleReader.parentNode.removeChild(singleReader); } catch(e) {}
                                    }
                                    for (var i = 0; i < total; i++) {
                                        var img = webtoonImgs[i];
                                        var container = document.getElementById('d-img-' + i);
                                        try { img.scrollIntoView({ block: 'start' }); } catch(e) {}
                                        // Wait for the descrambler to set img.src to a blob URL.
                                        var w = 0;
                                        while ((!img.src || img.src.indexOf('blob:') !== 0) && w < 20000) {
                                            await sleep(250); w += 250;
                                        }
                                        if (img.src && img.src.indexOf('blob:') === 0) {
                                            var ok = await saveBlobUrl(img.src);
                                            if (ok) saved++;
                                        }
                                        // Free the page's DOM/bitmap memory before moving on.
                                        try {
                                            img.src = '';
                                            img.removeAttribute('src');
                                            if (container && container.parentNode) {
                                                container.parentNode.removeChild(container);
                                            }
                                        } catch(e) {}
                                        webtoonImgs[i] = null;
                                        // Drop the slot ref too — the hook will overwrite it
                                        // for the next page; nulling here lets any earlier
                                        // (already-saved) blob be GCed promptly.
                                        window.__japscanLastBlob = null;
                                        console.log('[japscan] webtoon page ' + (i + 1) + ' / ' + total + ' after ' + w + 'ms (saved=' + saved + ')');
                                    }
                                    console.log('[japscan] webtoon done, ' + saved + ' / ' + total + ' pages saved');
                                    try { window.$$interfaceName.passDone(); } catch(e) {}
                                    return;
                                }

                                // ---- Paginated mode ----
                                // Total page count from the (hidden) <select>. Navigation goes
                                // through the reader's click-zones (`#block-left` / `#block-right`).
                                // Slimselect option clicks don't work because `.ss-content` is
                                // hidden until the dropdown is opened; click-zones are what real
                                // users tap and reliably trigger a per-page render.
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
                                // sees is unreliable. Wait for things to settle, discard whatever
                                // was captured, then bounce next->prev to force a clean page-1
                                // render before we start harvesting.
                                await sleep(5000);
                                window.__japscanLastBlob = null;

                                nav(nextBtn, 'ArrowRight');
                                await waitForBlob(8000);
                                window.__japscanLastBlob = null;

                                nav(prevBtn, 'ArrowLeft');
                                var t0 = await waitForBlob(8000);
                                // Capture page 1's blob synchronously, then kick off page 2 BEFORE
                                // we start encoding+saving page 1. The descrambler's per-page
                                // pipeline (tile fetch + decode + composite) overlaps with our
                                // blob → base64 → disk-write, shaving roughly one save-latency
                                // off every page.
                                var u0 = window.__japscanLastBlob;
                                window.__japscanLastBlob = null;
                                if (total > 1) nav(nextBtn, 'ArrowRight');
                                var saved = u0 ? ((await saveBlobUrl(u0)) ? 1 : 0) : 0;
                                console.log('[japscan] page 1 captured after ' + t0 + 'ms (saved=' + saved + ')');

                                for (var i = 1; i < total; i++) {
                                    var t = await waitForBlob(8000);
                                    var u = window.__japscanLastBlob;
                                    window.__japscanLastBlob = null;
                                    // Pre-click the next page so its render starts now; we'll
                                    // encode + save the current page concurrently below.
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
                    ) {}
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
        fun passDone() {
            images = synchronized(savedPaths) { savedPaths.toList() }
            latch.countDown()
        }
    }
}
