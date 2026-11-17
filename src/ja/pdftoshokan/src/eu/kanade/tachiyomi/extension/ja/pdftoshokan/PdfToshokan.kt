package eu.kanade.tachiyomi.extension.ja.pdftoshokan

import android.util.Base64
import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import org.jsoup.nodes.Document
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

@Source
abstract class PdfToshokan : KeiSource() {

    private val preferences by getPreferencesLazy()

    private val store by lazy { VolumeStore(applicationContext.cacheDir.resolve("source_$id/volumes")) }

    private val baseHost get() = baseUrl.toHttpUrl().host

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = this
        .addInterceptor(PageInterceptor(store) { it.host.endsWith(".$baseHost") })
        .addInterceptor(ClientHintsInterceptor(baseUrl) { it.host == baseHost || it.host.endsWith(".$baseHost") })
        // TEMP DEBUG: remove before PR
        .addNetworkInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)
            if (request.url.host.endsWith(baseHost)) {
                val sent = request.headers.names().filterNot { it.equals("Cookie", true) }.joinToString { "$it=${request.header(it)}" }
                val cookies = request.header("Cookie")?.split(";")?.joinToString { it.substringBefore("=").trim() }
                android.util.Log.d(
                    "PdfToshokanDebug",
                    "${request.method} ${request.url} -> ${response.code} cf-mitigated=${response.header("cf-mitigated")} cookies=[$cookies] headers=[$sent]",
                )
            }
            response
        }

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
            val id = MANGA_ID_REGEX.find(xorHex(link.attr(linkAttr), linkKey))?.groupValues?.get(1)
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
        val details = parseDetails(document, manga.url)
        preferences.edit().putString(titleKey(manga.url), details.title).apply()
        return SMangaUpdate(details, chapterList)
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

        val cached = preferences.getString(fileKey(id, label), null)?.toHttpUrl()
        val (fileUrl, index) = cached?.let { url ->
            runCatching { url to loadIndex(url) }.getOrNull()
        } ?: run {
            val url = resolveFile(id, label, guessFromFolder = cached == null)
            url to loadIndex(url)
        }
        return List(index.pages.size) { Page(it, imageUrl = "$fileUrl#$it") }
    }

    private suspend fun loadIndex(url: HttpUrl): VolumeIndex {
        if (!store.contains(url)) store.save(url) { download(url, it) }
        return withContext(Dispatchers.IO) { store.index(url).second }
    }

    /**
     * decode.php sits behind Cloudflare's bot scoring, so it's the last resort. Volume files are
     * `<file host>/<prefix>/<folder>/<volume>.pdf`, with one prefix for the whole site and a folder
     * that is usually the title with its brackets spaced out, so a known or guessed folder is tried first.
     */
    private suspend fun resolveFile(id: String, label: String, guessFromFolder: Boolean): HttpUrl {
        if (guessFromFolder) {
            val folders = listOfNotNull(
                preferences.getString(folderKey(id), null),
                preferences.getString(titleKey(id), null)?.let { title ->
                    HttpUrl.Builder().scheme("https").host("n3.$baseHost")
                        .addPathSegment(preferences.getString(PREFIX_KEY, DEFAULT_PREFIX)!!)
                        .addPathSegment(title.replace(BRACKETS_REGEX, " ").replace(SPACES_RUN_REGEX, " ").trim())
                        .addPathSegment("")
                        .build().toString()
                },
            ).distinct()
            for (folder in folders) {
                val guess = folder.toHttpUrl().newBuilder().addPathSegment("$label.pdf").build()
                if (fetchGuess(guess)) return guess.also { remember(id, label, it) }
            }
        }

        return decode("$baseUrl/manga/$id", label).also { remember(id, label, it) }
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

    /** Downloads a guessed volume file straight away: the file host allows few requests, so there's no separate check. */
    private suspend fun fetchGuess(url: HttpUrl): Boolean = try {
        store.save(url) { download(url, it) }
        true
    } catch (_: VolumeMissing) {
        false
    }

    /**
     * Fetched in one request the way the site's PDF viewer does, and from one of the site's pages: the file host turns
     * away requests from outside the site, and blocks for a while after a burst of requests.
     */
    private suspend fun download(url: HttpUrl, file: File) = file.outputStream().buffered().use { out ->
        runInSite<Unit>(DOWNLOAD_TIMEOUT, downloadScript(url)) { message ->
            when {
                message == "done" -> resolve(Unit)
                message.startsWith("data:") -> out.write(Base64.decode(message.substring(5), Base64.DEFAULT))
                message == "404" -> reject(VolumeMissing())
                message == "403" || message == "429" -> reject(IOException("$RATE_LIMITED_MESSAGE (HTTP $message)"))
                else -> reject(IOException("Volume download failed (HTTP $message)"))
            }
        }
    }

    /** Runs [script] on a page of the site, with a bridge it posts its results to; `BRIDGE` in it is the bridge's name. */
    private suspend fun <T> runInSite(timeout: Duration, script: String, onMessage: WebViewScope<T>.(String) -> Unit): T = runWebView(timeout) {
        setupSiteWebView(allowFileHost = true)
        val bridge = randomName()
        webMessageBridge(bridge, setOf(baseUrl)) { message -> onMessage(message) }
        var started = false
        onPageFinished { url ->
            if (!started && url.toHttpUrlOrNull()?.encodedPath == "/robots.txt") {
                started = true
                evaluateJs(script.replace("BRIDGE", bridge))
            }
        }
        loadUrl("$baseUrl/robots.txt")
    }

    // Streamed out in pieces as it arrives, so a whole volume never sits in one bridge message
    private fun downloadScript(url: HttpUrl) = """
        (async () => {
            const response = await fetch(${url.toString().toJsonString()}, { credentials: 'include' });
            // The file host also answers a file that isn't there with 403, only without Cloudflare's block page
            if (response.status === 403 && !/cloudflare/i.test(await response.text())) return window.BRIDGE.postMessage('404');
            if (response.status !== 200) return window.BRIDGE.postMessage(String(response.status));
            const reader = response.body.getReader();
            let parts = [], size = 0;
            const flush = () => {
                const bytes = new Uint8Array(size);
                let at = 0;
                for (const part of parts) { bytes.set(part, at); at += part.length; }
                let binary = '';
                for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
                window.BRIDGE.postMessage('data:' + btoa(binary));
                parts = [];
                size = 0;
            };
            for (;;) {
                const { done, value } = await reader.read();
                if (done) break;
                parts.push(value);
                size += value.length;
                if (size >= $BRIDGE_PIECE) flush();
            }
            if (size > 0) flush();
            window.BRIDGE.postMessage('done');
        })().catch(() => window.BRIDGE.postMessage('0'));
    """.trimIndent()

    private fun remember(id: String, label: String, file: HttpUrl) {
        val folder = file.newBuilder().removePathSegment(file.pathSize - 1).addPathSegment("").build()
        preferences.edit()
            .putString(fileKey(id, label), file.toString())
            .putString(folderKey(id), folder.toString())
            .putString(PREFIX_KEY, file.pathSegments.first())
            .apply()
    }

    private fun fileKey(id: String, label: String) = "file_$id/$label"

    private fun folderKey(id: String) = "folder_$id"

    private fun titleKey(id: String) = "title_$id"

    private suspend fun loadPage(url: String): Document = client.get(url, Fetch.DOCUMENT).asJsoup()

    /**
     * Only the site and its challenge load, and the file host with [allowFileHost]: ads hijack the page. A viewer
     * run passes [onVolumeFile] to also let the reader library load and to catch its request for the volume file.
     */
    private fun WebViewScope<*>.setupSiteWebView(onVolumeFile: ((HttpUrl) -> Unit)? = null, allowFileHost: Boolean = false) {
        userAgent = headers["User-Agent"]!!
        blockImages = true
        // The firewall turns away requests that name the app, which WebView does unless told otherwise
        setOriginMatchedHeader("X-Requested-With", "", setOf(baseUrl, "https://*.$baseHost"))
        interceptRequest { request ->
            val url = request.url.toString().toHttpUrlOrNull() ?: return@interceptRequest emptyResponse()
            when {
                url.host == baseHost || url.host == "challenges.cloudflare.com" -> null
                allowFileHost && url.host.endsWith(".$baseHost") -> null
                onVolumeFile != null && url.host in VIEWER_LIBRARY_HOSTS -> null
                onVolumeFile != null && url.host.endsWith(".$baseHost") && url.pathSegments.last().substringAfterLast('.', "") !in ASSET_EXTENSIONS -> {
                    onVolumeFile(url)
                    emptyResponse()
                }
                else -> emptyResponse()
            }
        }
    }

    private fun emptyResponse() = WebResourceResponse("text/plain", null, 204, "No Content", null, ByteArrayInputStream(ByteArray(0)))

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("キーワード検索時はフィルターは無視されます"),
        Filter.Header("カテゴリー選択時は並び替えは無視されます"),
        SortFilter(),
        CategoryFilter(),
    )

    private fun xorHex(hex: String, key: String): String = buildString {
        for (i in 0 until hex.length / 2) {
            append((hex.substring(i * 2, i * 2 + 2).toInt(16) xor key[i % key.length].code).toChar())
        }
    }

    companion object {
        private const val TRAP_ID = "4973674"
        private const val DECODE_ATTEMPTS = 3
        private const val BLOCKED = "__blocked__"
        private const val MAINTENANCE = "__maintenance__"
        private const val PREFIX_KEY = "file_prefix"
        private const val BRIDGE_PIECE = 1024 * 1024
        private const val RATE_LIMITED_MESSAGE = "The file host only allows a few downloads in a short time, try again in a few minutes"
        private val DOWNLOAD_TIMEOUT = 10.minutes

        // As seen in the site's volume URLs in November 2026; replaced by whatever decode.php last returned
        private const val DEFAULT_PREFIX = "ghy6g4bujsw"

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

        private val BRACKETS_REGEX = Regex("""[（）()［］\[\]【】「」『』〈〉《》]""")
        private val SPACES_RUN_REGEX = Regex("""\s+""")
        private val SPACES_REGEX = Regex("""[　 ]""")
        private val HES_CONFIG_REGEX = Regex("""key\s*:\s*"([^"]+)"\s*,\s*randAttr\s*:\s*"([^"]+)"""")
        private val HIDDEN_CLASS_REGEX = Regex("""\.(category-\d+)\s*\{[^}]*?(?:display\s*:\s*none|opacity\s*:\s*0|(?<![-\w])width\s*:\s*0)""")
        private val TRAP_CLASS_REGEX = Regex("""querySelector\(\s*["']\.(category-\d+)\s+a["']""")
        private val TRAP_REGEX = Regex("""location\.href\s*=\s*['"]/manga/(\d+)""")
        private val MANGA_ID_REGEX = Regex("""/manga/(\d+)""")
    }
}

private class VolumeMissing : IOException("Volume not found")
