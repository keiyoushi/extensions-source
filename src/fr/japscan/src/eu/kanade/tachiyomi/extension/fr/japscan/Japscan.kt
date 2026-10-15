package eu.kanade.tachiyomi.extension.fr.japscan

import android.content.ComponentName
import android.content.Intent
import android.util.Base64
import android.webkit.WebResourceResponse
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CacheControl
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.ByteArrayInputStream
import java.io.File
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class Japscan :
    KeiSource(),
    ConfigurableSource {

    private val preferences by getPreferencesLazy()

    // Pages are captured from the reader's canvases and spooled to the cache dir; their imageUrl
    // points at a sentinel host that this interceptor serves from disk, ahead of the rate limiter.
    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addInterceptor { chain ->
        val request = chain.request()
        if (request.url.host != CACHE_HOST) return@addInterceptor chain.proceed(request)
        val bytes = File("/" + request.url.pathSegments.joinToString("/")).takeIf(File::exists)?.readBytes()
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(if (bytes != null) 200 else 404)
            .message(if (bytes != null) "OK" else "Not Found")
            .body((bytes ?: ByteArray(0)).toResponseBody("image/jpeg".toMediaType()))
            .build()
    }.rateLimit(1, 2.seconds)

    // Sometimes an adblock blocker will pop up, preventing the user from opening
    // a cloudflare protected page
    override fun getHomeUrl() = "$baseUrl/mangas/?sort=popular&p=1"

    override suspend fun getPopularManga(page: Int) = parseMangaList(client.get("$baseUrl/mangas/?sort=popular&p=$page").asJsoup())

    override suspend fun getLatestUpdates(page: Int) = parseMangaList(client.get("$baseUrl/mangas/?sort=updated&p=$page").asJsoup())

    private fun parseMangaList(document: Document): MangasPage {
        val mangas = document.select(".mangas-list .manga-block:not(:has(a[href='']))").map { element ->
            SManga.create().apply {
                element.selectFirst("a")!!.let {
                    setUrlWithoutDomain(it.absUrl("href"))
                    title = it.text()
                    thumbnail_url = it.selectFirst("img")?.absUrl("data-src")
                }
            }
        }
        val hasNextPage = document.selectFirst(".pagination > li:last-child:not(.disabled)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        if (query.isBlank()) return getPopularManga(page)
        val body = FormBody.Builder().add("search", query).build()
        val searchHeaders = headers.newBuilder().add("X-Requested-With", "XMLHttpRequest").build()
        val results = client.post("$baseUrl/ls/", searchHeaders, body).parseAs<List<SearchResultDto>>()
        return MangasPage(results.map { it.toSManga(baseUrl) }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseUrl.toHttpUrl().host) return null
        val segments = url.pathSegments.filter(String::isNotEmpty)
        if (segments.size < 2 || segments[0] !in CHAPTER_PATH_TYPES) return null
        val mangaUrl = "/${segments[0]}/${segments[1]}/"
        val document = client.get(baseUrl + mangaUrl).asJsoup()
        return parseMangaDetails(document).apply {
            this.url = mangaUrl
            title = document.selectFirst("#main .card-body h1")!!.text()
        }
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val response = client.get(baseUrl + manga.url)
        val mangaSlug = extractMangaSlug(response.request.url)
        val document = response.asJsoup()
        val details = parseMangaDetails(document).apply {
            url = manga.url
            title = manga.title
        }
        val chapterList = document.select(chapterListSelector()).mapNotNull { el ->
            runCatching { parseChapter(el, mangaSlug) }.getOrNull()
        }
        return SMangaUpdate(details, filterOutlierChapters(chapterList))
    }

    private fun parseMangaDetails(document: Document) = SManga.create().apply {
        val infoElement = document.selectFirst("#main .card-body")!!
        thumbnail_url = infoElement.selectFirst("img")?.absUrl("src")
        infoElement.select(".row, .d-flex").select("p").forEach { el ->
            when (el.select("span").text()) {
                "Auteur(s):" -> author = el.text().removePrefix("Auteur(s):").trim()
                "Artiste(s):" -> artist = el.text().removePrefix("Artiste(s):").trim()
                "Genre(s):" -> genre = el.text().removePrefix("Genre(s):").trim()
                "Statut:" -> status = parseStatus(el.text().removePrefix("Statut:"))
            }
        }
        description = infoElement.selectFirst("div:contains(Synopsis) + p")?.ownText()
    }

    private fun parseStatus(status: String) = status.lowercase().let {
        when {
            it.contains("en cours") -> SManga.ONGOING
            it.contains("terminé") -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // JapScan sometimes uploads some "spoiler preview" chapters, containing 2 or 3 untranslated pictures taken from a raw. Sometimes they also upload full RAWs/US versions and replace them with a translation as soon as available.
    // Those have a span.badge "SPOILER" or "RAW". The additional pseudo selector makes sure to exclude these from the chapter list.
    private fun chapterListSelector() = "#list_chapters > div.collapse > div.list_chapters" +
        if (preferences.getString(SHOW_SPOILER_CHAPTERS, "hide") == "hide") {
            ":not(:has(.badge:contains(SPOILER),.badge:contains(RAW),.badge:contains(VUS)))"
        } else {
            ""
        }

    // Backstop for a honeypot that clears the slug/number binding in parseChapter: its number
    // comes out wildly out of range (observed: 483181 among real 1174..1181), so cap on the median.
    // Compare chapter_number, not the URL id: "Chapitre 1100.5" is /11005/.
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
        return segments[typeIdx + 1]
    }

    private fun isHidden(el: Element): Boolean {
        if (el.hasClass("d-none")) return true
        if (el.hasAttr("hidden")) return true
        if (el.attr("aria-hidden").equals("true", ignoreCase = true)) return true
        val style = el.attr("style").replace(" ", "").lowercase()
        return HIDDEN_STYLE_TOKENS.any { style.contains(it) } || HIDDEN_STYLE_REGEX.containsMatchIn(style)
    }

    private fun isHiddenWithin(el: Element, root: Element): Boolean {
        var cur: Element? = el
        while (cur != null && cur !== root) {
            if (isHidden(cur)) return true
            cur = cur.parent()
        }
        return false
    }

    // Japscan hides honeypot links in each row (d-none, zero size, off-screen, ...) and puts the
    // real URL in a random non-href attribute. Only visible elements are considered, and the URL
    // must be /<type>/<mangaSlug>/<N>/ with N matching the chapter number in the name.
    private fun parseChapter(element: Element, mangaSlug: String?): SChapter {
        val allUrlPairs = (element.getElementsContainingText("Chapitre") + element.getElementsContainingText("Volume"))
            .filterNot { isHiddenWithin(it, element) }
            .mapNotNull { el ->
                val attrMatch = el.attributes().asList().firstOrNull { attr ->
                    CHAPTER_PATH_TYPES.any { attr.value.startsWith("/$it/") }
                }
                attrMatch?.let { Pair(el.ownText().ifBlank { el.text() }, it.value) }
            }
            .distinctBy { it.second }

        val filtered = allUrlPairs.filter { (name, url) ->
            val segments = url.split('/').filter { it.isNotEmpty() }
            if (segments.size != 3) return@filter false
            if (segments[0] !in CHAPTER_PATH_TYPES) return@filter false
            if (mangaSlug != null && segments[1] != mangaSlug) return@filter false
            val urlNum = segments[2]
            if (!urlNum.all { it.isDigit() }) return@filter false
            if (urlNum.length > 1 && urlNum.startsWith('0')) return@filter false
            val chapterNum = CHAPTER_NUM_REGEX.find(name)?.groupValues?.get(1)?.replace(".", "")
                ?: name.split(NON_NUMBER_REGEX).lastOrNull { it.isNotEmpty() }?.replace(".", "")
                ?: return@filter false
            chapterNum == urlNum
        }

        // Fall back to the unfiltered list in case the heuristics are too aggressive, preferring
        // the longest URL: real slugs are usually longer than honeypot slugs (e.g. "cv").
        val (name, url) = filtered.firstOrNull()
            ?: allUrlPairs.maxByOrNull { it.second.length }
            ?: throw Exception("Impossible de trouver l'URL du chapitre")

        return SChapter.create().apply {
            this.url = url
            this.name = name
            chapter_number = CHAPTER_NUM_REGEX.find(name)?.groupValues?.get(1)?.toFloatOrNull() ?: -1f
            date_upload = DATE_FORMAT.tryParseDate(element.selectFirst("span.float-right")?.text(), PARIS)
        }
    }

    private val pageListMutex = Mutex()

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        pageListMutex.withLock {
            // Must be read before the first suspension point, while the caller's frames are on the stack.
            val isReader = Exception().stackTrace.any { it.className.contains("reader") }
            val chapterUrl = baseUrl + chapter.url
            sweepPageCache(applicationContext.cacheDir)

            solveCaptcha(chapterUrl, isReader)

            // manhwa/manhua use the long-strip reader, everything else the paginated one. The DOM
            // can't tell them apart before the reader JS mounts, and both need different hooks.
            val urlSegment = chapter.url.trimStart('/').substringBefore('/').lowercase()
            val isWebtoon = urlSegment == "manhwa" || urlSegment == "manhua"

            val cachedPages = runReaderWebView(chapterUrl, isWebtoon, urlSegment)
            if (cachedPages.isEmpty()) {
                throw Exception("Erreur lors de la récupération des pages")
            }
            return cachedPages.mapIndexed { i, path -> Page(i, imageUrl = "https://$CACHE_HOST$path") }
        }
    }

    private suspend fun captchaPresent(chapterUrl: String): Boolean = client.get(chapterUrl, CacheControl.FORCE_NETWORK).use {
        CAPTCHA_REGEX.containsMatchIn(it.body.string())
    }

    private suspend fun solveCaptcha(chapterUrl: String, isReader: Boolean) {
        if (!captchaPresent(chapterUrl)) return

        // Cold sessions usually get past the captcha after loading the homepage once in a WebView
        warmupWebViewSession()
        if (!captchaPresent(chapterUrl)) return

        val context = applicationContext
        try {
            val intent = Intent().apply {
                component = ComponentName(context, "eu.kanade.tachiyomi.ui.webview.WebViewActivity")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra("url_key", chapterUrl)
                putExtra("source_key", id)
                putExtra("title_key", "Résolvez le captcha, fermez la Webview et réouvrez le chapitre.")
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Suwayomi etc.
            throw Exception("Résolvez le captcha de ce chapitre depuis la WebView et réouvrez le chapitre.")
        }
        repeat(CAPTCHA_MAX_POLLS) {
            delay(CAPTCHA_POLL_INTERVAL)
            if (!captchaPresent(chapterUrl)) {
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
                return
            }
        }
        throw Exception("Résolvez le captcha, fermez la Webview et réouvrez le chapitre.")
    }

    private suspend fun warmupWebViewSession() {
        runCatching {
            val response = client.get(baseUrl)

            runWebView<Unit>(timeout = 8.seconds) {
                var finished = false
                onPageFinished { finished = true }
                // Settle window that lets Cloudflare's beacon commit cf_clearance before teardown
                poll(200.milliseconds) { if (finished) resolve(Unit) }
                loadData(baseUrl, response.body.string())
            }
        }
    }

    private suspend fun runReaderWebView(
        chapterUrl: String,
        isWebtoon: Boolean,
        urlSegment: String,
    ): List<String> {
        val interfaceName = randomString()
        val sessionTag = "$CACHE_FILE_PREFIX${System.currentTimeMillis()}"
        val savedPaths = mutableListOf<String>()
        var done = false

        val response = client.get(chapterUrl)

        return runCatching {
            runWebView<List<String>>(timeout = 3.minutes) {
                domStorageEnabled = true
                javaScriptEnabled = true
                blockImages = false
                userAgent = headers["User-Agent"]!!
                if (isWebtoon) {
                    useWideViewPort = true
                    loadWithOverviewMode = false
                }

                jsBridge("${interfaceName}_savePage") { dataUri ->
                    savePage(dataUri, sessionTag, savedPaths.size)?.let(savedPaths::add)
                }

                jsBridge("${interfaceName}_passDone") {
                    done = true
                    resolve(savedPaths)
                }

                // The reader pulls rotating ad hosts whose modals break the detached descrambler,
                // so only the origins the reader needs are allowed.
                interceptRequest { request ->
                    val host = request.url.host ?: return@interceptRequest null
                    if (ALLOWED_HOSTS.any { host == it || host.endsWith(".$it") }) {
                        null
                    } else {
                        // A null body keeps some WebView builds pending, stalling the capture
                        WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                    }
                }

                onPageStarted {
                    evaluateJs(ACLIB_STUB)
                    evaluateJs(if (isWebtoon) webtoonHooks(interfaceName) else PAGINATED_HOOK)
                }

                onPageFinished {
                    evaluateJs(if (isWebtoon) webtoonDriver(interfaceName, urlSegment) else paginatedDriver(interfaceName))
                }

                loadData(chapterUrl, response.body.string())
            }
        }.getOrElse { emptyList() }
            .takeIf { done } ?: emptyList()
    }

    private fun savePage(
        dataUri: String,
        sessionTag: String,
        size: Int,
    ): String? {
        val commaIdx = dataUri.indexOf(',')
        if (commaIdx <= 0) return null
        return runCatching {
            val bytes = Base64.decode(dataUri.substring(commaIdx + 1), Base64.DEFAULT)
            val file = File(applicationContext.cacheDir, "$sessionTag-$size.bin")
            file.writeBytes(bytes)
            file.absolutePath
        }.getOrNull()
    }

    // Spooled pages must outlive the chapter being read (the reader may re-request them or
    // preload the next chapter), so only files older than a day are reaped.
    private fun sweepPageCache(cacheDir: File) {
        val cutoff = System.currentTimeMillis() - 24.hours.inWholeMilliseconds
        cacheDir.listFiles()?.forEach {
            if (it.name.startsWith(CACHE_FILE_PREFIX) && it.lastModified() < cutoff) it.delete()
        }
    }

    private fun randomString(length: Int = 10): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = SHOW_SPOILER_CHAPTERS
            title = SHOW_SPOILER_CHAPTERS_TITLE
            entries = arrayOf("Montrer uniquement les chapitres traduit en Français", "Montrer les chapitres spoiler")
            entryValues = arrayOf("hide", "show")
            summary = "%s"
            setDefaultValue("hide")
        }.let(screen::addPreference)
    }

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

        // Styles that visually remove an element while leaving it in the DOM: zero opacity/size,
        // 3+ digit off-screen offsets or translations, and collapsed transforms. Zero values are
        // anchored so visible values like `opacity:0.9` or `min-width:0` don't match.
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
        private val CHAPTER_NUM_REGEX = Regex("""(?i)chapitre\s+([\d.]+)""")
        private val NON_NUMBER_REGEX = Regex("[^0-9.]+")
        private val CAPTCHA_REGEX = """window\.__captcha\s*=\s*\{\s*needed\s*:\s*true\s*,?""".toRegex()
        private val DATE_FORMAT = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.US)
        private val PARIS = ZoneId.of("Europe/Paris")

        private const val SHOW_SPOILER_CHAPTERS_TITLE = "Les chapitres en Anglais ou non traduit sont upload en tant que \" Spoilers \" sur Japscan"
        private const val SHOW_SPOILER_CHAPTERS = "JAPSCAN_SPOILER_CHAPTERS"

        private const val CACHE_HOST = "japscan-cache.local"
        private const val CACHE_FILE_PREFIX = "japscan-"
        private val IDLE_TIMEOUT = 45.seconds
        private val CAPTCHA_POLL_INTERVAL = 5.seconds
        private const val CAPTCHA_MAX_POLLS = 15

        // Under ~1280px wide the long-strip reader lazy-loads tiles on scroll, which a detached
        // WebView can't trigger; above it every tile host is created upfront.
        private const val WEBVIEW_VIEWPORT_WIDTH = 1920
        private const val WEBVIEW_VIEWPORT_HEIGHT = 16384

        private val ALLOWED_HOSTS = listOf(
            "japscan.foo",
            "cdnjs.cloudflare.com",
            "code.jquery.com",
            "cdn.jsdelivr.net",
            "fonts.googleapis.com",
            "fonts.gstatic.com",
            "challenges.cloudflare.com",
        )
    }
}
