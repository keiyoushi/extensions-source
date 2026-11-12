package eu.kanade.tachiyomi.extension.pt.sakuramangas

import android.util.Base64
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.NativeWebViewApi
import keiyoushi.utils.WebViewScope
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeMark
import kotlin.time.TimeSource

@Source
abstract class SakuraMangas : KeiSource() {

    private val host get() = baseUrl.toHttpUrl().host

    private val origin get() = "https://$host"

    override fun OkHttpClient.Builder.configureClient() = apply {
        rateLimit(2) { it.host == host }
        cronet()
    }

    // Our WebView shows the Cloudflare check to the user, so the app's own solver is left out
    private val pageClient by lazy {
        client.newBuilder().apply {
            interceptors().removeAll { it.javaClass.simpleName == "CloudflareInterceptor" }
        }.build()
    }

    private val documentHeaders get() = headersBuilder().removeAll("X-Requested-With").build()

    private val xhrHeaders get() = headersBuilder().set("X-Requested-With", "XMLHttpRequest").set("Referer", "$baseUrl/").build()

    private val chapterHeaders = ConcurrentHashMap<String, Headers>()

    override suspend fun getPopularManga(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val result = runPage("$baseUrl/obras/") { runScript("browse.js", "{}") }.parseAs<BrowseResult>()
        return MangasPage(result.cards.map { it.toSManga() }, false)
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        if (page > 1) return MangasPage(emptyList(), false)
        val home = pageClient.get("$baseUrl/", documentHeaders, Fetch.DOCUMENT).use { it.body.string() }
        val token = CSRF_REGEX.find(home)!!.groupValues[1]
        val body = FormBody.Builder().add("csrf_home", token).build()
        val entries = pageClient.post("$baseUrl$LATEST_PATH", xhrHeaders, Fetch.XHR, body).parseAs<List<LatestDto>>()
        return MangasPage(entries.map { it.toSManga(baseUrl) }.distinctBy { it.url }, false)
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = query.trim().toHttpUrlOrNull()
        if (url != null && url.host.removePrefix("www.") == host) return getMangasByUrl(url, page)
        if (page > 1 || query.isBlank()) return MangasPage(emptyList(), false)
        val options = """{ "query": ${quoteJs(query.trim())} }"""
        val result = runPage("$baseUrl/obras/") { runScript("browse.js", options) }.parseAs<BrowseResult>()
        return MangasPage(result.cards.map { it.toSManga() }, false)
    }

    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        val slug = url.pathSegments.takeIf { it.firstOrNull() == "obras" }?.getOrNull(1)?.ifEmpty { null } ?: return null
        val manga = SManga.create().apply { this.url = "/obras/$slug" }
        return getMangaUpdate(manga, emptyList(), fetchDetails = true, fetchChapters = false).manga
    }

    // Details and chapters come from the same page, so both are read in one load
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val options = """{ "withChapters": $fetchChapters }"""
        val result = runPage(getMangaUrl(manga)) { runScript("manga.js", options) }.parseAs<MangaResult>()
        val details = result.details!!.toSManga(manga.url)
        if (!fetchChapters) return SMangaUpdate(details, chapters)
        if (!result.complete) throw Exception("A lista de capítulos não carregou por completo")
        return SMangaUpdate(details, result.chapters.map { it.toSChapter() })
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        chapterPacer.waitForSlot()
        val chapterUrl = getChapterUrl(chapter)
        val capture = captureReader(chapterUrl)
        // Recent chapters are stored as encrypted .bin files that only the site's reader decrypts
        if (capture.extension == "bin") throw Exception("Este capítulo usa imagens criptografadas, ainda não suportadas")
        chapterHeaders[chapterUrl] = capture.headers
        val count = countPages(capture.folder, capture.extension, capture.lastRequested, capture.headers)
        return (1..count).map { Page(it - 1, url = chapterUrl, imageUrl = "${capture.folder}/${it.pad()}.${capture.extension}") }
    }

    override fun getImageRequest(page: Page): Request {
        val headers = chapterHeaders[page.url] ?: return super.getImageRequest(page)
        return GET(page.imageUrl!!, headers)
    }

    private class ReaderCapture(val folder: String, val extension: String, val headers: Headers, val lastRequested: Int)

    // The reader asks for its first images with headers it builds itself (x-sakura, delivery-mode and a daily
    // x-realtime-auth); the image server only answers requests that carry them, so they are taken from those requests
    private suspend fun captureReader(chapterUrl: String): ReaderCapture {
        val images = CopyOnWriteArrayList<Pair<String, Map<String, String>>>()
        var firstImageAt: TimeMark? = null
        runPage(chapterUrl) {
            interceptRequest { request ->
                if (request.url.host == host && request.url.path.orEmpty().startsWith("/imagens/")) {
                    images += request.url.toString() to request.requestHeaders.toMap()
                    if (firstImageAt == null) firstImageAt = TimeSource.Monotonic.markNow()
                    pixel()
                } else {
                    handle(request)
                }
            }
            onGate { resolve ->
                if (firstImageAt?.let { it.elapsedNow() > READER_SETTLE } == true) resolve()
            }
        }
        val first = images.firstOrNull() ?: throw Exception("O leitor do site não carregou nenhuma página")
        val headers = Headers.Builder().apply {
            first.second.forEach { (key, value) -> if (!key.equals("Referer", true)) set(key, value) }
        }.build()
        val lastRequested = images.mapNotNull { it.first.substringAfterLast('/').substringBefore('.').toIntOrNull() }.max()
        val file = first.first.substringAfterLast('/')
        return ReaderCapture(first.first.substringBeforeLast('/'), file.substringAfterLast('.', "jpg"), headers, lastRequested)
    }

    // Pages are numbered with no gaps, so the first number that isn't an image ends the chapter. Each is asked for one
    // byte, in small batches; the site answers a missing page with a redirect, which isn't followed
    private suspend fun countPages(folder: String, extension: String, lastRequested: Int, headers: Headers): Int = coroutineScope {
        val probeClient = pageClient.newBuilder().followRedirects(false).followSslRedirects(false).build()
        val ranged = headers.newBuilder().set("Range", "bytes=0-0").build()
        var count = lastRequested
        var next = lastRequested + 1
        while (next <= MAX_PAGES) {
            val batch = (next until minOf(next + PROBE_BATCH, MAX_PAGES + 1)).map { number ->
                async { number to isImage(probeClient, "$folder/${number.pad()}.$extension", ranged) }
            }.awaitAll()
            for ((number, image) in batch) {
                if (!image) return@coroutineScope count
                count = number
            }
            next += PROBE_BATCH
            delay(PROBE_INTERVAL)
        }
        count
    }

    private suspend fun isImage(client: OkHttpClient, url: String, headers: Headers) = client.get(url, headers, Fetch.NONE, ensureSuccess = false).use {
        it.code in 200..299 && it.header("Content-Type").orEmpty().startsWith("image/")
    }

    private class PageOutcome(val payload: String?, val rateLimited: Boolean)

    /** One WebView run: what it intercepts, when the page is ready, and the page script it runs. */
    private inner class PageScope(val scope: WebViewScope<PageOutcome>) {
        var interceptor: ((WebResourceRequest) -> WebResourceResponse?)? = null
        var ready: ((() -> Unit) -> Unit)? = null
        var bootstrap: String? = null
        var reloadAfterClearance = false

        fun interceptRequest(block: (WebResourceRequest) -> WebResourceResponse?) {
            interceptor = block
        }

        fun onGate(block: (() -> Unit) -> Unit) {
            ready = block
        }

        fun runScript(asset: String, options: String) {
            bootstrap = """
                (() => {
                    if (window.__sakuraStarted) return;
                    window.__sakuraStarted = true;
                    const done = (message) => { try { window.$DONE_BRIDGE.postMessage(message); } catch (e) {} };
                    const tap = (x, y) => { try { window.$TAP_BRIDGE.postMessage(x + "," + y); } catch (e) {} };
                    const type = (text) => { try { window.$TYPE_BRIDGE.postMessage(text); } catch (e) {} };
                    (${readAsset(asset)})(done, tap, $options, type);
                })();
            """.trimIndent()
        }

        fun pixel() = WebResourceResponse("image/gif", null, ByteArrayInputStream(PIXEL))

        private fun empty() = WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))

        fun handle(request: WebResourceRequest): WebResourceResponse? {
            val requestHost = request.url.host
            val path = request.url.path.orEmpty()
            return when {
                requestHost == host && STYLE_REGEX.containsMatchIn(path) -> empty()
                requestHost == host && IMAGE_REGEX.containsMatchIn(path) -> pixel()
                // The check ends with a posted page; reloaded as a GET so it doesn't carry the WebView's X-Requested-With
                requestHost == host && request.isForMainFrame && request.method != "GET" -> {
                    reloadAfterClearance = true
                    empty()
                }
                requestHost == host -> scope.proxyRequest(request, pageClient) { removeHeader("X-Requested-With") }
                requestHost == null || requestHost == CLOUDFLARE_HOST -> null
                else -> empty()
            }
        }
    }

    @OptIn(NativeWebViewApi::class)
    private suspend fun runPage(url: String, configure: PageScope.() -> Unit): String {
        var overlay: ChallengeOverlay? = null
        var challengeSince: TimeMark? = null
        var flushed = false
        val clearanceAtStart = clearance()
        val outcome = try {
            runWebView<PageOutcome>(timeout = PAGE_TIMEOUT) {
                val page = PageScope(this).apply(configure)
                val check = ChallengeOverlay { reject(IOException("A verificação foi fechada antes de terminar")) }
                overlay = check
                userAgent = headers["User-Agent"]!!
                // WebView adds X-Requested-With with the app's package, which the site blocks; its own XHRs send this
                setOriginMatchedHeader("X-Requested-With", "XMLHttpRequest", setOf(origin))
                webMessageBridge(DONE_BRIDGE, setOf(origin)) { resolve(PageOutcome(it, rateLimited = false)) }
                webMessageBridge(TAP_BRIDGE, setOf(origin)) { message ->
                    val (x, y) = message.split(',').map { it.toFloat() }
                    tap(x, y)
                }
                webMessageBridge(TYPE_BRIDGE, setOf(origin)) { typeText(it) }
                interceptRequest { request -> page.interceptor?.invoke(request) ?: page.handle(request) }
                // The page scripts tap and type, which needs a WebView that draws; an offscreen surface gives it one
                if (page.bootstrap != null) withNativeWebView { BackgroundSurface.host(it) }

                poll(POLL_INTERVAL) {
                    evaluateJs(GATE) { out ->
                        val gate = out.parseAs<String>().parseAs<Gate>()
                        val current = clearance()
                        val fresh = current != null && current != clearanceAtStart
                        // Stored at once so a new cf_clearance outlives an app that is closed right after
                        if (fresh && !flushed) {
                            flushed = true
                            CookieManager.getInstance().flush()
                        }
                        when {
                            gate.captcha || gate.challenge -> {
                                val since = challengeSince ?: TimeSource.Monotonic.markNow().also { challengeSince = it }
                                if (gate.captcha || since.elapsedNow() > CHALLENGE_WAIT) {
                                    withNativeWebView { check.show(it) }
                                }
                            }
                            gate.rate -> resolve(PageOutcome(null, rateLimited = true))
                            check.isShowing -> {
                                challengeSince = null
                                check.dismiss()
                                if (page.bootstrap != null) withNativeWebView { BackgroundSurface.host(it) }
                            }
                        }
                        if (page.reloadAfterClearance && fresh) {
                            page.reloadAfterClearance = false
                            evaluateJs("location.replace(${quoteJs(url)})")
                        }
                        page.ready?.invoke { resolve(PageOutcome(null, rateLimited = false)) }
                    }
                }

                injectAtDocumentStart(page.bootstrap.orEmpty(), setOf(origin))
                loadUrl(url)
            }
        } finally {
            withContext(Dispatchers.Main) { overlay?.dismiss() }
        }
        if (outcome.rateLimited) throw IOException("O site detectou atividade incomum. Aguarde alguns minutos e tente de novo.")
        return outcome.payload.orEmpty()
    }

    private fun clearance(): String? = CookieManager.getInstance().getCookie(baseUrl)
        ?.split(';')?.map { it.trim() }?.firstOrNull { it.startsWith("cf_clearance=") }

    private fun readAsset(name: String) = javaClass.getResource("/assets/$name")!!.readText()

    private fun Int.pad() = toString().padStart(3, '0')

    // One chapter opened per minute: the site blocks an address that opens chapters too often
    private val chapterPacer = Pacer(1.minutes)

    private class Pacer(private val interval: kotlin.time.Duration) {
        private val mutex = Mutex()
        private var last: TimeMark? = null

        suspend fun waitForSlot() = mutex.withLock {
            last?.let { mark -> (interval - mark.elapsedNow()).takeIf { it.isPositive() }?.let { delay(it) } }
            last = TimeSource.Monotonic.markNow()
        }
    }

    companion object {
        private const val LATEST_PATH = "/dist/sakura/models/home/__.home_ultimo.php"
        private const val CLOUDFLARE_HOST = "challenges.cloudflare.com"
        private const val MAX_PAGES = 300
        private const val PROBE_BATCH = 4

        private const val DONE_BRIDGE = "sakuraDone"
        private const val TAP_BRIDGE = "sakuraTap"
        private const val TYPE_BRIDGE = "sakuraType"

        private val PIXEL = Base64.decode("R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7", Base64.DEFAULT)
        private val STYLE_REGEX = Regex("""\.(css|woff2?|ttf|otf)$""")
        private val IMAGE_REGEX = Regex("""\.(jpe?g|png|webp|gif|avif|ico)$""")
        private val CSRF_REGEX = Regex("""<meta\s+(?:name="csrf-token"\s+content|csrf-token)="([0-9a-f]+)"""")

        private val POLL_INTERVAL = 500.milliseconds
        private val PAGE_TIMEOUT = 2.minutes
        private val CHALLENGE_WAIT = 5.seconds
        private val READER_SETTLE = 1500.milliseconds
        private val PROBE_INTERVAL = 250.milliseconds

        private const val GATE = """(() => {
            const bar = document.getElementById('rate-limit-challenge');
            const style = bar ? getComputedStyle(bar) : null;
            const body = document.body ? document.body.innerText : '';
            return JSON.stringify({
                challenge: /Just a moment/.test(document.title),
                captcha: !!style && style.display !== 'none' && style.visibility !== 'hidden',
                rate: /atividade incomum/i.test(body),
            });
        })()"""

        private fun quoteJs(value: String): String = buildString {
            append('"')
            for (c in value) {
                when (c) {
                    '\\' -> append("\\\\")
                    '"' -> append("\\\"")
                    '\n' -> append("\\n")
                    '<' -> append("\\u003c")
                    else -> append(c)
                }
            }
            append('"')
        }
    }
}
