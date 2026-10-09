package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.content.ComponentName
import android.content.Intent
import android.util.Base64
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
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
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.inflate
import keiyoushi.utils.ownTextOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.toJsonRequestBody
import kotlinx.coroutines.delay
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@Source
abstract class ScanManga :
    KeiSource(),
    ConfigurableSource {

    private val domain get() = baseUrl.toHttpUrl().topPrivateDomain()!!
    private val baseImageUrl get() = "https://static.$domain/img/manga"
    private val baseSearchUrl get() = "https://bqj.$domain/search/quick.json"

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

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addNetworkInterceptor(stripEmptyXRequestedWith)

    // Reader-page fetches reuse the app client (cache, gzip, DoH, cookie jar, etc.) but strip
    // the host's CloudflareInterceptor — that interceptor wastes ~30 s per call trying its own
    // headless solve before throwing, blowing up our polling.
    private val readerClient: OkHttpClient by lazy {
        client.newBuilder()
            .apply { interceptors().removeAll { it.javaClass.simpleName == "CloudflareInterceptor" } }
            .build()
    }

    override fun Headers.Builder.configureHeaders(): Headers.Builder = add("upgrade-insecure-requests", "1")
        .add("sec-fetch-site", "none")
        .add("accept-language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("X-Requested-With", "")

    // Browse/search pages get a Cloudflare 403 with a browser-like `accept`, but the reader
    // (chapter page, lel API, images) answers 503 without it.
    private val readerHeaders: Headers
        get() = headersBuilder()
            .add(
                "accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            )
            .build()

    // Popular
    override suspend fun getPopularManga(page: Int): MangasPage {
        val mangas = client.get("$baseUrl/TOP-Manga-Webtoon-47.html").asJsoup().select("#carouselTOPContainer div.top").map { element ->
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
        val mangas = client.get(baseUrl).asJsoup().select("#content_news .publi").map { element ->
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

        return mangaDetailsParse(client.get(baseUrl + url.encodedPath).asJsoup()).apply {
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
        val mangas = client.get(url, searchHeaders).parseAs<MangaSearchDto> { if (it == "[]") """{"title":null}""" else it }
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
        val document = client.get(getMangaUrl(manga)).asJsoup()
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

        val inflated = String(Base64.decode(data, Base64.NO_WRAP or Base64.NO_PADDING).inflate())

        // Remove trailing hex string and reverse
        val reversed = inflated.removeSuffix(idc.toString(16)).reversed()

        return String(Base64.decode(reversed, Base64.DEFAULT)).parseAs<UrlPayload>()
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        // Must run before the first suspension point, otherwise the caller's frames are gone
        val isReader = Exception().stackTrace.any { it.className.contains("reader") }
        val context = applicationContext
        val chapterUrl = getChapterUrl(chapter)

        suspend fun fetch(): String? = try {
            readerClient.get(chapterUrl, readerHeaders, ensureSuccess = false).use { resp ->
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
                delay(CF_POLL_INTERVAL)
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

        return parsePageList(body.asJsoup(chapterUrl))
    }

    private val sessionWarmedUp = AtomicBoolean(false)

    private suspend fun warmupWebViewSession() {
        if (!sessionWarmedUp.compareAndSet(false, true)) return

        try {
            runWebView<Unit>(WARMUP_TIMEOUT) {
                // The settle window lets CF's Turnstile beacon commit cf_clearance.
                onPageFinished { poll(WARMUP_SETTLE) { resolve(Unit) } }
                loadUrl("$baseUrl/")
            }
        } catch (_: Exception) {
            sessionWarmedUp.set(false)
        }
    }

    private suspend fun parsePageList(document: Document): List<Page> {
        val packedScript = document.selectFirst(PACKED_SCRIPT_SELECTOR)!!.data()
        val unpackedScript = decodeHunter(packedScript)

        val (sml) = SML_PARAM_REGEX.find(unpackedScript)?.destructured
            ?: error("Failed to extract sml parameter.")

        val (sme) = SME_PARAM_REGEX.find(unpackedScript)?.destructured
            ?: error("Failed to extract sme parameter.")

        val (chapterId) = CHAPTER_INFO_REGEX.find(packedScript)?.destructured
            ?: error("Failed to extract chapter ID.")

        val documentUrl = document.baseUri().toHttpUrl()
        val requestHeaders = readerHeaders.newBuilder()
            .set("Origin", "${documentUrl.scheme}://${documentUrl.host}")
            .set("Referer", documentUrl.toString())
            .add("Token", LEL_TOKEN)
            .build()

        val lelResponse = client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()
            .post(
                "https://bqj.$domain/lel/$chapterId.json",
                requestHeaders,
                LelRequestDto(sme, sml, getFingerprint()).toJsonRequestBody(),
            )
            .use { dataAPI(it.body.string(), chapterId.toInt()) }

        return lelResponse.generateImageUrls().map { Page(it.first, imageUrl = it.second) }
    }

    override fun imageRequest(page: Page): Request = GET(page.imageUrl!!, readerHeaders)

    private suspend fun getFingerprint(): String {
        var currentValue = preferences.getString("gpu_renderer", null)

        if (currentValue.isNullOrEmpty()) {
            val returnValue = try {
                runWebView<String>(5.seconds) {
                    onPageFinished {
                        evaluateJs(FINGERPRINT_SCRIPT) { resolve(it.removeSurrounding("\"")) }
                    }
                    loadUrl("about:blank")
                }
            } catch (_: Exception) {
                "SUMK"
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
        }.also { screen.addPreference(it) }
    }

    companion object {
        private const val PACKED_SCRIPT_SELECTOR = "script:containsData(eval\\(function \\()"
        private val HUNTER_OBFUSCATION_REGEX =
            Regex("""eval\s*\(\s*function\s*\(\s*\w\s*,\s*\w\s*,\s*\w\s*,\s*\w\s*,\s*\w\s*,\s*\w\s*(?:,\s*[^)]+)?\)\s*\{\s*.*?\s*\}\s*\(\s*"([^"]+)"\s*,\s*\d+\s*,\s*"([^"]+)"\s*,\s*(\d+)\s*,\s*(\d+)\s*,\s*\d+\s*\)\s*\)""")
        private val SML_PARAM_REGEX = Regex("""sml\s*=\s*'([^']+)'""")
        private val SME_PARAM_REGEX = Regex("""sme\s*=\s*'([^']+)'""")
        private val CHAPTER_INFO_REGEX = Regex("""const idc = (\d+)""")
        private val MANGA_PATH_REGEX = Regex("""/\d+(?:-\d+)?/[^/]+\.html""")
        private const val LEL_TOKEN = "yf"
        private val CF_POLL_INTERVAL = 5.seconds
        private const val CF_MAX_POLLS = 15
        private val WARMUP_SETTLE = 200.milliseconds
        private val WARMUP_TIMEOUT = 8.seconds

        private val FINGERPRINT_SCRIPT = """
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
    }
}
