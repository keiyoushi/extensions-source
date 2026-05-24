package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GocTruyenTranhVui :
    HttpSource(),
    ConfigurableSource {
    override val name = "Goc Truyen Tranh Vui"

    override val lang = "vi"

    private val defaultBaseUrl = "https://goctruyentranhvui30.com"

    override val baseUrl get() = getPrefBaseUrl()

    private val apiUrl get() = "$baseUrl/api/v2"

    override val supportsLatest: Boolean = true

    private val preferences: SharedPreferences = getPreferences {
        getString(PREF_DEFAULT_BASE_URL, null).let { prefDefaultBaseUrl ->
            if (prefDefaultBaseUrl != defaultBaseUrl) {
                edit()
                    .putString(PREF_BASE_URL, defaultBaseUrl)
                    .putString(PREF_DEFAULT_BASE_URL, defaultBaseUrl)
                    .apply()
            }
        }
    }

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .apply {
            build()["user-agent"]?.let { userAgent ->
                set("user-agent", removeWebViewToken(userAgent))
            }
        }

    private fun removeWebViewToken(userAgent: String): String = userAgent.replace(WEBVIEW_TOKEN_REGEX, ")")

    private val xhrHeaders by lazy {
        headersBuilder()
            .set("X-Requested-With", "XMLHttpRequest")
            .add("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
            .add("Accept-Language", "vi-VN,vi;q=0.9,en-US;q=0.8,en;q=0.7")
            .add("Cache-Control", "max-age=0")
            .add("Sec-Ch-Ua-Mobile", "?1")
            .add("Sec-Ch-Ua-Platform", "\"Android\"")
            .add("Sec-Fetch-Dest", "document")
            .add("Sec-Fetch-Mode", "navigate")
            .add("Sec-Fetch-Site", "same-origin")
            .add("Sec-Fetch-User", "?1")
            .add("Upgrade-Insecure-Requests", "1")
            .build()
    }

    override fun popularMangaRequest(page: Int): Request = searchMangaRequest(
        page,
        "",
        FilterList(
            SortByList(getSortByList()).apply {
                state[0].state = true
            },
        ),
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.parseAs<ResultDto<ListingDto>>()
        val hasNextPage = res.result.next
        return MangasPage(res.result.data.map { it.toSManga(baseUrl) }, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = searchMangaRequest(
        page,
        "",
        FilterList(
            SortByList(getSortByList()).apply {
                state[3].state = true
            },
        ),
    )

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith("https://")) {
            val url = query.toHttpUrl()
            val baseHost = baseUrl.toHttpUrl().host
            val defaultHost = defaultBaseUrl.toHttpUrl().host
            if (url.host != baseHost && url.host != defaultHost) {
                throw Exception("Tên miền không được hỗ trợ")
            }

            if (url.pathSegments.size >= 2 && url.pathSegments[0] == "truyen") {
                // Note: Fetching manga details directly for Deep Links is a temporary workaround
                // because the website currently restricts browsing/searching.
                // This allows users to access specific manga via URL as a temporary support measure.
                return client.newCall(GET(query, headers))
                    .asObservableSuccess()
                    .map { response ->
                        val manga = mangaDetailsParse(response)
                        MangasPage(listOf(manga), false)
                    }
            }
            return Observable.just(MangasPage(emptyList(), false))
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/truyen/${manga.url.substringAfter(':')}"

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        // B1: call manga detail url refresh cookie
        val refreshReq = GET(getMangaUrl(manga), headers)

        return client.newCall(refreshReq)
            .asObservableSuccess()
            .flatMap { _ ->
                // B2: recall chapter list request
                client.newCall(chapterListRequest(manga))
                    .asObservableSuccess()
                    .map(::chapterListParse)
            }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringBefore(':')
        val slug = manga.url.substringAfter(':')
        return GET("$baseUrl/api/comic/$mangaId/chapter?limit=-1#$slug", xhrHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.fragment!!
        val chapterJson = runCatching { response.parseAs<ResultDto<ChapterListDto>>() }.getOrNull()
        if (chapterJson == null || chapterJson.result.chapters.isEmpty()) {
            throw Exception("Có thể: Phiên làm việc đã hết hạn, vui lòng tải lại.")
        }
        return chapterJson.result.chapters.map { it.toSChapter(slug) }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = chapter.url
        val slug = url.substringAfter("/truyen/").substringBefore("/chuong-")
        val numberChapter = url.substringAfter("/chuong-").substringBefore("#")
        return "$baseUrl/truyen/$slug/chuong-$numberChapter"
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.select(".v-card-title").text()
        genre = document.select(".group-content > .v-chip-link").joinToString { it.text() }
        thumbnail_url = document.selectFirst("img.image")?.absUrl("src")
        status = parseStatus(document.selectFirst(".mb-1:contains(Trạng thái:) span")?.text())
        author = document.selectFirst(".mb-1:contains(Tác giả:) span")?.text()
        description = document.select(".v-card-text").joinToString { it.wholeText().trim() }

        // Extract ID and slug for internal use (especially for Deep Links)
        val script = document.select("script").firstOrNull { it.data().contains("const comic = {") }?.data()
        val id = script?.let { COMIC_ID_REGEX.find(it)?.groupValues?.get(1) }
            ?: document.selectFirst("#comic-id-comment")?.attr("value")
        val nameEn = script?.let { COMIC_NAME_EN_REGEX.find(it)?.groupValues?.get(1) }
            ?: response.request.url.pathSegments.getOrNull(1)

        if (id != null && nameEn != null) {
            this.url = "$id:$nameEn"
        }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang thực hiện", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = chapter.url
        val slug = url.substringAfter("/truyen/").substringBefore("/chuong-")
        val numberChapter = url.substringAfter("/chuong-").substringBefore("#")
        val comicId = url.substringAfter("#")

        val body = FormBody.Builder()
            .add("comicId", comicId)
            .add("chapterNumber", numberChapter)
            .add("nameEn", slug)
            .build()

        return POST("$baseUrl/api/chapter/loadAll", pageHeaders, body)
    }

    override fun pageListParse(response: Response): List<Page> {
        val jsonResult = runCatching { response.parseAs<ResultDto<ImageListDto>>() }
        jsonResult.onFailure {
            throw Exception("Có thể: Phiên làm việc đã hết hạn, vui lòng tải lại")
        }

        val imageList = jsonResult.getOrThrow().result.data
        if (imageList.isNullOrEmpty()) {
            throw Exception("Chưa đăng nhập trong WebView. Hoặc không có ảnh!")
        }

        return imageList.mapIndexed { i, url ->
            val finalUrl = if (url.startsWith("/image/")) {
                baseUrl + url
            } else {
                url
            }
            Page(i, imageUrl = finalUrl)
        }
    }

    private val pageHeaders by lazy {
        token?.let {
            headersBuilder()
                .set("X-Requested-With", "XMLHttpRequest")
                .set("Origin", baseUrl)
                .set("Authorization", it)
                .build()
        } ?: xhrHeaders
    }

    private var _token: String? = null

    @get:SuppressLint("SetJavaScriptEnabled")
    val token: String?
        get() {
            _token?.also { return it }
            val handler = Handler(Looper.getMainLooper())
            val latch = CountDownLatch(1)
            if (!customToken().isNullOrBlank()) {
                return customToken()
            }
            if (_token != null) return _token

            handler.post {
                val webview = WebView(Injekt.get<Application>())
                with(webview.settings) {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    blockNetworkImage = true
                }
                webview.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        // Get token
                        view!!.evaluateJavascript("window.localStorage.getItem('Authorization')") { token ->
                            _token = token.takeUnless { it == "null" }?.removeSurrounding("\"")
                            latch.countDown()
                            webview.destroy()
                        }
                    }
                }
                webview.loadDataWithBaseURL(baseUrl, " ", "text/html", "UTF-8", null)
            }

            latch.await(10, TimeUnit.SECONDS)
            return _token
        }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("search")
            addQueryParameter("p", (page - 1).toString())
            if (query.isNotEmpty()) addQueryParameter("searchValue", query)
            for (filter in filters) {
                when (filter) {
                    is FilterGroup ->
                        for (checkbox in filter.state) {
                            if (checkbox.state) addQueryParameter(filter.query, checkbox.id)
                        }

                    else -> {}
                }
            }
        }.build()
        return GET(url, xhrHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        StatusList(getStatusList()),
        SortByList(getSortByList()),
        GenreList(getGenreList()),
    )

    private fun customToken(): String? = preferences.getString(CUSTOM_TOKEN, null)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        EditTextPreference(screen.context).apply {
            key = PREF_CUSTOM_DOMAIN
            title = "Tùy chỉnh tên miền"
            summary = "Nhập tên miền đầy đủ (ví dụ: $defaultBaseUrl)"
            setDefaultValue(defaultBaseUrl)
            dialogTitle = "Ghi đè URL cơ sở"
            dialogMessage = "Default: $defaultBaseUrl"
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val inputUrl = newValue as String
                    if (inputUrl.isNotBlank()) {
                        inputUrl.toHttpUrl()
                    }
                    Toast.makeText(screen.context, "Tên miền đã được thay đổi", Toast.LENGTH_LONG).show()
                    true
                } catch (e: Exception) {
                    Toast.makeText(screen.context, "Lỗi sai định dạng URL: ${e.message}", Toast.LENGTH_LONG).show()
                    false
                }
            }
        }.let(screen::addPreference)
        EditTextPreference(screen.context).apply {
            key = CUSTOM_TOKEN
            title = "Authorization Token"
            summary = "Enter token manually"
            dialogTitle = "Authorization Token"
            customToken()?.let { dialogMessage = if (it.isNotEmpty()) "Token: ${customToken()}" else "Only show manually entered token, do not show token from WebView" }
            setOnPreferenceChangeListener { _, _ ->
                Toast.makeText(screen.context, RESTART_APP, Toast.LENGTH_LONG).show()
                true
            }
        }.also(screen::addPreference)
    }
    private fun getPrefBaseUrl(): String = preferences.getString(PREF_CUSTOM_DOMAIN, defaultBaseUrl)!!
    companion object {
        private const val CUSTOM_TOKEN = "custom_token"
        private const val PREF_DEFAULT_BASE_URL = "pref_default_base_url"
        private const val PREF_BASE_URL = "pref_base_url"
        private const val PREF_CUSTOM_DOMAIN = "pref_custom_domain"
        private const val RESTART_APP = "Khởi chạy lại ứng dụng để áp dụng token mới nhập."
        private val WEBVIEW_TOKEN_REGEX = Regex(""";\s*wv\)""")
        private val COMIC_ID_REGEX = Regex("""id:\s*"([^"]+)"""")
        private val COMIC_NAME_EN_REGEX = Regex("""nameEn:\s*`([^`]+)`""")
    }
}
