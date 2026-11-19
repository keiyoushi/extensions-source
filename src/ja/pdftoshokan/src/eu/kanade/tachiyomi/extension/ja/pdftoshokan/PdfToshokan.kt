package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.lib.cronet.cronet
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.decodeHex
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import keiyoushi.utils.xor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.IOException
import java.io.OutputStream
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class PdfToshokan : KeiSource() {

    private val preferences by getPreferencesLazy()

    private val store by lazy { VolumeStore(applicationContext.cacheDir.resolve("source_$id/volumes")) }

    private val downloads = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Volumes download one at a time, the way a person reads them on the site. */
    private val downloadLock = Mutex()

    private val baseHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this
        // The site answers bursts of page loads with 429, so pages are spaced out like a person browsing
        .rateLimit(2, interval = 500.milliseconds) { it.host == baseHost }
        // The site and its file host turn away clients that don't connect like a browser
        .cronet()
        .addInterceptor(PageInterceptor(store) { it.host.endsWith(".$baseHost") })

    override suspend fun getPopularManga(page: Int): MangasPage = getListing("popular", page)

    override suspend fun getLatestUpdates(page: Int): MangasPage = getListing("", page)

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        // Same normalization and limits as the site's own search box
        val keyword = query.replace(SPACES_REGEX, " ").trim()
        if (keyword.isNotEmpty()) {
            if (keyword.length !in 2..20) throw Exception("キーワードは2〜20文字で入力してください")
            return getListing("search/$keyword", page)
        }

        val category = filters.firstInstance<CategoryFilter>().toUriPart()
        return if (category.isNotEmpty()) {
            getListing("category/$category", page)
        } else {
            getListing(filters.firstInstance<SortFilter>().toUriPart(), page)
        }
    }

    private suspend fun getListing(path: String, page: Int): MangasPage {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            if (path.isNotEmpty()) addPathSegments(path)
            if (page > 1) addPathSegment("page").addPathSegment(page.toString())
        }.build()
        val document = loadPage(url.toString())
        val hasNextPage = document.select("a[href*=/page/${page + 1}]")
            .any { it.attr("href").substringAfterLast("/page/").trimEnd('/') == "${page + 1}" }
        return MangasPage(parseListing(document), hasNextPage)
    }

    private fun parseListing(document: Document): List<SManga> {
        val scripts = document.select("script:not([src])").joinToString("\n") { it.data() }
        val (linkKey, linkAttr) = HES_CONFIG_REGEX.find(scripts)!!.destructured

        // Honeypots: a collapsed article whose link leads to an URL that gets the client IP banned.
        // Its link only ever gets read here, from the raw HTML: the page hooks getAttribute on it to navigate there.
        val styles = document.select("style").joinToString("\n") { it.data() }
        val hiddenClasses = HIDDEN_CLASS_REGEX.findAll(styles).map { it.groupValues[1] }.toSet() +
            TRAP_CLASS_REGEX.findAll(scripts).map { it.groupValues[1] }
        val trapIds = TRAP_REGEX.findAll(scripts).map { it.groupValues[1] }.toSet() + TRAP_ID

        return document.select("article").mapNotNull { article ->
            if (article.classNames().any { it in hiddenClasses }) return@mapNotNull null
            val link = article.selectFirst(".entry-title a[$linkAttr]") ?: return@mapNotNull null
            val id = MANGA_ID_REGEX.find(link.attr(linkAttr).decodeHex().xor(linkKey.encodeToByteArray()).decodeToString())?.groupValues?.get(1)
                ?: return@mapNotNull null
            if (id in trapIds) return@mapNotNull null

            SManga.create().apply {
                url = id
                title = link.text()
                thumbnail_url = article.selectFirst(".entry-content img")?.absUrl("src")
            }
        }
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.host != baseHost) return null
        val id = MANGA_ID_REGEX.find(url.encodedPath)?.groupValues?.get(1) ?: return null
        if (id == TRAP_ID) return null
        return parseDetails(loadPage("$baseUrl/manga/$id"), id)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/manga/${manga.url}"

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = loadPage(getMangaUrl(manga))
        val chapterList = volumeOptions(document).map { label ->
            SChapter.create().apply {
                url = "${manga.url}/$label"
                name = if (label.toFloatOrNull() != null) "第${label}巻" else label
                chapter_number = label.toFloatOrNull() ?: -1f
            }
        }.reversed()
        return SMangaUpdate(parseDetails(document, manga.url), chapterList)
    }

    private fun parseDetails(document: Document, id: String) = SManga.create().apply {
        url = id
        title = document.selectFirst("h1.entry-title")!!.text()
        thumbnail_url = document.selectFirst(".entry-content img")?.absUrl("src")
            ?: document.selectFirst("meta[property=og:image]")?.attr("content")
        description = document.select(".entry-content > p")
            .filter { it.selectFirst("img, select, input") == null }
            .joinToString("\n\n") { it.text() }
            .ifBlank { null }
        genre = document.select(".cat-links a").joinToString { it.text() }.ifBlank { null }
        // Tags hold the author names; their href is a honeypot, so only the text is used
        author = document.select(".tags-links a").joinToString { it.text() }.ifBlank { null }
        status = SManga.UNKNOWN
    }

    private fun volumeOptions(document: Document) = document.select("select.vi13 option").map { it.text() }.distinct()

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/manga/${chapter.url.substringBefore("/")}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val id = chapter.url.substringBefore("/")
        val label = chapter.url.substringAfter("/")
        val (file, count) = openVolume(id, label)
        return List(count) { Page(it, imageUrl = "$file#$it") }
    }

    /**
     * The file host lets a volume's file through only with the pass decode.php hands out along with it, so a volume
     * that isn't stored yet is opened through decode.php, the way the site's read button does. A stored volume opens
     * from its remembered file without going online.
     */
    private suspend fun openVolume(id: String, label: String): Pair<HttpUrl, Int> {
        val known = preferences.getString(fileKey(id, label), null)?.toHttpUrl()
            ?: preferences.getString(folderKey(id), null)?.toHttpUrl()?.volumeFile(label)
        if (known != null && store.contains(known)) return known to open(known)

        val file = decode("$baseUrl/manga/$id", label)
        remember(id, label, file)
        return file to open(file)
    }

    private fun HttpUrl.volumeFile(label: String) = newBuilder().removePathSegment(pathSize - 1).addPathSegment("$label.pdf").build()

    /** Starts the volume's download if needed and returns its page count, which comes before the pages do. */
    private suspend fun open(url: HttpUrl): Int {
        store.download(url, downloads) { download(url, it) }
        return store.pageCount(url)
    }

    private suspend fun decode(titleUrl: String, label: String): HttpUrl {
        repeat(DECODE_ATTEMPTS) { attempt ->
            when (val result = runDecode(titleUrl, label)) {
                null -> if (attempt < DECODE_ATTEMPTS - 1) delay(10.seconds * (attempt + 1))
                else -> return result
            }
        }
        throw IOException("Site is rate limiting volume requests, try again in a few minutes")
    }

    /**
     * Opens a volume the way a reader does: the site's firewall only lets decode.php through for a real tap on the
     * title page's read button, which opens it in place of the page. decode.php then hands the viewer an opaque token,
     * so the viewer is left to run and its request for the volume file is caught. Returns the volume file, or null
     * when rate limited.
     */
    @OptIn(NativeWebViewApi::class)
    private suspend fun runDecode(titleUrl: String, label: String): HttpUrl? = runWebView(timeout = 90.seconds) {
        setupSiteWebView(onVolumeFile = { resolve(it) })
        val bridge = randomName()
        webMessageBridge(bridge, setOf(baseUrl)) { message ->
            if (message == "missing") {
                reject(IOException("Volume not found"))
            } else {
                val (x, y) = message.split(',').map(String::toFloat)
                tap(x, y)
            }
        }
        injectAtDocumentStart(pressReadScript(bridge, label), setOf(baseUrl))
        poll(1.seconds) {
            evaluateJs(DECODE_STATE_JS) { json ->
                val state = json.parseAs<String?>() ?: return@evaluateJs
                when (state) {
                    "429" -> resolve(null)
                    BLOCKED -> reject(IOException("Blocked by Cloudflare, open the title in WebView and try again"))
                    MAINTENANCE -> reject(IOException("Site is under maintenance, try again later"))
                }
            }
        }
        loadUrl(titleUrl)
    }

    private fun pressReadScript(bridge: String, label: String) = """
        (() => {
            if (window.top !== window || !location.pathname.startsWith('/manga/')) return;
            document.addEventListener('DOMContentLoaded', () => {
                // The first read tap opens an ad unless the site's timer, keyed by a hash of the UA and language, says one just did
                const raw = navigator.userAgent + navigator.language;
                let hash = 0;
                for (let i = 0; i < raw.length; i++) { hash = ((hash << 5) - hash) + raw.charCodeAt(i); hash |= 0; }
                localStorage.setItem('vi4' + Math.abs(hash), Date.now());
                // Wait for the site's own handler to restore the last read volume first, then pick ours
                setTimeout(() => {
                    const select = document.querySelector('select.vi13');
                    const button = document.querySelector('input.vi12');
                    const option = select && [...select.options].find(o => o.text === ${label.toJsonString()});
                    if (!option || !button) return window.$bridge.postMessage('missing');
                    select.value = option.value;
                    button.scrollIntoView({ block: 'center' });
                    setTimeout(() => {
                        const r = button.getBoundingClientRect();
                        window.$bridge.postMessage((r.left + r.width / 2) + ',' + (r.top + r.height / 2));
                    }, 300);
                }, 500);
            });
        })();
    """.trimIndent()

    private fun randomName() = List(12) { ('a'..'z').random() }.joinToString("")

    /**
     * Fetched in one request the way the site's PDF viewer does, as its cross-site call from the site: the file host
     * turns away requests from outside the site, and rate limits a burst of requests. The volume is written out as it
     * arrives, so its first pages can be read before the rest is in.
     */
    private suspend fun download(url: HttpUrl, out: OutputStream) = downloadLock.withLock {
        // Every request while rate limited makes the limit last longer, so none are sent until it's likely over
        if (System.currentTimeMillis() < preferences.getLong(RATE_LIMITED_UNTIL_KEY, 0)) throw IOException(RATE_LIMITED_MESSAGE)
        client.get(url, fetch = Fetch.XHR.from(baseUrl), ensureSuccess = false).use { response ->
            when (response.code) {
                200 -> withContext(Dispatchers.IO) { response.body.byteStream().use { it.copyTo(out) } }
                // The file host also answers a file that isn't there with 403, only without Cloudflare's block page
                403 -> throw if (response.body.string().contains("cloudflare", ignoreCase = true)) FileHostRefused() else VolumeMissing()
                429 -> {
                    preferences.edit().putLong(RATE_LIMITED_UNTIL_KEY, System.currentTimeMillis() + RATE_LIMIT_PAUSE.inWholeMilliseconds).apply()
                    throw IOException(RATE_LIMITED_MESSAGE)
                }
                else -> throw IOException("Volume download failed (HTTP ${response.code})")
            }
        }
    }

    /** Only a file that isn't named after its volume is kept, the folder covers the rest. */
    private fun remember(id: String, label: String, file: HttpUrl) {
        val folder = file.newBuilder().removePathSegment(file.pathSize - 1).addPathSegment("").build()
        preferences.edit()
            .apply {
                if (file.pathSegments.last() == "$label.pdf") remove(fileKey(id, label)) else putString(fileKey(id, label), file.toString())
            }
            .putString(folderKey(id), folder.toString())
            .apply()
    }

    private fun fileKey(id: String, label: String) = "file_$id/$label"

    private fun folderKey(id: String) = "folder_$id"

    private suspend fun loadPage(url: String): Document = client.get(url, Fetch.DOCUMENT).asJsoup()

    /**
     * Only the site and its challenge load: ads hijack the page. A viewer
     * run passes [onVolumeFile] to also let the reader library load and to catch its request for the volume file.
     */
    private fun WebViewScope<*>.setupSiteWebView(onVolumeFile: ((HttpUrl) -> Unit)? = null) {
        userAgent = headers["User-Agent"]!!
        blockImages = true
        // The firewall turns away requests that name the app, which WebView does unless told otherwise
        setOriginMatchedHeader("X-Requested-With", "", setOf(baseUrl, "https://*.$baseHost"))
        interceptRequest { request ->
            val url = request.url.toString().toHttpUrlOrNull() ?: return@interceptRequest emptyResponse()
            when {
                url.host == baseHost || url.host == "challenges.cloudflare.com" -> null
                onVolumeFile != null && url.host in VIEWER_LIBRARY_HOSTS -> null
                onVolumeFile != null && url.host.endsWith(".$baseHost") && url.pathSegments.last().substringAfterLast('.', "") !in ASSET_EXTENSIONS -> {
                    onVolumeFile(url)
                    emptyResponse()
                }
                else -> emptyResponse()
            }
        }
    }

    private fun emptyResponse() = WebResourceResponse(null, null, null)

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("キーワード検索時はフィルターは無視されます"),
        Filter.Header("カテゴリー選択時は並び替えは無視されます"),
        SortFilter(),
        CategoryFilter(),
    )

    companion object {
        private const val TRAP_ID = "4973674"
        private const val DECODE_ATTEMPTS = 3
        private const val BLOCKED = "__blocked__"
        private const val MAINTENANCE = "__maintenance__"
        private const val RATE_LIMITED_MESSAGE = "The file host is limiting downloads after too many in a short time, try again in an hour"
        private const val RATE_LIMITED_UNTIL_KEY = "rate_limited_until"
        private val RATE_LIMIT_PAUSE = 1.hours

        // decode.php redirects to the viewer with the volume file in its `file` parameter
        private const val DECODE_STATE_JS = """(() => {
            if (location.pathname.startsWith('/manga/')) return null;
            if (location.pathname === '/maintenance.html') return '$MAINTENANCE';
            if (document.readyState !== 'complete' || /Just a moment/.test(document.title)) return null;
            const nav = performance.getEntriesByType('navigation')[0];
            if (nav && nav.responseStatus === 429) return '429';
            if (/Attention Required/.test(document.title)) return '$BLOCKED';
            return location.href;
        })()"""

        private val ASSET_EXTENSIONS = setOf("js", "css", "png", "jpg", "jpeg", "gif", "svg", "webp", "ico", "woff", "woff2")

        // Script hosts the viewer loads its PDF reader from
        private val VIEWER_LIBRARY_HOSTS = setOf("cdnjs.cloudflare.com", "cdn.jsdelivr.net", "ajax.googleapis.com", "unpkg.com")

        private val SPACES_REGEX = Regex("""[　 ]""")
        private val HES_CONFIG_REGEX = Regex("""key\s*:\s*"([^"]+)"\s*,\s*randAttr\s*:\s*"([^"]+)"""")
        private val HIDDEN_CLASS_REGEX = Regex("""\.(category-\d+)\s*\{[^}]*?(?:display\s*:\s*none|opacity\s*:\s*0|(?<![-\w])width\s*:\s*0)""")
        private val TRAP_CLASS_REGEX = Regex("""querySelector\(\s*["']\.(category-\d+)\s+a["']""")
        private val TRAP_REGEX = Regex("""location\.href\s*=\s*['"]/manga/(\d+)""")
        private val MANGA_ID_REGEX = Regex("""/manga/(\d+)""")
    }
}

private class VolumeMissing : IOException("Volume not found")

/** The file host only lets a volume's file through with the pass decode.php hands out along with it. */
private class FileHostRefused : IOException("The file host turned the volume away, try again in a few minutes")
