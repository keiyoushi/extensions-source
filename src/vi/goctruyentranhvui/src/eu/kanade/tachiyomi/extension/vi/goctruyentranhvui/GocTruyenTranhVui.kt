package eu.kanade.tachiyomi.extension.vi.goctruyentranhvui

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
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
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class GocTruyenTranhVui : HttpSource(), ConfigurableSource {
    override val lang = "vi"

    override val baseUrl = "https://goctruyentranhvui17.com"

    override val name = "Goc Truyen Tranh Vui"

    private val apiUrl = "$baseUrl/api/v2"

    override val supportsLatest: Boolean = true

    private val preferences: SharedPreferences = getPreferences()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET(
        apiUrl.toHttpUrl().newBuilder().apply {
            addPathSegments("home/filter")
            addQueryParameter("p", (page - 1).toString())
            addQueryParameter("value", "recommend")
        }.build(),
        headers,
    )

    override fun popularMangaParse(response: Response): MangasPage {
        val res = response.parseAs<ResultDto<ListingDto>>()
        val hasNextPage = res.result.next
        return MangasPage(res.result.data.map { it.toSManga(baseUrl) }, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET(
        "$apiUrl/search?p=${page - 1}&orders%5B%5D=recentDate",
        headers,
    )

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga) = "$baseUrl/truyen/${manga.url.substringAfter(':')}"

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.substringBefore(':')
        val slug = manga.url.substringAfter(':')
        return GET("$baseUrl/api/comic/$mangaId/chapter?limit=-1#$slug", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url.fragment!!
        val chapterJson = response.parseAs<ResultDto<ChapterListDto>>()
        return chapterJson.result.chapters.map { it.toSChapter(slug) }
    }

    override fun mangaDetailsRequest(manga: SManga) = GET(getMangaUrl(manga), headers)

    override fun mangaDetailsParse(response: Response): SManga = SManga.create().apply {
        val document = response.asJsoup()
        title = document.select(".v-card-title").text()
        genre = document.select(".group-content > .v-chip-link").joinToString { it.text() }
        thumbnail_url = document.selectFirst("img.image")?.absUrl("src")
        status = parseStatus(document.selectFirst(".mb-1:contains(Trạng thái:) span")?.text())
        author = document.selectFirst(".mb-1:contains(Tác giả:) span")?.text()
        description = document.select(".v-card-text").joinToString { it.wholeText() }
    }

    private fun parseStatus(status: String?) = when {
        status == null -> SManga.UNKNOWN
        status.contains("Đang thực hiện", ignoreCase = true) -> SManga.ONGOING
        status.contains("Hoàn thành", ignoreCase = true) -> SManga.COMPLETED
        else -> SManga.UNKNOWN
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.body.string()
        val pattern = Regex("chapterJson:\\s*`(.*?)`")
        val match = pattern.find(html) ?: throw Exception("Không tìm thấy Json") // find json
        val jsonPage = match.groups[1]!!.value

        val imageUrls = if (jsonPage.isEmpty()) {
            val regexMangaId = Regex("""comic\s*=\s*\{\s*id:\s*"(\d{10})"""")
            val matchId = regexMangaId.find(html) ?: throw Exception("Không tìm thấy mangaId") // find mangaId
            val mangaId = matchId.groups[1]!!.value
            val nameEn = response.request.url.toString().substringAfter("/truyen/").substringBefore("/")
            val chapterNumber = response.request.url.toString().substringAfterLast("chuong-")
            val body = FormBody.Builder()
                .add("comicId", mangaId)
                .add("chapterNumber", chapterNumber)
                .add("nameEn", nameEn)
                .build()
            val request = POST("$baseUrl/api/chapter/auth", pageHeaders, body)
            client.newCall(request).execute().parseAs<ResultDto<ImageListDto>>().result.data
        } else {
            jsonPage.parseAs<ImageListWrapper>().body.result.data
        }
        return imageUrls.mapIndexed { i, url ->
            val finalUrl = if (url.startsWith("/image/")) { baseUrl + url } else { url }
            Page(i, imageUrl = finalUrl)
        }
    }
    private val pageHeaders by lazy {
        getToken()?.let {
            headersBuilder()
                .add("X-Requested-With", "XMLHttpRequest")
                .add("Authorization", it)
                .build()
        } ?: throw Exception("Vui lòng đăng nhập trong WebView")
    }
    private var _token: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    private fun getToken(): String? {
        _token?.also { return it }
        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val customToken = preferences.getString("custom_token", null)
        if (!customToken.isNullOrBlank()) {
            return customToken
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
            addQueryParameter("searchValue", query)
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
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getFilterList() = FilterList(
        StatusList(getStatusList()),
        SortByList(getSortByList()),
        GenreList(getGenreList()),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val tokenPref = EditTextPreference(screen.context).apply {
            key = "custom_token"
            title = "Authorization Token"
            summary = "Enter token manually"
            dialogTitle = "Authorization Token"
            dialogMessage = "Token: ${getToken()}"
        }
        screen.addPreference(tokenPref)
    }
}
