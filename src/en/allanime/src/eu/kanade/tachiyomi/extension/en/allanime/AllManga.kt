package eu.kanade.tachiyomi.extension.en.allanime

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class AllManga :
    HttpSource(),
    ConfigurableSource {

    override val name = "AllManga"

    override val baseUrl = "https://allmanga.to"

    private val apiUrl = "https://api.allanime.day/api"

    override val lang = "en"

    override val id = 4709139914729853090

    override val supportsLatest = true

    override val supportsRelatedMangas = false

    private val preferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(apiUrl.toHttpUrl(), 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    /* Popular */
    override fun popularMangaRequest(page: Int): Request {
        val payload = GraphQL(
            PopularVariables(
                type = "manga",
                size = LIMIT,
                dateRange = 0,
                page = page,
                allowAdult = preferences.allowAdult,
                allowUnknown = false,
            ),
            POPULAR_QUERY,
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(apiUrl, headers, requestBody)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<ApiPopularResponse>()

        val mangaList = result.data.popular.mangas
            .mapNotNull { it.manga?.toSManga() }

        val hasNextPage = result.data.popular.mangas.size == LIMIT

        return MangasPage(mangaList, hasNextPage)
    }

    /* Latest */
    override fun latestUpdatesRequest(page: Int) = searchMangaRequest(page, "", FilterList())

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    /* Search */
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val httpUrl = query.toHttpUrl()
            if (httpUrl.host != baseUrl.toHttpUrl().host) {
                throw Exception("Unsupported url")
            }
            val id = httpUrl.pathSegments.getOrNull(1)
                ?: throw Exception("Unsupported url")
            return fetchSearchManga(page, "$SEARCH_PREFIX$id", filters)
        }

        if (!query.startsWith(SEARCH_PREFIX)) {
            return super.fetchSearchManga(page, query, filters)
        }

        val url = "/manga/${query.substringAfter(SEARCH_PREFIX)}/"
        return fetchMangaDetails(SManga.create().apply { this.url = url }).map {
            MangasPage(listOf(it), false)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val payload = GraphQL(
            SearchVariables(
                search = SearchPayload(
                    query = query.takeUnless { it.isEmpty() },
                    sortBy = filters.firstInstanceOrNull<SortFilter>()?.getValue(),
                    genres = filters.firstInstanceOrNull<GenreFilter>()?.included,
                    excludeGenres = filters.firstInstanceOrNull<GenreFilter>()?.excluded,
                    isManga = true,
                    allowAdult = preferences.allowAdult,
                    allowUnknown = false,
                ),
                size = LIMIT,
                page = page,
                translationType = "sub",
                countryOrigin = filters.firstInstanceOrNull<CountryFilter>()?.getValue() ?: "ALL",
            ),
            SEARCH_QUERY,
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(apiUrl, headers, requestBody)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val result = response.parseAs<ApiSearchResponse>()

        val mangaList = result.data.mangas.edges
            .map(SearchManga::toSManga)

        val hasNextPage = result.data.mangas.edges.size == LIMIT

        return MangasPage(mangaList, hasNextPage)
    }

    override fun getFilterList() = getFilters()

    /* Details */
    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = manga.url.split("/")[2]

        val payload = GraphQL(
            IDVariables(mangaId),
            DETAILS_QUERY,
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(apiUrl, headers, requestBody)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<ApiMangaDetailsResponse>()

        return result.data.manga.toSManga()
    }

    override fun getMangaUrl(manga: SManga): String {
        val mangaId = manga.url.split("/")[2]
        return "$baseUrl/manga/$mangaId"
    }

    /* Chapters */
    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.split("/")[2]

        val payload = GraphQL(
            ChapterListVariables(
                id = mangaId,
                showId = "manga@$mangaId",
            ),
            CHAPTERS_QUERY,
        )

        val requestBody = payload.toJsonRequestBody()

        return POST(apiUrl, headers, requestBody)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<ApiChapterListResponse>()
        val mangaUrl = "${result.data.manga.mangaId}/${result.data.manga.name.titleToSlug()}"

        val availableChapters = result.data.manga.availableChaptersDetail.sub
        val chapterDetails = result.data.chapterList.associateBy { it.chapterNum.content }

        return availableChapters.map { chapterNum ->
            chapterDetails[chapterNum]!!.toSChapter(mangaUrl)
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val chapterUrlParts = chapter.url.split("/")
        val mangaId = chapterUrlParts[2]
        val chapterSlug = chapterUrlParts[4]
        return "$baseUrl/manga/$mangaId/$chapterSlug"
    }

    /* Pages */
    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = Observable.fromCallable {
        pageListFromWebView(chapter)
    }

    override fun pageListRequest(chapter: SChapter): Request = throw UnsupportedOperationException()

    override fun pageListParse(response: Response): List<Page> = throw UnsupportedOperationException()

    @SuppressLint("SetJavaScriptEnabled")
    private fun pageListFromWebView(chapter: SChapter): List<Page> {
        val document = client.newCall(GET(getChapterUrl(chapter), headers)).execute().asJsoup()

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch)
        val pool = ('a'..'z') + ('A'..'Z')
        val interfaceName = (1..(10..20).random())
            .map { pool.random() }
            .joinToString("")
        val script = """
            (function () {
                const originalParse = JSON.parse;
                JSON.parse = new Proxy(originalParse, {
                    apply(target, thisArg, args) {
                        const result = Reflect.apply(target, thisArg, args);
                        if (result && result.chapterPages) {
                            window.$interfaceName.passPayload(args[0]);
                        }
                        return result;
                    }
                });
            })();
        """.trimIndent()
        var webView: WebView? = null

        handler.post {
            val view = WebView(Injekt.get<Application>())
            webView = view

            with(view.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                blockNetworkImage = true
                userAgentString = headers["User-Agent"]
            }
            view.addJavascriptInterface(jsInterface, interfaceName)

            view.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: android.graphics.Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view.evaluateJavascript(script) {}
                }
            }

            view.loadDataWithBaseURL(getChapterUrl(chapter), document.outerHtml(), "text/html", "utf-8", null)
        }

        val completed = latch.await(30, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (!completed) throw Exception("Timed out waiting for page list")

        val payload = jsInterface.payload ?: throw Exception("Failed to capture page list")
        val pageListData = payload.parseAs<PageListData>(json).pageList
            ?: return emptyList()

        val pages = pageListData.edges.firstOrNull {
            val fullUrlAvailable = it.pictureUrls.randomOrNull()?.url?.matches(urlRegex) == true
            val serverAvailable = it.serverUrl != null

            fullUrlAvailable || serverAvailable
        }
            ?: pageListData.edges.firstOrNull()
            ?: return emptyList()

        val imageDomain = pages.serverUrl?.let { server ->
            if (server.matches(urlRegex)) {
                "${server.removeSuffix("/")}/"
            } else {
                "https://${server.removeSuffix("/")}/"
            }
        } ?: "https://ytimgf.youtube-anime.com/"

        return pages.pictureUrls.mapIndexedNotNull { index, image ->
            image.url ?: return@mapIndexedNotNull null

            val imageUrl = if (image.url.matches(urlRegex)) {
                image.url
            } else {
                imageDomain + image.url.removePrefix("/")
            }
            Page(
                index = index,
                imageUrl = imageUrl,
            )
        }
    }

    private class JsInterface(private val latch: CountDownLatch) {
        var payload: String? = null
            private set

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passPayload(data: String) {
            if (payload == null) {
                payload = data
                latch.countDown()
            }
        }
    }

    override fun imageRequest(page: Page): Request {
        val quality = preferences.imageQuality

        if (quality == IMAGE_QUALITY_PREF_DEFAULT) {
            return super.imageRequest(page)
        }

        val oldUrl = imageQualityRegex.find(page.imageUrl!!)!!.groupValues[1]
        val newUrl = "$IMAGE_CDN/$oldUrl?w=$quality"

        return GET(newUrl, headers)
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = IMAGE_QUALITY_PREF
            title = "Image Quality"
            entries = arrayOf("Original", "Wp-800", "Wp-480")
            entryValues = arrayOf("original", "800", "480")
            setDefaultValue(IMAGE_QUALITY_PREF_DEFAULT)
            summary = "Warning: Wp quality servers can be slow and might not work sometimes"
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_ADULT_PREF
            title = "Show Adult Content"
            setDefaultValue(SHOW_ADULT_PREF_DEFAULT)
        }.also(screen::addPreference)
    }

    private val SharedPreferences.allowAdult
        get() = getBoolean(SHOW_ADULT_PREF, SHOW_ADULT_PREF_DEFAULT)

    private val SharedPreferences.imageQuality
        get() = getString(IMAGE_QUALITY_PREF, IMAGE_QUALITY_PREF_DEFAULT)!!

    companion object {
        private const val LIMIT = 20
        const val SEARCH_PREFIX = "id:"
        val urlRegex = Regex("^https?://.*")
        private const val IMAGE_CDN = "https://wp.youtube-anime.com"
        private val imageQualityRegex = Regex("^https?://([^#]+)")

        private const val SHOW_ADULT_PREF = "pref_adult"
        private const val SHOW_ADULT_PREF_DEFAULT = false
        private const val IMAGE_QUALITY_PREF = "pref_quality"
        private const val IMAGE_QUALITY_PREF_DEFAULT = "original"
    }
}
