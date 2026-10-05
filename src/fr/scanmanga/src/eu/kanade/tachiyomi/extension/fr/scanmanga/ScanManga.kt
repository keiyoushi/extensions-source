package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.annotation.Source
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import rx.Observable
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.zip.Inflater

@Source
abstract class ScanManga :
    HttpSource(),
    ConfigurableSource {

    private val domain = baseUrl.toHttpUrl().topPrivateDomain()!!
    private val baseImageUrl = "https://static.$domain/img/manga"
    private val baseSearchUrl = "https://bqj.$domain/search/quick.json"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    private val stripEmptyXRequestedWith = Interceptor { chain ->
        val request = chain.request()
        val header = request.header("X-Requested-With")
        if (header != null && header.isEmpty()) {
            chain.proceed(request.newBuilder().removeHeader("X-Requested-With").build())
        } else {
            chain.proceed(request)
        }
    }

    override val client = super.client.newBuilder()
        .addNetworkInterceptor(stripEmptyXRequestedWith)
        .build()

    // Reader-page fetches reuse the app client (cache, gzip, DoH, cookie jar, etc.) but strip
    // the host's CloudflareInterceptor — that interceptor wastes ~30 s per call trying its own
    // headless solve before throwing, blowing up our polling.
    private val readerClient: OkHttpClient by lazy {
        client.newBuilder()
            .apply { interceptors().removeAll { it.javaClass.simpleName == "CloudflareInterceptor" } }
            .build()
    }

    // static.scan-manga.com answers 403 to covers requested without a Referer
    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("upgrade-insecure-requests", "1")
        .add("sec-fetch-site", "none")
        .add("accept-language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("X-Requested-With", "")

    // Browse/search pages get a Cloudflare 403 with a browser-like `accept`, but the reader
    // (chapter page, lel API, images) answers 503 without it.
    private val readerHeaders by lazy {
        headers.newBuilder()
            .add(
                "accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            )
            .build()
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/TOP-Manga-Webtoon-47.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select("#carouselTOPContainer div.top").map { element ->
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
    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()

        val mangas = document.select("#content_news .publi").map { element ->
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
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        val url = query.toHttpUrlOrNull() ?: return super.fetchSearchManga(page, query, filters)
        if (url.topPrivateDomain() != domain || !MANGA_PATH_REGEX.matches(url.encodedPath)) {
            return Observable.just(MangasPage(emptyList(), false))
        }

        val manga = SManga.create().apply { this.url = url.encodedPath }
        return fetchMangaDetails(manga).map {
            it.url = manga.url
            it.initialized = true
            MangasPage(listOf(it), false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseSearchUrl
            .toHttpUrl().newBuilder()
            .addQueryParameter("term", query)
            .build()
            .toString()

        val newHeaders = headers.newBuilder()
            .add("Content-type", "application/json; charset=UTF-8")
            .build()

        return GET(url, newHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val json = response.body.string()
        if (json == "[]") {
            return MangasPage(emptyList(), false)
        }

        return MangasPage(
            json.parseAs<MangaSearchDto>().title?.map {
                SManga.create().apply {
                    title = it.nom_match
                    setUrlWithoutDomain(it.url)
                    thumbnail_url = "$baseImageUrl/${it.image}"
                }
            } ?: emptyList(),
            false,
        )
    }

    // Details
    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()

        return SManga.create().apply {
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
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
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
    private fun decodeHunter(obfuscatedJs: String): String {
        val (encoded, mask, intervalStr, optionStr) = HUNTER_OBFUSCATION_REGEX.find(obfuscatedJs)?.destructured
            ?: error("Failed to match obfuscation pattern")

        val interval = intervalStr.toInt()
        val option = optionStr.toInt()
        val delimiter = mask[option]
        val tokens = encoded.split(delimiter).filter { it.isNotEmpty() }
        val reversedMap = mask.withIndex().associate { it.value to it.index }

        return buildString {
            for (token in tokens) {
                // Reverse the hashIt() operation: convert masked characters back to digits
                val digitString = token.map { c ->
                    reversedMap[c]?.toString() ?: error("Invalid masked character: $c")
                }.joinToString("")

                // Convert from base `option` to decimal
                val number = digitString.toIntOrNull(option)
                    ?: error("Failed to parse token: $digitString as base $option")

                // Reverse the shift done during encodeIt()
                val originalCharCode = number - interval

                append(originalCharCode.toChar())
            }
        }
    }

    private val multipleSpaces = Regex("""\s+""")

    private fun dataAPI(data: String, idc: Int): UrlPayload {
        if (data.contains("error")) {
            error("Received error response from data API: ${multipleSpaces.replace(data, " ").trim()}")
        }

        // Step 1: Base64 decode the input
        val compressedBytes = Base64.decode(data, Base64.NO_WRAP or Base64.NO_PADDING)

        // Step 2: Inflate (zlib decompress)
        val inflater = Inflater()
        inflater.setInput(compressedBytes)
        val outputBuffer = ByteArray(512 * 1024)
        val decompressedLength = inflater.inflate(outputBuffer)
        inflater.end()

        val inflated = String(outputBuffer, 0, decompressedLength)

        // Step 3: Remove trailing hex string and reverse
        val hexIdc = idc.toString(16)
        val cleaned = inflated.removeSuffix(hexIdc)
        val reversed = cleaned.reversed()

        // Step 4: Base64 decode and parse JSON
        val finalJsonStr = String(Base64.decode(reversed, Base64.DEFAULT))

        return finalJsonStr.parseAs<UrlPayload>()
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        val context = applicationContext
        val chapterUrl = "$baseUrl${chapter.url}"
        val isReader = Exception().stackTrace.any { it.className.contains("reader") }

        fun fetch(): String? = try {
            readerClient.newCall(GET(chapterUrl, readerHeaders)).execute().use { resp ->
                resp.body.string().takeIf { CHAPTER_INFO_REGEX.containsMatchIn(it) }
            }
        } catch (_: Exception) {
            null
        }

        var body = fetch()
        if (body == null) {
            // Cold-session path: CF refuses to issue cf_clearance to a session that's
            // never touched the host. Warm up by loading the homepage in a hidden WV,
            // then re-probe — usually clears it without ever needing WebViewActivity.
            warmupWebViewSession()
            body = fetch()
        }
        if (body == null) {
            try {
                val intent = Intent().apply {
                    component = ComponentName(context, "eu.kanade.tachiyomi.ui.webview.WebViewActivity")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra("url_key", chapterUrl)
                    putExtra("source_key", id)
                    putExtra("title_key", "Résolvez le challenge Cloudflare, fermez la WebView et réouvrez le chapitre.")
                }
                context.startActivity(intent)
            } catch (_: Exception) {
                throw Exception("Résolvez le challenge Cloudflare depuis la WebView puis réouvrez le chapitre.")
            }

            for (attempt in 1..CF_MAX_POLLS) {
                Thread.sleep(CF_POLL_INTERVAL_MS)
                body = fetch()
                if (body != null) {
                    val closeIntent = Intent().apply {
                        val target = if (isReader) {
                            "eu.kanade.tachiyomi.ui.reader.ReaderActivity"
                        } else {
                            "eu.kanade.tachiyomi.ui.main.MainActivity"
                        }
                        component = ComponentName(context, target)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    context.startActivity(closeIntent)
                    break
                }
            }
            if (body == null) {
                // WV flow exhausted itself; the warmup we did wasn't enough either.
                // Clear the gate so the next attempt warms again from scratch.
                sessionWarmedUp.set(false)
                throw Exception("Résolvez le challenge Cloudflare, fermez la WebView et réouvrez le chapitre.")
            }
        }

        return Observable.just(parsePageList(Jsoup.parse(body, chapterUrl)))
    }

    private val sessionWarmedUp = AtomicBoolean(false)

    @SuppressLint("SetJavaScriptEnabled")
    private fun warmupWebViewSession() {
        if (!sessionWarmedUp.compareAndSet(false, true)) return

        val latch = CountDownLatch(1)
        val mainHandler = Handler(Looper.getMainLooper())

        mainHandler.post {
            val wv = WebView(applicationContext)
            wv.settings.javaScriptEnabled = true
            wv.settings.domStorageEnabled = true

            val cm = android.webkit.CookieManager.getInstance()
            cm.setAcceptCookie(true)
            cm.setAcceptThirdPartyCookies(wv, true)

            wv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Schedule teardown on the main looper directly — view.postDelayed
                    // is silently dropped because the WebView isn't attached to a window.
                    // The settle window lets CF's Turnstile beacon commit cf_clearance.
                    mainHandler.postDelayed({
                        runCatching {
                            view?.stopLoading()
                            view?.destroy()
                        }
                        latch.countDown()
                    }, WARMUP_SETTLE_MS)
                }
            }
            wv.loadUrl("$baseUrl/")
        }

        try {
            if (!latch.await(WARMUP_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                sessionWarmedUp.set(false)
            }
        } catch (_: InterruptedException) {
            sessionWarmedUp.set(false)
        }
    }

    override fun pageListParse(response: Response): List<Page> = parsePageList(response.asJsoup())

    private fun parsePageList(document: org.jsoup.nodes.Document): List<Page> {
        val packedScript = document.selectFirst(PACKED_SCRIPT_SELECTOR)!!.data()
        val unpackedScript = decodeHunter(packedScript)

        val (sml) = SML_PARAM_REGEX.find(unpackedScript)?.destructured
            ?: error("Failed to extract sml parameter.")

        val (sme) = SME_PARAM_REGEX.find(unpackedScript)?.destructured
            ?: error("Failed to extract sme parameter.")

        val (chapterId) = CHAPTER_INFO_REGEX.find(packedScript)?.destructured
            ?: error("Failed to extract chapter ID.")

        val availableVariables = mapOf(
            "sme" to sme,
            "sml" to sml,
            "fingerprint" to getFingerprint(),
            "chapterId" to chapterId,
            "topDomain" to (baseUrl.toHttpUrl().topPrivateDomain() ?: ""),
        )

        val mediaType = "application/json; charset=UTF-8".toMediaType()
        val documentUrl = document.baseUri().toHttpUrl()

        val requestBody = injectVariables(REQUEST_BODY, availableVariables)
        val pageListUrl = injectVariables(PAGE_LIST_URL, availableVariables)
        val requestHeaders = readerHeaders.newBuilder()
            .add("Origin", "${documentUrl.scheme}://${documentUrl.host}")
            .set("Referer", documentUrl.toString())
            .add("Token", LEL_TOKEN)
            .build()

        val pageListRequest = POST(
            url = pageListUrl,
            headers = requestHeaders,
            body = requestBody.toRequestBody(mediaType),
        )

        val lelResponse = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()
            .newCall(pageListRequest).execute().use { response ->
                if (!response.isSuccessful) {
                    error("Unexpected error while fetching lel. HTTP ${response.code}")
                }
                dataAPI(response.body.string(), chapterId.toInt())
            }

        return lelResponse.generateImageUrls().map { Page(it.first, imageUrl = it.second) }
    }

    // Page
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = readerHeaders.newBuilder()
            .add("Origin", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getFingerprint(): String {
        var currentValue = preferences.getString("gpu_renderer", null)

        if (currentValue.isNullOrEmpty()) {
            val latch = CountDownLatch(1)
            var returnValue = "SUMK"

            Handler(Looper.getMainLooper()).post {
                val webView = WebView(applicationContext)
                webView.settings.javaScriptEnabled = true

                webView.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        val script = """
                        (function() {
                            try {
                                const canvas = document.createElement("canvas");
                                const gl = canvas.getContext("webgl");
                                const debugInfo = gl ? gl.getExtension("WEBGL_debug_renderer_info") : null;
                                const gpu = debugInfo ? gl.getParameter(debugInfo.UNMASKED_RENDERER_WEBGL) : "IC";

                                return btoa(gpu);
                            } catch (e) {
                                return btoa("IC");
                            }
                        })();
                        """.trimIndent()

                        view?.evaluateJavascript(script) {
                            returnValue = it?.removeSurrounding("\"") ?: "SUMK"
                            view.stopLoading()
                            view.destroy()
                            latch.countDown()
                        }
                    }
                }
                webView.loadUrl("about:blank")
            }

            try {
                latch.await(5, TimeUnit.SECONDS)
            } catch (_: InterruptedException) {
            }

            val decodedValue = String(Base64.decode(returnValue, Base64.DEFAULT))

            preferences.edit().putString("gpu_renderer", decodedValue).apply()
            currentValue = decodedValue
        }

        return Base64.encodeToString(
            """{"gpu":"$currentValue","connection":"cellular"}""".toByteArray(),
            Base64.NO_WRAP,
        )
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = "gpu_renderer"
            title = "Unmasked GPU renderer"
            summary =
                "Set and cache your GPU renderer string here to bypass fingerprint-based blocking. You can find your GPU renderer by visiting a site like https://www.browserleaks.com/webgl. Make sure to enter the exact string as shown on the site, without any extra spaces or characters and use Google Chrome on Android."
            setDefaultValue(null)
            dialogTitle = "GPU Renderer"
            dialogMessage =
                "Enter your GPU renderer string here. This is used to bypass blocking based on WebGL fingerprinting. You can find your GPU renderer by visiting a site like https://www.browserleaks.com/webgl using Google Chrome on Android. Make sure to enter the exact string as shown on the site, without any extra spaces or characters."

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putString(key, newValue as String).commit()
            }
        }.also { screen.addPreference(it) }
    }

    private fun injectVariables(template: String, variables: Map<String, String>): String {
        var result = template
        for ((key, value) in variables) {
            result = result.replace("{$key}", value)
        }
        return result
    }

    companion object {
        private const val PACKED_SCRIPT_SELECTOR = "script:containsData(eval\\(function \\()"
        private val HUNTER_OBFUSCATION_REGEX = Regex("""eval\s*\(\s*function\s*\(\s*\w\s*,\s*\w\s*,\s*\w\s*,\s*\w\s*,\s*\w\s*,\s*\w\s*(?:,\s*[^)]+)?\)\s*\{\s*.*?\s*\}\s*\(\s*"([^"]+)"\s*,\s*\d+\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*\d+\s*\)\s*\)""")
        private val SML_PARAM_REGEX = Regex("""sml\s*=\s*'([^']+)'""")
        private val SME_PARAM_REGEX = Regex("""sme\s*=\s*'([^']+)'""")
        private val CHAPTER_INFO_REGEX = Regex("""const idc = (\d+)""")
        private val MANGA_PATH_REGEX = Regex("""/\d+(?:-\d+)?/[^/]+\.html""")
        private const val PAGE_LIST_URL = "https://bqj.{topDomain}/lel/{chapterId}.json"
        private const val REQUEST_BODY = """{"a":"{sme}","b":"{sml}","c":"{fingerprint}"}"""
        private const val LEL_TOKEN = "yf"
        private const val CF_POLL_INTERVAL_MS = 5000L
        private const val CF_MAX_POLLS = 15
        private const val WARMUP_SETTLE_MS = 200L
        private const val WARMUP_TIMEOUT_SECONDS = 8L
    }
}
