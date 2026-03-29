package eu.kanade.tachiyomi.extension.fr.scanmanga

import android.annotation.SuppressLint
import android.app.Application
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
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import okhttp3.CookieJar
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.zip.Inflater

class ScanManga :
    HttpSource(),
    ConfigurableSource {
    override val name = "Scan-Manga"

    override val baseUrl = "https://m.scan-manga.com"
    private val baseImageUrl = "https://static.scan-manga.com/img/manga"

    override val lang = "fr"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override val client = super.client.newBuilder()
        .addNetworkInterceptor { chain ->
            val originalRequest = chain.request()
            val header = originalRequest.header("X-Requested-With")
            if (header != null && header.isEmpty()) {
                return@addNetworkInterceptor chain.proceed(
                    originalRequest.newBuilder()
                        .removeHeader("X-Requested-With")
                        .build(),
                )
            }
            return@addNetworkInterceptor chain.proceed(originalRequest)
        }
        .build()

    override fun headersBuilder(): Headers.Builder {
        val currentChromeVersion = super.headersBuilder().build().get("User-Agent")?.let {
            Regex("Chrome/(\\d+)").find(it)?.groupValues?.get(1)?.toIntOrNull()
        } ?: 145

        return Headers.Builder()
//            .add(
//                "sec-ch-ua",
//                "\"Not:A-Brand\";v=\"99\", \"Google Chrome\";v=\"$currentChromeVersion\", \"Chromium\";v=\"$currentChromeVersion\"",
//            )
//            .add("sec-ch-ua-mobile", "?1")
//            .add("sec-ch-ua-platform", "\"Android\"")
            .add("upgrade-insecure-requests", "1")
            .add(
                "user-agent",
                "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/$currentChromeVersion.0.0.0 Mobile Safari/537.36",
            )
            .add(
                "accept",
                "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            )
            .add("sec-fetch-site", "none")
//            .add("sec-fetch-mode", "navigate")
//            .add("sec-fetch-user", "?1")
//            .add("sec-fetch-dest", "document")
            .add("accept-language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7")
//            .add("priority", "u=0, i")
            .add("X-Requested-With", "")
    }

    // Popular
    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/TOP-Manga-Webtoon-45.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.asJsoup().select("#carouselTOPContainer > div.top").map { element ->
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
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/api/search/quick.json"
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
            title = document.select("h1.main_title[itemprop=name]").text()
            author = document.select("div[itemprop=author]").text()
            description = document.selectFirst("div.titres_desc[itemprop=description]")?.text()
            genre = document.selectFirst("div.titres_souspart span[itemprop=genre]")?.text()

            val statutText = document.selectFirst("div.titres_souspart")?.ownText()
            status = when {
                statutText?.contains("En cours", ignoreCase = true) == true -> SManga.ONGOING
                statutText?.contains("Terminé", ignoreCase = true) == true -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            thumbnail_url = document.select("div.full_img_serie img[itemprop=image]").attr("src")
        }
    }

    // Chapters
    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.chapt_m").map { element ->
            val linkEl = element.selectFirst("td.publimg span.i a")!!
            val titleEl = element.selectFirst("td.publititle")

            val chapterName = linkEl.text()
            val extraTitle = titleEl?.text()

            SChapter.create().apply {
                name = if (!extraTitle.isNullOrEmpty()) "$chapterName - $extraTitle" else chapterName
                setUrlWithoutDomain(linkEl.absUrl("href"))
            }
        }
    }

    // Pages
    private fun decodeHunter(obfuscatedJs: String): String {
        val markers = markerManager::getMarkers

        val (encoded, mask, intervalStr, optionStr) = runSafe {
            Regex(markers().regexes.hunterObfuscation).find(obfuscatedJs)?.destructured
                ?: error("Failed to match pattern")
        }

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
        val outputBuffer = ByteArray(512 * 1024) // 512 KB buffer, should be more than enough
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

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val markers = markerManager::getMarkers

        val packedScript = runSafe { document.selectFirst(markers().selectors.packedScript)!!.data() }
        val unpackedScript = decodeHunter(packedScript)

        // parametersRegex
        val (sml) = runSafe {
            Regex(markers().regexes.smlParam).find(unpackedScript)?.destructured
                ?: error("Failed to extract sml parameter.")
        }

        val (sme) = runSafe {
            Regex(markers().regexes.smeParam).find(unpackedScript)?.destructured
                ?: error("Failed to extract sme parameter.")
        }

        val (chapterId) = runSafe {
            Regex(markers().regexes.chapterInfo).find(packedScript)?.destructured
                ?: error("Failed to extract chapter ID.")
        }

        val availableVariables = mapOf(
            "sme" to sme,
            "sml" to sml,
            "fingerprint" to getFingerprint(),
            "chapterId" to chapterId,
            "topDomain" to (baseUrl.toHttpUrl().topPrivateDomain() ?: ""),
        )

        val mediaType = "application/json; charset=UTF-8".toMediaType()
        val documentUrl = document.baseUri().toHttpUrl()

        val lelResponse = runSafe {
            val requestBody = injectVariables(markers().apiConfig.requestBody, availableVariables)
            val pageListUrl = injectVariables(markers().apiConfig.pageListUrl, availableVariables)
            val requestHeaders = headers.newBuilder()
                .add("Origin", "${documentUrl.scheme}://${documentUrl.host}")
                .add("Referer", documentUrl.toString())
                .apply {
                    markers().apiConfig.headers?.forEach { (key, value) ->
                        add(key, injectVariables(value, availableVariables))
                    }
                }
                .build()

            val pageListRequest = POST(
                url = pageListUrl,
                headers = requestHeaders,
                body = requestBody.toRequestBody(mediaType),
            )

            client.newBuilder().cookieJar(CookieJar.NO_COOKIES).build()
                .newCall(pageListRequest).execute().use { response ->
                    if (!response.isSuccessful) {
                        error("Unexpected error while fetching lel. HTTP ${response.code}")
                    }
                    dataAPI(response.body.string(), chapterId.toInt())
                }
        }

        return lelResponse.generateImageUrls().map { Page(it.first, imageUrl = it.second) }
    }

    // Page
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val imgHeaders = headers.newBuilder()
            .add("Origin", baseUrl)
            .build()

        return GET(page.imageUrl!!, imgHeaders)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getFingerprint(): String {
        var currentValue = preferences.getString("gpu_renderer", null)

        if (currentValue.isNullOrEmpty()) {
            val latch = CountDownLatch(1)
            var returnValue = "SUMK" // Default to "IC" if something goes wrong

            Handler(Looper.getMainLooper()).post {
                val webView = WebView(Injekt.get<Application>())
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
                            returnValue = it?.removeSurrounding("\"") ?: "SUMK" // btoa("IC") = "SUMK"
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

        EditTextPreference(screen.context).apply {
            key = MarkerManager.PREF_MARKERS_JSON
            title = "Debug: Markers JSON"
            summary =
                "For debugging purposes. Displays the raw JSON string of the markers used for decoding obfuscated scripts. Automatically updated when markers are refreshed."

            setDefaultValue(null)
            dialogTitle = "Markers JSON"
            dialogMessage =
                "This is the raw JSON string of the markers used for decoding obfuscated scripts. It is automatically updated when markers are refreshed. You can use this information for debugging purposes."

            setOnPreferenceChangeListener { _, _ ->
                // Do not allow manual changes, this is for display only
                false
            }
        }.also { screen.addPreference(it) }
    }

    private val markerManager by lazy { MarkerManager(client, preferences) }

    private fun injectVariables(template: String, variables: Map<String, String>): String {
        var result = template
        for ((key, value) in variables) {
            result = result.replace("{$key}", value)
        }
        return result
    }

    fun <T> runSafe(fn: () -> T): T = runCatching { fn() }.getOrElse {
        markerManager.fetchWithRetry()

        // Second attempt
        runCatching { fn() }.getOrElse { throwable ->
            markerManager.handleFatalFailure(throwable)
        }
    }
}
