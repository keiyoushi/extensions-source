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
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.extension.en.kagane.ChapterDto.Companion.dateFormat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.await
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
import keiyoushi.utils.tryParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.CacheControl
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.brotli.BrotliInterceptor
import okhttp3.internal.closeQuietly
import okio.IOException
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.charset.StandardCharsets
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Kagane : HttpSource(), ConfigurableSource {

    override val name = "Kagane"

    private val domain = "kagane.org"
    private val apiUrl = "https://api.$domain"
    override val baseUrl = "https://$domain"

    override val lang = "en"

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
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

        val seriesId = url.pathSegments[3]
        val chapterId = url.pathSegments[5]

        var response = chain.proceed(
            request.newBuilder()
                .url(url.newBuilder().setQueryParameter("token", accessToken).build())
                .build(),
        )

        if (response.code == 401 || response.code == 507) {
            response.close()
            val challenge = try {
                getChallengeResponse(seriesId, chapterId)
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

    override fun popularMangaRequest(page: Int) =
        searchMangaRequest(
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

    override fun latestUpdatesRequest(page: Int) =
        searchMangaRequest(
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
            filters.forEach { filter ->
                when (filter) {
                    is GenresFilter -> {
                        filter.addToJsonObject(this, preferences.excludedGenres.toList())
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

        val url = "$apiUrl/api/v1/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("page", (page - 1).toString())
            addQueryParameter("size", 35.toString()) // Default items per request
            if (query.isNotBlank()) {
                addQueryParameter("name", query)
            }
            filters.forEach { filter ->
                when (filter) {
                    is SortFilter -> {
                        filter.toUriPart().takeIf { it.isNotEmpty() }
                            ?.let { uriPart -> addQueryParameter("sort", uriPart) }
                            ?: run {
                                if (query.isBlank()) {
                                    addQueryParameter("sort", "updated_at,desc")
                                }
                            }
                    }

                    else -> {}
                }
            }
            addQueryParameter("scanlations", preferences.showScanlations.toString())
        }

        return POST(url.toString(), headers, body)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<SearchDto>()
        val mangas = dto.content.filter {
            if (!preferences.showDuplicates) {
                val alternateSeries = client.newCall(GET("$apiUrl/api/v1/alternate_series/${it.id}", apiHeaders))
                    .execute()
                    .parseAs<List<AlternateSeries>>()

                if (alternateSeries.isEmpty()) return@filter true

                val date = dateFormat.tryParse(it.releaseDate)
                for (alt in alternateSeries) {
                    val altDate = dateFormat.tryParse(alt.releaseDate)

                    when {
                        it.booksCount < alt.booksCount -> return@filter false
                        it.booksCount == alt.booksCount -> {
                            if (date > altDate) return@filter false
                        }
                    }
                }
                true
            } else {
                true
            }
        }.map { it.toSManga(apiUrl, preferences.showSource) }
        return MangasPage(mangas, hasNextPage = dto.hasNextPage())
    }

    // =========================== Manga Details ============================

    override fun mangaDetailsParse(response: Response): SManga {
        val dto = response.parseAs<DetailsDto>()
        return dto.toSManga()
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        return mangaDetailsRequest(manga.url)
    }

    private fun mangaDetailsRequest(seriesId: String): Request {
        return GET("$apiUrl/api/v1/series/$seriesId", apiHeaders)
    }

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl/series/${manga.url}"
    }

    // ============================== Chapters ==============================

    override fun chapterListParse(response: Response): List<SChapter> {
        val seriesId = response.request.url.toString()
            .substringAfterLast("/")

        val dto = response.parseAs<ChapterDto>()

        val source = runCatching {
            client.newCall(mangaDetailsRequest(seriesId))
                .execute()
                .parseAs<DetailsDto>()
                .source
        }.getOrDefault("")
        val useSourceChapterNumber = source in setOf(
            "Dark Horse Comics",
            "Flame Comics",
            "MangaDex",
            "Square Enix Manga",
        )

        return dto.content.map { it -> it.toSChapter(useSourceChapterNumber) }.reversed()
    }

    override fun chapterListRequest(manga: SManga): Request {
        return GET("$apiUrl/api/v1/books/${manga.url}", apiHeaders)
    }

    // =============================== Pages ================================

    private val apiHeaders = headers.newBuilder().apply {
        add("Origin", baseUrl)
        add("Referer", "$baseUrl/")
    }.build()

    private fun getCertificate(url: String): String {
        return client.newCall(GET(url, apiHeaders)).execute()
            .body.bytes()
            .toBase64()
    }

    private val windvineCertificate by lazy { getCertificate("$apiUrl/api/v1/static/bin.bin") }

    private val fairPlayCertificate by lazy { getCertificate("$apiUrl/api/v1/static/crt.crt") }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        if (chapter.url.count { it == ';' } != 2) throw Exception("Chapter url error, please refresh chapter list.")
        var (seriesId, chapterId, pageCount) = chapter.url.split(";")

        val challengeResp = getChallengeResponse(seriesId, chapterId)
        accessToken = challengeResp.accessToken
        cacheUrl = challengeResp.cacheUrl
        if (preferences.dataSaver) {
            chapterId = chapterId + "_ds"
        }

        val pages = (0 until pageCount.toInt()).map { page ->
            val pageUrl = "$cacheUrl/api/v1/books".toHttpUrl().newBuilder().apply {
                addPathSegment(seriesId)
                addPathSegment("file")
                addPathSegment(chapterId)
                addPathSegment(challengeResp.pageMapping.getValue(page + 1))
                addQueryParameter("token", accessToken)
                addQueryParameter("index", (page + 1).toString())
            }.build().toString()

            Page(page, imageUrl = pageUrl)
        }

        return Observable.just(pages)
    }

    private var cacheUrl = "https://yukine.$domain"
    private var accessToken: String = ""

    @SuppressLint("SetJavaScriptEnabled")
    private fun getChallengeResponse(seriesId: String, chapterId: String): ChallengeDto {
        val f = "$seriesId:$chapterId".sha256().sliceArray(0 until 16)

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

        val challengeUrl =
            "$apiUrl/api/v1/books/$seriesId/file/$chapterId".toHttpUrl().newBuilder().apply {
                if (preferences.dataSaver) {
                    addQueryParameter("datasaver", true.toString())
                }
            }.build()
        val challengeBody = buildJsonObject {
            put("challenge", jsInterface.challenge)
        }.toJsonString().toRequestBody("application/json".toMediaType())

        return client.newCall(POST(challengeUrl.toString(), apiHeaders, challengeBody)).execute()
            .parseAs<ChallengeDto>()
    }

    private fun concat(vararg arrays: ByteArray): ByteArray =
        arrays.reduce { acc, bytes -> acc + bytes }

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

    override fun pageListParse(response: Response): List<Page> {
        throw UnsupportedOperationException()
    }

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // ============================ Preferences =============================

    private val SharedPreferences.contentRating: List<String>
        get() {
            val maxRating = this.getString(CONTENT_RATING, CONTENT_RATING_DEFAULT)
            val index = CONTENT_RATINGS.indexOfFirst { it == maxRating }
            return CONTENT_RATINGS.slice(0..index.coerceAtLeast(0))
        }

    private val SharedPreferences.excludedGenres: Set<String>
        get() = this.getStringSet(GENRES_PREF, emptySet()) ?: emptySet()

    private val SharedPreferences.showScanlations: Boolean
        get() = this.getBoolean(SHOW_SCANLATIONS, SHOW_SCANLATIONS_DEFAULT)

    private val SharedPreferences.showSource: Boolean
        get() = this.getBoolean(SHOW_SOURCE, SHOW_SOURCE_DEFAULT)

    private val SharedPreferences.showDuplicates: Boolean
        get() = this.getBoolean(SHOW_DUPLICATES, SHOW_DUPLICATES_DEFAULT)

    private val SharedPreferences.dataSaver
        get() = this.getBoolean(DATA_SAVER, false)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = CONTENT_RATING
            title = "Content Rating"
            entries = CONTENT_RATINGS.map { it.replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
            entryValues = CONTENT_RATINGS
            summary = "%s"
            setDefaultValue(CONTENT_RATING_DEFAULT)
        }.let(screen::addPreference)

        MultiSelectListPreference(screen.context).apply {
            key = GENRES_PREF
            title = "Exclude Genres"
            entries = GenresList.map { it.replaceFirstChar { c -> c.uppercase() } }.toTypedArray()
            entryValues = GenresList
            summary = preferences.excludedGenres.joinToString { it.replaceFirstChar { c -> c.uppercase() } }
            setDefaultValue(emptySet<String>())

            setOnPreferenceChangeListener { _, values ->
                val selected = values as Set<String>
                this.summary = selected.joinToString { it.replaceFirstChar { c -> c.uppercase() } }
                true
            }
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_SCANLATIONS
            title = "Show scanlations"
            setDefaultValue(SHOW_SCANLATIONS_DEFAULT)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_DUPLICATES
            title = "Show duplicates"
            summary = "Show duplicate entries.\nPicks the entry with most chapters if disabled\nThis switch isn't always accurate\nNOTE: Enabling this option will slow your search speed down"
            setDefaultValue(SHOW_DUPLICATES_DEFAULT)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_SOURCE
            title = "Show source name"
            summary = "Show source name in title"
            setDefaultValue(SHOW_SOURCE_DEFAULT)
        }.let(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = DATA_SAVER
            title = "Data saver"
            setDefaultValue(false)
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
        private const val SHOW_SCANLATIONS = "pref_show_scanlations"
        private const val SHOW_SCANLATIONS_DEFAULT = true

        private const val SHOW_SOURCE = "pref_show_source"
        private const val SHOW_SOURCE_DEFAULT = false

        private const val SHOW_DUPLICATES = "pref_show_duplicates"
        private const val SHOW_DUPLICATES_DEFAULT = true

        private const val DATA_SAVER = "data_saver_default"
    }

    // ============================= Filters ==============================

    private val metadataClient = client.newBuilder()
        .addNetworkInterceptor { chain ->
            chain.proceed(chain.request()).newBuilder()
                .header("Cache-Control", "max-age=${24 * 60 * 60}")
                .removeHeader("Pragma")
                .removeHeader("Expires")
                .build()
        }.build()

    override fun getFilterList(): FilterList = runBlocking(Dispatchers.IO) {
        val filters: MutableList<Filter<*>> = mutableListOf(
            SortFilter(),
            ContentRatingFilter(
                preferences.contentRating.toSet(),
            ),
            // GenresFilter(),
            // TagsFilter(),
            // SourcesFilter(),
            Filter.Separator(),
        )

        val response = metadataClient.newCall(
            GET("$apiUrl/api/v1/metadata", apiHeaders, CacheControl.FORCE_CACHE),
        ).await()

        // the cache only request fails if it was not cached already
        if (!response.isSuccessful) {
            metadataClient.newCall(
                GET("$apiUrl/api/v1/metadata", apiHeaders, CacheControl.FORCE_NETWORK),
            ).enqueue(
                object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        response.closeQuietly()
                    }
                    override fun onFailure(call: Call, e: IOException) {
                        Log.e(name, "Failed to fetch filters", e)
                    }
                },
            )

            filters.addAll(
                index = 0,
                listOf(
                    Filter.Header("Press 'Reset' to load more filters"),
                    Filter.Separator(),
                ),
            )

            return@runBlocking FilterList(filters)
        }

        val metadata = try {
            response.parseAs<MetadataDto>()
        } catch (e: Throwable) {
            Log.e(name, "Unable to parse filters", e)

            filters.addAll(
                index = 0,
                listOf(
                    Filter.Header("Failed to parse additional filters"),
                    Filter.Separator(),
                ),
            )
            return@runBlocking FilterList(filters)
        }

        filters.addAll(
            index = 2,
            listOf(
                GenresFilter(metadata.getGenresList()),
                TagsFilter(metadata.getTagsList()),
                SourcesFilter(metadata.getSourcesList().filter { !(!preferences.showScanlations && !officialSources.contains(it.name.lowercase())) }),
            ),
        )

        FilterList(filters)
    }
}
