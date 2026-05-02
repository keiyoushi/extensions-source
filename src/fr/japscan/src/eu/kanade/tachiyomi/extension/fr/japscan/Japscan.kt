package eu.kanade.tachiyomi.extension.fr.japscan

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.collections.mapIndexed

class Japscan :
    ParsedHttpSource(),
    ConfigurableSource {

    override val id: Long = 11

    override val name = "Japscan"

    // Sometimes an adblock blocker will pop up, preventing the user from opening
    // a cloudflare protected page
    private val internalBaseUrl = "https://www.japscan.foo"
    override val baseUrl = "$internalBaseUrl/mangas/?sort=popular&p=1"

    override val lang = "fr"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val preferences: SharedPreferences by getPreferencesLazy()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2)
        .build()

    private val captchaRegex = """window\.__captcha\s*=\s*\{\s*needed\s*:\s*true\s*,?""".toRegex()

    companion object {
        private val CHAPTER_PATH_TYPES = setOf("manga", "manhua", "manhwa", "bd", "comic")
        private val HIDDEN_STYLE_TOKENS = listOf(
            "display:none",
            "visibility:hidden",
            "opacity:0",
            "width:0",
            "height:0",
            "pointer-events:none",
            "clip-path:inset(100%",
            "clip:rect(0,0,0,0",
            "font-size:0",
            "text-indent:-",
        )

        // Match a large absolute offset (3+ digits, positive or negative) on any side.
        // 3 digits is enough to be off-screen even with viewport units (200vh, 999vw, …)
        // while still tolerating fine adjustments like top:-1px or right:99px.
        private val OFFSCREEN_OFFSET_REGEX = Regex("""(?:top|bottom|left|right):-?\d{3,}""")
        val dateFormat by lazy {
            SimpleDateFormat("dd MMM yyyy", Locale.US)
        }
        private const val SHOW_SPOILER_CHAPTERS_TITLE = "Les chapitres en Anglais ou non traduit sont upload en tant que \" Spoilers \" sur Japscan"
        private const val SHOW_SPOILER_CHAPTERS = "JAPSCAN_SPOILER_CHAPTERS"
        private val prefsEntries = arrayOf("Montrer uniquement les chapitres traduit en Français", "Montrer les chapitres spoiler")
        private val prefsEntryValues = arrayOf("hide", "show")
    }

    private fun chapterListPref() = preferences.getString(SHOW_SPOILER_CHAPTERS, "hide")

    override fun headersBuilder() = super.headersBuilder()
        .add("referer", "$internalBaseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$internalBaseUrl/mangas/?sort=popular&p=$page", headers)

    override fun popularMangaNextPageSelector() = ".pagination > li:last-child:not(.disabled)"

    override fun popularMangaSelector() = ".mangas-list .manga-block:not(:has(a[href='']))"

    override fun popularMangaFromElement(element: Element): SManga {
        val manga = SManga.create()
        element.select("a").first()!!.let {
            manga.setUrlWithoutDomain(it.attr("href"))
            manga.title = it.text()
            manga.thumbnail_url = it.selectFirst("img")?.attr("abs:data-src")
        }
        return manga
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = GET("$internalBaseUrl/mangas/?sort=updated&p=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element): SManga = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector(): String = popularMangaNextPageSelector()

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

    override fun searchMangaNextPageSelector(): String = popularMangaSelector()

    override fun searchMangaSelector(): String = "div.card div.p-2"

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.first() == "ls") {
            val jsonResult = json.parseToJsonElement(response.body.string()).jsonArray

            val mangaList = jsonResult.map { jsonEl -> searchMangaFromJson(jsonEl.jsonObject) }

            return MangasPage(mangaList, hasNextPage = false)
        }

        val baseUrlHost = internalBaseUrl.toHttpUrl().host
        val document = response.asJsoup()
        val manga = document
            .select(searchMangaSelector())
            .filter { it ->
                // Filter out ads masquerading as search results
                it.select("p a").attr("abs:href").toHttpUrl().host == baseUrlHost
            }
            .map(::searchMangaFromElement)
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null

        return MangasPage(manga, hasNextPage)
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        thumbnail_url = element.select("img").attr("abs:src")
        element.select("p a").let {
            title = it.text()
            url = it.attr("href")
        }
    }

    private fun searchMangaFromJson(jsonObj: JsonObject): SManga = SManga.create().apply {
        url = jsonObj["url"]!!.jsonPrimitive.content
        title = jsonObj["name"]!!.jsonPrimitive.content
        thumbnail_url = internalBaseUrl + jsonObj["image"]!!.jsonPrimitive.content
    }

    override fun mangaDetailsRequest(manga: SManga): Request = GET(internalBaseUrl + manga.url, headers)

    override fun mangaDetailsParse(document: Document): SManga {
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

    private fun parseStatus(status: String) = when {
        status.contains("En Cours") -> SManga.ONGOING
        status.contains("Terminé") -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun getChapterUrl(chapter: SChapter): String = internalBaseUrl + chapter.url

    override fun chapterListRequest(manga: SManga): Request = GET(internalBaseUrl + manga.url, headers)

    override fun chapterListSelector() = "#list_chapters > div.collapse > div.list_chapters" +
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
        return document.select(chapterListSelector()).mapNotNull { el ->
            runCatching { parseChapter(el, mangaSlug) }.getOrNull()
        }
    }

    private fun extractMangaSlug(url: okhttp3.HttpUrl): String? {
        val segments = url.pathSegments.filter { it.isNotEmpty() }
        val typeIdx = segments.indexOfFirst { it in CHAPTER_PATH_TYPES }
        if (typeIdx == -1 || typeIdx + 1 >= segments.size) return null
        return segments[typeIdx + 1].takeIf { it.isNotEmpty() }
    }

    override fun chapterFromElement(element: Element): SChapter = throw UnsupportedOperationException()

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
                val chapterNum = Regex("""(?i)chapitre\s+([\d.]+)""").find(name)
                    ?.groupValues?.get(1)?.replace(".", "")
                    ?: name.split(Regex("[^0-9.]+")).filter { it.isNotEmpty() }
                        .lastOrNull()?.replace(".", "")
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
        chapter.date_upload = element.selectFirst("span")?.text()?.trim()?.let { parseChapterDate(it) } ?: 0L
        return chapter
    }

    private fun parseChapterDate(date: String) = runCatching {
        dateFormat.parse(date)!!.time
    }.getOrDefault(0L)

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val interfaceName = randomString()
        val context = Injekt.get<Application>()
        val isReader = Exception().stackTrace.any { it.className.contains("reader") }

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch)
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
            } catch (e: Exception) {
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
            innerWv.settings.blockNetworkImage = true
            innerWv.settings.userAgentString = headers["User-Agent"]
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            innerWv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view?.evaluateJavascript(
                        $$"""
                            function waitForRC(callback) {
                                if (window.__rc) {
                                    callback();
                                } else {
                                    setTimeout(() => waitForRC(callback), 100);
                                }
                            }

                            // Hook atob — Japscan delivers the chapter payload as a base64-encoded
                            // JSON whose `cc` array contains the c4.japscan.foo image URLs. The
                            // payload no longer goes through String.replace, so atob is the only
                            // reliable interception point.
                            const originalAtob = window.atob;
                            window.atob = function(str) {
                                const result = originalAtob.call(this, str);
                                try {
                                    let utf8 = result;
                                    try {
                                        utf8 = decodeURIComponent(
                                            Array.from(result, c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join('')
                                        );
                                    } catch (e) { return result; }
                                    if (utf8.indexOf('c4.japscan.foo') !== -1 && !window.__createCalled) {
                                        try {
                                            const parsed = JSON.parse(utf8);
                                            waitForRC(() => create(parsed));
                                        } catch (e) { /* not JSON */ }
                                    }
                                } catch (e) { /* swallow */ }
                                return result;
                            };

                            const originalReplace = String.prototype.replace;

                            function tryDecodeBase64ToJsonKeysOnly(str) {
                              const s = String(str).trim();
                              if (!/^[A-Za-z0-9+/]+={0,2}$/.test(s) || s.length % 4 === 1) return null;
                              try {
                                const bin = atob(s);
                                const utf8 = decodeURIComponent(
                                  Array.from(bin, c => '%' + ('00' + c.charCodeAt(0).toString(16)).slice(-2)).join('')
                                );
                                if(!utf8.includes(location.pathname.replaceAll("/", "\\/"))) return null;
                                const parsed = JSON.parse(utf8);
                                return parsed
                              } catch (e) {
                                return null;
                              }
                              return null;
                            }

                            String.prototype.replace = function(searchValue, replaceValue) {
                              const receiver = this;

                              const effectiveReplace = (typeof replaceValue === 'function')
                                ? function(...args) { return replaceValue.apply(this, args); }
                                : replaceValue;

                              const rawResult = originalReplace.call(receiver, searchValue, effectiveReplace);

                              if (typeof rawResult === 'string') {
                                const parsed = tryDecodeBase64ToJsonKeysOnly(rawResult);
                                if (parsed) {
                                  waitForRC(() => create(parsed))
                                }
                              }

                              return rawResult;
                            };

                            function findFirstArray(obj) {
                              let found = null;
                              (function visit(value) {
                                if (found) return;
                                if (value && typeof value === 'object') {
                                  if (Array.isArray(value)) {
                                    found = value;
                                    return;
                                  }
                                  for (const k in value) {
                                    if (Object.prototype.hasOwnProperty.call(value, k)) {
                                      visit(value[k]);
                                      if (found) return;
                                    }
                                  }
                                }
                              })(obj);
                              return found;
                            }

                            // Pick the array whose elements look like CDN image URLs.
                            // Japscan's payload also embeds a path-segments array (e.g.
                            // ["manga","one-piece","1181"]) that findFirstArray would otherwise grab.
                            function findImageArray(obj) {
                              let found = null;
                              (function visit(value) {
                                if (found) return;
                                if (Array.isArray(value) && value.length > 0 &&
                                    value.every(v => typeof v === 'string' && v.indexOf('c4.japscan.foo') !== -1)) {
                                  found = value;
                                  return;
                                }
                                if (value && typeof value === 'object') {
                                  for (const k in value) {
                                    if (Object.prototype.hasOwnProperty.call(value, k)) {
                                      visit(value[k]);
                                      if (found) return;
                                    }
                                  }
                                }
                              })(obj);
                              return found;
                            }

                            function create(parsed) {
                                if (window.__createCalled) return;
                                let arr = findImageArray(parsed) || findFirstArray(parsed);
                                const arrLen = arr ? arr.length : -1;
                                if (!arr || arr.length === 0) return;
                                window.__createCalled = true;
                                const chapterMatch = location.pathname.match(/\/(\d+)(?:\/|$)/);
                                const chapterNum = chapterMatch ? Number(chapterMatch[1]) : null;
                                let candidate = null;
                                (function visit(obj) {
                                    if (candidate) return;
                                        if (obj && typeof obj === 'object') {
                                            for (const k in obj) {
                                                if (!Object.prototype.hasOwnProperty.call(obj, k)) continue;
                                                const v = obj[k];
                                                if (typeof v === 'number' && Number.isFinite(v) && Math.floor(v) === v) {
                                                const n = v;
                                                if (n > 0 && n <= arrLen && n !== chapterNum) { candidate = n; return; }
                                            }
                                            if (typeof v === 'string' && /^[0-9]+$/.test(v)) {
                                                const n = Number(v);
                                                if (n > 0 && n <= arrLen && n !== chapterNum) { candidate = n; return; }
                                            }
                                            if (typeof v === 'object') visit(v);
                                            if (candidate) return;
                                        }
                                    }
                                })(parsed);
                                const finalNum = candidate || chapterNum || 0;
                                window.$$interfaceName.passPayload(JSON.stringify(arr), window.__rc.p, window.__rc.v, finalNum.toString());
                            }
                        """.trimIndent(),
                    ) {}
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    // Force-keep the WebView "alive" so its JS timers don't get suspended while
                    // we're detached. resumeTimers is global to all WebViews in the process.
                    view?.onResume()
                    view?.resumeTimers()
                }
            }

            innerWv.loadUrl(
                "$internalBaseUrl${chapter.url}",
                headers.toMap(),
            )
        }

        latch.await(30, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception("Erreur lors de la récupération des pages")
        }
        val baseUrlHost = internalBaseUrl.toHttpUrl().host.substringAfter("www.")
        val images = jsInterface.images
            .filter { it.toHttpUrl().host.endsWith(baseUrlHost) }
            .mapIndexed { i, url ->
                if (i != jsInterface.pi) {
                    Page(i, imageUrl = "$url&${jsInterface.p}=${jsInterface.v}")
                } else {
                    null
                }
            }
            .filterNotNull()
        return Observable.just(images)
    }

    override fun pageListParse(document: Document) = throw Exception("Not used")

    override fun imageUrlParse(document: Document): String = ""

    // Filters
    private class TextField(name: String) : Filter.Text(name)

    private class PageList(pages: Array<Int>) : Filter.Select<Int>("Page #", arrayOf(0, *pages))

    // Prefs
    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val chapterListPref = ListPreference(screen.context).apply {
            key = SHOW_SPOILER_CHAPTERS_TITLE
            title = SHOW_SPOILER_CHAPTERS_TITLE
            entries = prefsEntries
            entryValues = prefsEntryValues
            summary = "%s"

            setOnPreferenceChangeListener { _, newValue ->
                val selected = newValue as String
                val index = this.findIndexOfValue(selected)
                val entry = entryValues[index] as String
                preferences.edit().putString(SHOW_SPOILER_CHAPTERS, entry).commit()
            }
        }
        screen.addPreference(chapterListPref)
    }

    private fun randomString(length: Int = 10): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    internal class JsInterface(private val latch: CountDownLatch) {
        var images: List<String> = listOf()
            private set
        var p: String = ""
            private set
        var v: String = ""
            private set
        var pi: Int = -1
            private set

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passPayload(rawData: String, p: String, v: String, pi: String) {
            try {
                images = rawData.parseAs<List<String>>()
                    .map { "$it?y=1" }
                this.p = p
                this.v = v
                this.pi = pi.toInt()
                latch.countDown()
            } catch (_: Exception) {
                return
            }
        }
    }
}
