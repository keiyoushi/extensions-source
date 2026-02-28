package eu.kanade.tachiyomi.extension.en.kagane

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.en.kagane.wv.Cdm
import eu.kanade.tachiyomi.extension.en.kagane.wv.ProtectionSystemHeaderBox
import eu.kanade.tachiyomi.extension.en.kagane.wv.parsePssh
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
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonString
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okio.IOException
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Kagane :
    HttpSource(),
    ConfigurableSource {

    override val name = "Kagane"

    private val domain = "kagane.org"
    private val apiUrl = "https://yuzuki.$domain"
    override val baseUrl = "https://$domain"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(::refreshTokenInterceptor)
        // fix disk cache
        .apply {
            val index = networkInterceptors().indexOfFirst { it is BrotliInterceptor }
            if (index >= 0) interceptors().add(networkInterceptors().removeAt(index))
        }
        .build()

    private fun refreshTokenInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val url = request.url
        if (!url.queryParameterNames.contains("token")) {
            return chain.proceed(request)
        }

        val chapterId = url.pathSegments[4]

        var response = chain.proceed(
            request.newBuilder()
                .url(url.newBuilder().setQueryParameter("token", accessToken).build())
                .build(),
        )

        if (response.code == 401 || response.code == 507) {
            response.close()
            val challenge = try {
                getChallengeResponse(chapterId)
            } catch (_: Exception) {
                throw IOException("Failed to retrieve token")
            }
            accessToken = challenge.accessToken
            cacheUrl = challenge.cacheUrl
            response = chain.proceed(
                request.newBuilder()
                    .url(url.newBuilder().setQueryParameter("token", accessToken).build())
                    .build(),
            )
        }

        return response
    }

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int) = searchMangaRequest(
        page,
        "",
        FilterList(
            SortFilter(Filter.Sort.Selection(1, false)),
            ContentRatingFilter(
                preferences.contentRating.toSet(),
            ),
            GenresFilter(emptyList()),
        ),
    )

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(
        page,
        "",
        FilterList(
            SortFilter(Filter.Sort.Selection(6, false)),
            ContentRatingFilter(
                preferences.contentRating.toSet(),
            ),
            GenresFilter(emptyList()),
        ),
    )

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val body = buildJsonObject {
            if (query.isNotBlank()) {
                put("title", query)
            }

            val displayMode = preferences.sourceDisplayMode
            val sourceTypes = if (displayMode == "official") {
                listOf("Official")
            } else {
                listOf("Official", "Unofficial", "Mixed")
            }
            putJsonArray("source_type") {
                sourceTypes.forEach { add(it) }
            }

            var genresMatchAll: Boolean? = null
            var tagsMatchAll: Boolean? = null

            filters.forEach { filter ->
                when (filter) {
                    is MatchAllGenresFilter -> {
                        genresMatchAll = if (filter.state) true else null
                    }

                    is MatchAllTagsFilter -> {
                        tagsMatchAll = if (filter.state) true else null
                    }

                    else -> { }
                }
            }

            filters.forEach { filter ->
                when (filter) {
                    is GenresFilter -> {
                        val excludedGenreIds = preferences.excludedGenres.mapNotNull { genreName ->
                            metadata?.genres?.entries?.firstOrNull {
                                it.value.equals(genreName, ignoreCase = true)
                            }?.key
                        }
                        filter.addToJsonObject(this, "genres", excludedGenreIds, genresMatchAll)
                    }

                    is TagsSearchFilter -> {
                        val rawInput = filter.state.trim()
                        if (rawInput.isNotBlank()) {
                            val metadata = metadata
                            if (metadata != null) {
                                val tagEntries = rawInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }

                                val includeIds = mutableListOf<String>()
                                val excludeIds = mutableListOf<String>()

                                tagEntries.forEach { entry ->
                                    val isExclude = entry.startsWith("-")
                                    val tagName = if (isExclude) entry.removePrefix("-").trim() else entry
                                    val tagId = metadata.tags.entries.firstOrNull {
                                        it.value.equals(tagName, ignoreCase = true)
                                    }?.key
                                    if (tagId != null) {
                                        if (isExclude) excludeIds.add(tagId) else includeIds.add(tagId)
                                    }
                                }

                                if (includeIds.isNotEmpty() || excludeIds.isNotEmpty()) {
                                    putJsonObject("tags") {
                                        if (tagsMatchAll == true) {
                                            put("match_all", true)
                                        }
                                        if (includeIds.isNotEmpty()) {
                                            putJsonArray("values") {
                                                includeIds.forEach { add(it) }
                                            }
                                        }
                                        if (excludeIds.isNotEmpty()) {
                                            putJsonArray("exclude") {
                                                excludeIds.forEach { add(it) }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    is SourcesFilter -> {
                        filter.addToJsonObject(this)
                    }

                    is JsonFilter -> {
                        filter.addToJsonObject(this)
                    }

                    else -> {}
                }
            }
        }
            .toJsonString()
            .toRequestBody("application/json".toMediaType())

        val url = "$apiUrl/api/v2/search/series".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", (page - 1).toString())
            addQueryParameter("size", 35.toString()) // Default items per request
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        val sortParam = filter.toUriPart()
                        when {
                            sortParam.isNotEmpty() -> addQueryParameter("sort", sortParam)
                        }
                    }

                    else -> {}
                }
            }
        }

        return POST(url.toString(), headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchDto>()
        val sources = if (!preferences.showSource) {
            emptyMap()
        } else {
            metadata?.sources?.associate { it.sourceId to it.title }
                ?: try {
                    val sourceResponse = metadataClient.newCall(
                        POST(
                            "$apiUrl/api/v2/sources/list",
                            apiHeaders,
                            buildJsonObject { put("source_types", null) }.toJsonString()
                                .toRequestBody("application/json".toMediaType()),
                        ),
                    ).execute()

                    if (sourceResponse.isSuccessful) {
                        sourceResponse.parseAs<SourcesDto>().sources.associate { it.sourceId to it.title }
                    } else {
                        emptyMap()
                    }
                } catch (e: Exception) {
                    Log.w(name, "Failed to load sources", e)
                    emptyMap()
                }
        }
        val mangas = dto.content.map { it.toSManga(apiUrl, preferences.showSource, sources) }
        return MangasPage(mangas, hasNextPage = dto.hasNextPage())
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<DetailsDto>()
        val sourceName = dto.sourceId?.let { sourceId ->
            metadata?.sources?.firstOrNull { it.sourceId == sourceId }?.title
                ?: try {
                    metadataClient.newCall(
                        POST(
                            "$apiUrl/api/v2/sources/list",
                            apiHeaders,
                            buildJsonObject { put("source_types", null) }.toJsonString()
                                .toRequestBody("application/json".toMediaType()),
                        ),
                    ).execute().takeIf { it.isSuccessful }
                        ?.parseAs<SourcesDto>()?.sources
                        ?.firstOrNull { it.sourceId == sourceId }?.title
                } catch (_: Exception) {
                    null
                }
        }
        return dto.toSManga(sourceName, baseUrl)
    }

    override fun mangaDetailsRequest(manga: SManga): Request = mangaDetailsRequest(manga.url)

    private fun mangaDetailsRequest(seriesId: String): Request = GET("$apiUrl/api/v2/series/$seriesId", apiHeaders)

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesId = response.request.url.toString()
            .substringAfterLast("/")

        val dto = response.parseAs<DetailsDto>()

        val useSourceChapterNumber = dto.format in setOf(
            "Dark Horse Comics",
            "Flame Comics",
            "MangaDex",
            "Square Enix Manga",
        )

        return dto.seriesBooks.map { book ->
            book.toSChapter(seriesId, useSourceChapterNumber, preferences.chapterTitleMode)
        }.reversed()
    }

    override fun chapterListRequest(manga: SManga): Request = GET("$apiUrl/api/v2/series/${manga.url}", apiHeaders)

    // =============================== Pages ================================

    private val apiHeaders = headers.newBuilder().apply {
        add("Origin", baseUrl)
        add("Referer", "$baseUrl/")
    }.build()

    private fun getCertificate(url: String): String = client.newCall(GET(url, apiHeaders)).execute()
        .body.bytes()
        .toBase64()

    private val windvineCertificate by lazy { getCertificate("$apiUrl/api/v2/static/bin.bin") }

    private val fairPlayCertificate by lazy { getCertificate("$apiUrl/api/v2/static/crt.crt") }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        if (chapter.url.contains(";")) {
            throw Exception("Outdated chapter URL. Please refresh the chapter list")
        }

        val chapterId = "$baseUrl${chapter.url}".toHttpUrl().pathSegments.last()
        val challengeResp = getChallengeResponse(chapterId)

        accessToken = challengeResp.accessToken
        cacheUrl = challengeResp.cacheUrl

        val pages = challengeResp.pages.map { page ->
            val pageUrl = "$cacheUrl/api/v2/books/file".toHttpUrl().newBuilder().apply {
                addPathSegment(chapterId)
                addPathSegment(page.pageUuid)
                addQueryParameter("token", accessToken)
                addQueryParameter("is_datasaver", preferences.dataSaver.toString())
            }.build().toString()

            Page(page.pageNumber, url = pageUrl, imageUrl = pageUrl)
        }
        Log.d("pages", "${pages.map { it.imageUrl }}")
        return Observable.just(pages)
    }

    override fun imageUrlRequest(page: Page): Request = GET(page.imageUrl!!, apiHeaders)

    private var cacheUrl = "https://akari.$domain"
    private var accessToken: String = ""
    private var integrityToken: String = ""
    private var integrityExp = System.currentTimeMillis()

    private fun getIntegrityToken(): String {
        if (integrityExp < System.currentTimeMillis()) {
            val res = client.newCall(
                POST(
                    "https://kagane.org/api/integrity",
                    headers,
                    body = "".toRequestBody("application/json".toMediaType()),
                ),
            ).execute().parseAs<IntegrityDto>()
            integrityToken = res.token
            integrityExp = res.exp * 1000
        }

        return integrityToken
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun getChallengeResponse(chapterId: String): ChallengeDto {
        val integrityToken = getIntegrityToken()
        val f = ":$chapterId".sha256().sliceArray(0 until 16)

        val challenge = if (preferences.wvd.isNotBlank()) {
            getChallengeWvd(f)
        } else {
            getChallengeWebview(f, chapterId)
        }

        val challengeUrl =
            "$apiUrl/api/v2/books/$chapterId".toHttpUrl().newBuilder()
                .addQueryParameter("is_datasaver", preferences.dataSaver.toString())
                .build()
        val challengeBody = buildJsonObject {
            put("challenge", challenge)
        }.toJsonString().toRequestBody("application/json".toMediaType())

        val headers = apiHeaders.newBuilder().add("x-integrity-token", integrityToken).build()

        return client.newCall(POST(challengeUrl.toString(), headers, challengeBody)).execute()
            .parseAs<ChallengeDto>()
    }

    private fun getChallengeWvd(f: ByteArray): String {
        val cdm = Cdm.fromData(preferences.wvd)
        val pssh = parsePssh(getPssh(f)).content as? ProtectionSystemHeaderBox
            ?: throw Exception("Failed to parse pssh")
        return cdm.getLicenseChallenge(pssh)
    }

    private fun getChallengeWebview(f: ByteArray, chapterId: String): String {
        val interfaceName = "jsInterface"
        val html = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8">
                <title>Title</title>
            </head>
            <body>
                <script>
                    function detectDRMSupport() {
                        return "WebKitMediaKeys" in window ? "fairplay" : "MediaKeys" in window && "function" == typeof navigator.requestMediaKeySystemAccess ? "widevine" : null
                    }

                    function base64ToArrayBuffer(base64) {
                        var binaryString = atob(base64);
                        var bytes = new Uint8Array(binaryString.length);
                        for (var i = 0; i < binaryString.length; i++) {
                            bytes[i] = binaryString.charCodeAt(i);
                        }
                        return bytes.buffer;
                    }

                    async function getData() {
                        let widevine = detectDRMSupport() !== 'fairplay';
                        const g = base64ToArrayBuffer(widevine ? "$windvineCertificate" : "$fairPlayCertificate");
                        let t = widevine ? await navigator.requestMediaKeySystemAccess("com.widevine.alpha", [{
                            initDataTypes: ["cenc"],
                            audioCapabilities: [],
                            videoCapabilities: [{
                                contentType: 'video/mp4; codecs="avc1.42E01E"'
                            }]
                        }]) : await navigator.requestMediaKeySystemAccess("com.apple.fps", [{
                            initDataTypes: ["skd"],
                            audioCapabilities: [{
                                contentType: 'audio/mp4; codecs="mp4a.40.2"'
                            }],
                            videoCapabilities: [{
                                contentType: 'video/mp4; codecs="avc1.42E01E"'
                            }]
                        }]);

                        let e = await t.createMediaKeys();
                        await e.setServerCertificate(g);
                        let video = widevine ? null : document.createElement("video");
                        if (video) {
                            video.style.display = "none";
                            document.body.appendChild(video);
                            await video.setMediaKeys(e);
                        }
                        let n = e.createSession();
                        let i = new Promise((resolve, reject) => {
                          function onMessage(event) {
                            n.removeEventListener("message", onMessage);
                            if (video) {
                                document.body.removeChild(video)
                            }
                            resolve(event.message);
                          }

                          function onError() {
                            n.removeEventListener("error", onError);
                            reject(new Error("Failed to generate license challenge"));
                          }

                          n.addEventListener("message", onMessage);
                          n.addEventListener("error", onError);
                        });

                        if (widevine) {
                            await n.generateRequest("cenc", base64ToArrayBuffer("${getPssh(f).toBase64()}"));
                        } else {
                            let oo = base64ToArrayBuffer("${f.toBase64()}")
                            let c = Array.from(new Uint8Array(oo)).map(t => t.toString(16).padStart(2, "0")).join("");
                            let d = JSON.stringify({
                                uri: "skd://" + c,
                                assetId: "$chapterId",
                            });
                            const textEncoder = new TextEncoder();
                            await n.generateRequest("skd", textEncoder.encode(d));
                        }
                        let o = await i;
                        let m = new Uint8Array(o);
                        let v = btoa(String.fromCharCode(...m));
                        window.$interfaceName.passPayload(v);
                    }
                    getData();
                </script>
            </body>
            </html>
        """.trimIndent()

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch)
        var webView: WebView? = null

        handler.post {
            val innerWv = WebView(Injekt.get<Application>())

            webView = innerWv
            innerWv.settings.domStorageEnabled = true
            innerWv.settings.javaScriptEnabled = true
            innerWv.settings.blockNetworkImage = true
            innerWv.settings.userAgentString = headers["User-Agent"]
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            innerWv.webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    if (request?.resources?.contains(PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID) == true) {
                        request.grant(request.resources)
                    } else {
                        super.onPermissionRequest(request)
                    }
                }
            }

            innerWv.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
        }

        latch.await(10, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception("Timed out getting drm challenge")
        }

        if (jsInterface.challenge.isEmpty()) {
            throw Exception("Failed to get drm challenge")
        }

        return jsInterface.challenge
    }

    private fun concat(vararg arrays: ByteArray): ByteArray = arrays.reduce { acc, bytes -> acc + bytes }

    private fun getPssh(t: ByteArray): ByteArray {
        val e = Base64.decode("7e+LqXnWSs6jyCfc1R0h7Q==", Base64.DEFAULT)
        val zeroes = ByteArray(4)

        val i = byteArrayOf(18, t.size.toByte()) + t
        val s = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(i.size).array()

        val innerBox = concat(zeroes, e, s, i)
        val outerSize =
            ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(innerBox.size + 8).array()
        val psshHeader = "pssh".toByteArray(StandardCharsets.UTF_8)

        return concat(outerSize, psshHeader, innerBox)
    }

    internal class JsInterface(private val latch: CountDownLatch) {
        var challenge: String = ""

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passPayload(rawData: String) {
            try {
                challenge = rawData
                latch.countDown()
            } catch (_: Exception) {
                return
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ============================ Preferences =============================

    private val SharedPreferences.contentRating: List<String>
        get() {
            val maxRating = this.getString(CONTENT_RATING, CONTENT_RATING_DEFAULT)
            val index = CONTENT_RATINGS.indexOfFirst { it == maxRating }
            return CONTENT_RATINGS.slice(0..index.coerceAtLeast(0))
        }

    private val SharedPreferences.excludedGenres: Set<String>
        get() = this.getStringSet(GENRES_PREF, emptySet()) ?: emptySet()

    private val SharedPreferences.sourceDisplayMode: String
        get() = this.getString(SOURCE_DISPLAY_MODE, SOURCE_DISPLAY_MODE_DEFAULT) ?: SOURCE_DISPLAY_MODE_DEFAULT

    private val SharedPreferences.showSource: Boolean
        get() = this.getBoolean(SHOW_SOURCE, SHOW_SOURCE_DEFAULT)

    private val SharedPreferences.dataSaver
        get() = this.getBoolean(DATA_SAVER, false)

    private val SharedPreferences.wvd
        get() = this.getString(WVD_KEY, WVD_DEFAULT)!!

    private val SharedPreferences.chapterTitleMode
        get() = this.getString(CHAPTER_TITLE_MODE, CHAPTER_TITLE_MODE_DEFAULT)!!

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = CONTENT_RATING
            title = "Content Rating"
            entries =
                CONTENT_RATINGS.map { it.replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
            entryValues = CONTENT_RATINGS
            summary = "%s"
            setDefaultValue(CONTENT_RATING_DEFAULT)
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = GENRES_PREF
            title = "Exclude Genres"
            entries = GenresList.map { it.replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
            entryValues = GenresList
            summary =
                preferences.excludedGenres.joinToString { it.replaceFirstChar { c -> c.uppercase() } }
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                val selected = values as Set<String>
                this.summary = selected.joinToString { it.replaceFirstChar { c -> c.uppercase() } }
                true
            }
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = SOURCE_DISPLAY_MODE
            title = "Source Display Selection"
            summary = "%s"
            entries = arrayOf("Official Sources Only", "Show All (Official + Scanlations)")
            entryValues = arrayOf("official", "all")
            setDefaultValue(SOURCE_DISPLAY_MODE_DEFAULT)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_SOURCE
            title = "Show source name in title"
            setDefaultValue(SHOW_SOURCE_DEFAULT)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = DATA_SAVER
            title = "Data saver"
            setDefaultValue(false)
        }.let(screen::addPreference)

        EditTextPreference(screen.context).apply {
            key = WVD_KEY
            title = "WVD file"
            summary = "Enter contents as base64 string"
            setDefaultValue(WVD_DEFAULT)
        }.let(screen::addPreference)

        ListPreference(screen.context).apply {
            key = CHAPTER_TITLE_MODE
            title = "Chapter title format"
            entries = CHAPTER_TITLE_MODE_NAMES
            entryValues = CHAPTER_TITLE_MODES
            summary = "How the chapter title should be displayed"
            setDefaultValue(CHAPTER_TITLE_MODE_DEFAULT)
        }.let(screen::addPreference)
    }

    // ============================= Utilities ==============================

    companion object {
        private const val CONTENT_RATING = "pref_content_rating"
        private const val CONTENT_RATING_DEFAULT = "pornographic"
        internal val CONTENT_RATINGS = arrayOf(
            "safe",
            "suggestive",
            "erotica",
            "pornographic",
        )

        private const val GENRES_PREF = "pref_genres_exclude"

        private const val SOURCE_DISPLAY_MODE = "pref_source_display_mode"
        private const val SOURCE_DISPLAY_MODE_DEFAULT = "all"

        private const val SHOW_SOURCE = "pref_show_source"
        private const val SHOW_SOURCE_DEFAULT = false

        private const val DATA_SAVER = "data_saver_default"

        private const val WVD_KEY = "wvd_key"
        private const val WVD_DEFAULT = ""

        private const val CHAPTER_TITLE_MODE = "chapter_title_mode"
        private const val CHAPTER_TITLE_MODE_DEFAULT = "optional"
        internal val CHAPTER_TITLE_MODES = arrayOf(
            "optional",
            "always",
        )
        internal val CHAPTER_TITLE_MODE_NAMES = arrayOf(
            "Default (Hide numbers)",
            "Include chapter numbers",
        )
    }

    // ============================= Filters ==============================

    private var metadata: MetadataDto? = null
    private val metadataClient = client.newBuilder()
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder()
                .header("Cache-Control", "max-age=${24 * 60 * 60}")
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .build()
        }.build()

    override fun getFilterList(): FilterList {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
            ContentRatingFilter(
                preferences.contentRating.toSet(),
            ),
            Filter.Separator(),
        )

        fetchMetadata()

        val meta = metadata

        if (meta != null) {
            val displayMode = preferences.sourceDisplayMode

            val validSources = meta.sources.filter { source ->
                when (displayMode) {
                    "official" -> source.sourceType.equals("Official", ignoreCase = true)
                    else -> true
                }
            }
            val sourceFilters = validSources
                .map { FilterData(it.sourceId, it.title) }
                .sortedBy { it.name }

            filters.addAll(
                listOf(
                    MatchAllGenresFilter(),
                    GenresFilter(meta.getGenresList()),
                    MatchAllTagsFilter(),
                    TagsSearchFilter(),
                    SourcesFilter(sourceFilters),
                ),
            )
        } else {
            filters.add(0, Filter.Header("Press 'Reset' to load more filters"))
            filters.add(1, Filter.Separator())
        }

        return FilterList(filters)
    }

    private fun fetchMetadata() {
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val genreResponse = metadataClient.newCall(
                    GET("$apiUrl/api/v2/genres/list", apiHeaders),
                ).execute()
                val tagsResponse = metadataClient.newCall(
                    GET("$apiUrl/api/v2/tags/list", apiHeaders),
                ).execute()
                val sourcesResponse = metadataClient.newCall(
                    POST(
                        "$apiUrl/api/v2/sources/list",
                        apiHeaders,
                        buildJsonObject { put("source_types", null) }.toJsonString()
                            .toRequestBody("application/json".toMediaType()),
                    ),
                ).execute()

                if (genreResponse.isSuccessful && tagsResponse.isSuccessful && sourcesResponse.isSuccessful) {
                    val genres = genreResponse.parseAs<List<GenreDto>>().associate { it.id to it.genreName }
                    val tags = tagsResponse.parseAs<List<TagDto>>().associate { it.id to it.tagName }
                    val sources = sourcesResponse.parseAs<SourcesDto>().sources

                    metadata = MetadataDto(genres, tags, sources)
                    Log.d(name, "Metadata fetched and updated")
                } else {
                    Log.e(name, "Failed to fetch metadata: One or more requests failed")
                }
            } catch (e: Exception) {
                Log.e(name, "Failed to fetch metadata", e)
            }
        }
    }
}
