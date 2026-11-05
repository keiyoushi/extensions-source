package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.content.ComponentName
import android.content.Intent
import android.util.Base64
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.Fetch
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.source.KeiSource
import keiyoushi.utils.JSON_MEDIA_TYPE
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.inflate
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.nodes.Document
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TimeSource

@Source
abstract class ScanManga : KeiSource() {
    private val domain get() = baseUrl.toHttpUrl().topPrivateDomain()!!
    private val baseImageUrl get() = "https://static.$domain/img/manga"
    private val baseSearchUrl get() = "https://bqj.$domain/search/quick.json"

    private val stripEmptyXRequestedWith = Interceptor { chain ->
        val request = chain.request()
        val header = request.header("X-Requested-With")
        if (header != null && header.isEmpty()) {
            chain.proceed(request.newBuilder().removeHeader("X-Requested-With").build())
        } else {
            chain.proceed(request)
        }
    }

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addNetworkInterceptor(stripEmptyXRequestedWith)

    // The site's reader calls this API without cookies. A refusal fails right away instead of starting the host's
    // CloudflareInterceptor, whose solve can't use a clearance this client doesn't send.
    private val lelClient: OkHttpClient by lazy {
        client.newBuilder()
            .apply { interceptors().removeAll { it.javaClass.simpleName == "CloudflareInterceptor" } }
            .cookieJar(CookieJar.NO_COOKIES)
            .build()
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("accept-language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("X-Requested-With", "")

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/TOP-Manga-Webtoon-47.html", Fetch.DOCUMENT).asJsoup().select("#carouselTOPContainer div.top").map { element ->
            SManga.create().apply {
                val titleElement = element.selectFirst("a.atop")!!

                title = titleElement.text()
                setUrlWithoutDomain(titleElement.attr("href"))
                thumbnail_url = element.selectFirst("img")?.attr("data-original")
            }
        }

        return MangasPage(mangas, false)
    }

    // Latest
    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val mangas = client.get(baseUrl, Fetch.DOCUMENT).asJsoup().select("#content_news .publi").map { element ->
            SManga.create().apply {
                val mangaElement = element.selectFirst("a.l_manga")!!

                title = mangaElement.text()
                setUrlWithoutDomain(mangaElement.attr("href"))

                thumbnail_url = element.selectFirst("img")?.attr("src")
            }
        }

        return MangasPage(mangas, false)
    }

    // Search
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? {
        if (url.topPrivateDomain() != domain || !MANGA_PATH_REGEX.matches(url.encodedPath)) return null

        return mangaDetailsParse(client.get(baseUrl + url.encodedPath, Fetch.DOCUMENT).asJsoup()).apply {
            this.url = url.encodedPath
        }
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val url = baseSearchUrl.toHttpUrl().newBuilder()
            .addQueryParameter("term", query)
            .build()

        val searchHeaders = headers.newBuilder()
            .add("Content-type", "application/json; charset=UTF-8")
            .build()

        // No results come back as `[]` instead of an object
        val mangas = client.get(url, searchHeaders, Fetch.XHR).parseAs<MangaSearchDto> { if (it == "[]") """{"title":null}""" else it }
            .title?.map {
                SManga.create().apply {
                    title = it.nomMatch
                    setUrlWithoutDomain(it.url)
                    thumbnail_url = "$baseImageUrl/${it.image}"
                }
            }.orEmpty()

        return MangasPage(mangas, false)
    }

    // Details + chapters (same page)
    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val document = client.get(getMangaUrl(manga), Fetch.DOCUMENT).asJsoup()
        // chapterListParse throws for licensed series; don't let that break a details-only refresh
        return SMangaUpdate(
            mangaDetailsParse(document),
            if (fetchChapters) chapterListParse(document) else chapters,
        )
    }

    private fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document.selectFirst("meta[itemprop=name]")!!.attr("content")
        // The itemprop div only holds the "Auteur/Artiste" label
        author = document.selectFirst("div[itemprop=author]")?.parent()?.ownTextOrNull()
        description = document.selectFirst("div.titres_desc[itemprop=description]")?.text()
        genre = document.selectFirst("span[itemprop=genre]")?.let { demo ->
            (listOf(demo.text()) + demo.parent()?.ownText().orEmpty().split(","))
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .joinToString()
        }

        val statutText = document.selectFirst("div.titres_souspart:has(div:containsOwn(Statut))")?.ownText()?.lowercase().orEmpty()
        status = when {
            "en cours" in statutText -> SManga.ONGOING
            "terminé" in statutText -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }

        thumbnail_url = document.selectFirst("meta[itemprop=image]")?.absUrl("content")
    }

    private fun chapterListParse(document: Document): List<SChapter> {
        // Licensed series also list chapters hosted by external publishers (izneo, mangaplus, ono.live).
        val chapters = document.select("div.chapt_m:has(td.publimg a[href*=/lecture-en-ligne/])").map { element ->
            val linkEl = element.selectFirst("td.publimg span.i a")!!
            val titleEl = element.selectFirst("td.publititle")

            val chapterName = linkEl.text()
            val extraTitle = titleEl?.text()

            SChapter.create().apply {
                name = if (!extraTitle.isNullOrEmpty()) "$chapterName - $extraTitle" else chapterName
                setUrlWithoutDomain(linkEl.absUrl("href"))
            }
        }

        if (chapters.isEmpty() && document.selectFirst("div.chapt_m") != null) {
            val platforms = document.select("a[href*=/plateforme-]").map { it.text() }.distinct()
                .ifEmpty { listOf("le site de l'éditeur") }
                .joinToString()
            throw Exception("Licencié : chapitres disponibles uniquement sur $platforms")
        }

        return chapters
    }

    // Pages
    private val multipleSpaces = Regex("""\s+""")

    private fun dataAPI(data: String, idc: Int): UrlPayload {
        if (data.contains("error")) {
            error("Received error response from data API: ${multipleSpaces.replace(data, " ").trim()}")
        }

        val inflated = String(Base64.decode(data, Base64.NO_WRAP or Base64.NO_PADDING).inflate())

        // Remove trailing hex string and reverse
        val reversed = inflated.removeSuffix(idc.toString(16)).reversed()

        return String(Base64.decode(reversed, Base64.DEFAULT)).parseAs<UrlPayload>()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = getChapterUrl(chapter)
        val chapterId = chapterUrl.substringAfterLast('_').substringBefore('.').toInt()
        val lelCall = captureLelCall(chapterUrl, chapterId)
            ?: throw Exception("Challenge Cloudflare. Résolvez-le dans la WebView, fermez-la et réessayez.")

        // Sent from the app: from the chapter page this call needs a CORS preflight, which fails in a WebView
        val lelHeaders = headers.newBuilder()
            .apply { lelCall.headers.forEach { (name, value) -> set(name, value) } }
            .build()

        return lelClient.post(lelCall.url, lelHeaders, Fetch.XHR.from(chapterUrl), lelCall.body.toRequestBody(JSON_MEDIA_TYPE))
            .use { dataAPI(it.body.string(), chapterId) }
            .generateImageUrls()
            .map { Page(it.first, imageUrl = it.second) }
    }

    private val webViewMutex = Mutex()

    // A download queue fails its chapters within moments, which would otherwise load and show the challenge for each
    private var challengeRetryAt = TimeSource.Monotonic.markNow()

    /** Null while the user has to solve a challenge. */
    private suspend fun captureLelCall(chapterUrl: String, chapterId: Int): LelCall? = webViewMutex.withLock {
        if (challengeRetryAt.hasNotPassedNow()) return@withLock null

        val lelCall = readLelCall(chapterUrl, chapterId)
        if (lelCall == null) {
            challengeRetryAt = TimeSource.Monotonic.markNow() + 3.seconds
            openChallengeWebView(chapterUrl)
        }
        lelCall
    }

    /**
     * Cloudflare only accepts the reader's cf_clearance from a WebView, so the site's own reader runs in a hidden one
     * and the lel call it makes is captured. Null as soon as a challenge shows up.
     */
    private suspend fun readLelCall(chapterUrl: String, chapterId: Int): LelCall? {
        val bridge = List(16) { ('a'..'z').random() }.joinToString("")
        val origins = setOf("https://*.$domain")

        return runWebView<LelCall?>(45.seconds) {
            userAgent = headers["User-Agent"]!!
            blockImages = true
            // The site answers app WebViews with a fake 404 on every request carrying their default value
            if (!setOriginMatchedHeader("X-Requested-With", "", origins)) return@runWebView reject(Exception("Mettez à jour Android System WebView pour lire cette source."))
            webMessageBridge(bridge, origins) { message ->
                val lelCall = message.parseAs<LelCall>()
                // Third-party scripts share this page and can call the hook too
                if (lelCall.url.toHttpUrlOrNull()?.topPrivateDomain() == domain) resolve(lelCall)
            }
            injectAtDocumentStart(lelHook(bridge, chapterId), origins)
            onReceivedError { request, error ->
                if (request.isForMainFrame) reject(Exception(error.description.toString()))
            }
            poll {
                // On mature titles the reader only starts once this warning is accepted
                evaluateJs("""[...document.querySelectorAll(".oui-avertissement-btn")].find((button) => button.offsetParent)?.click()""")
                // "challenge", or the page's HTTP status once it has loaded: a challenge page is a 403 too
                evaluateJs("""window._cf_chl_opt ? "challenge" : document.readyState == "complete" && performance.getEntriesByType("navigation")[0].responseStatus""") {
                    if (it == "\"challenge\"") resolve(null)
                    if ((it.toIntOrNull() ?: 0) >= 400) reject(Exception("Le site a répondu HTTP $it pour ce chapitre."))
                }
            }
            loadUrl(chapterUrl)
        }
    }

    private fun lelHook(bridge: String, chapterId: Int) = """
        (function () {
            const original = window.fetch;
            window.fetch = function (input, init) {
                if (!String(input.url || input).includes("/lel/$chapterId.json")) return original.call(this, input, init);
                const request = new Request(input, init);
                request.text().then((body) => window.$bridge.postMessage(JSON.stringify({ url: request.url, headers: Object.fromEntries(request.headers), body })));
                return new Promise(() => {});
            };
        })();
    """.trimIndent()

    private fun openChallengeWebView(chapterUrl: String) {
        val intent = Intent().apply {
            component = ComponentName(applicationContext, "eu.kanade.tachiyomi.ui.webview.WebViewActivity")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra("url_key", chapterUrl)
            putExtra("source_key", id)
        }
        // Apps without this activity (Suwayomi etc.) still get the error message
        runCatching { applicationContext.startActivity(intent) }
    }

    // The reader downloads pages with fetch(); the image host blocks requests sent as images
    override val imageFetch get() = Fetch.XHR

    companion object {
        private val MANGA_PATH_REGEX = Regex("""/\d+(?:-\d+)?/[^/]+\.html""")
    }
}
