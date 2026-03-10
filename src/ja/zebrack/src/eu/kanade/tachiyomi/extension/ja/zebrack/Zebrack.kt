package eu.kanade.tachiyomi.extension.ja.zebrack

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.decodeFromByteArray
import kotlinx.serialization.protobuf.ProtoBuf
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.Calendar
import java.util.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Zebrack :
    HttpSource(),
    ConfigurableSource {
    override val name = "Zebrack"
    private val subdomain = "zebrack-comic"
    override val baseUrl = "https://$subdomain.shueisha.co.jp"
    override val lang = "ja"
    override val supportsLatest = true

    private val apiUrl = "https://api2.$subdomain.com/api"
    private val magazineApiUrl = "https://api.$subdomain.com/api"
    private val jst = TimeZone.getTimeZone("Asia/Tokyo")
    private val preferences: SharedPreferences by getPreferencesLazy()
    private val hideLocked get() = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)

    private var secret: String? = null

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            if (!response.isSessionExpired()) return@addInterceptor response

            response.close()

            val failedSecret = request.url.queryParameter("secret")

            clearSecretIfCurrent(failedSecret)
            flushSecret(failedSecret)

            val newSecret = fetchSecret()
            val isSecretValid = !newSecret.isNullOrEmpty() && newSecret != failedSecret
            val newUrl = request.url.newBuilder().apply {
                if (isSecretValid) {
                    setQueryParameter("secret", newSecret)
                } else {
                    removeAllQueryParameters("secret")
                    clearSecretIfCurrent(newSecret)
                }
            }.build()

            val newRequest = request.newBuilder()
                .url(newUrl)
                .build()

            chain.proceed(newRequest)
        }
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/v3/title_tab_view".toHttpUrl().newBuilder()
            .addQueryParameter("os", "browser")
            .addQueryParameter("type", "ranking")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAsProto<RankingResponse>()
        val mangas = result.list.filter { it.type == "総合" }.flatMap { it.titles }.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val weekday = getLatestDay()
        val url = "$apiUrl/v3/rensai".toHttpUrl().newBuilder()
            .addQueryParameter("os", "browser")
            .addQueryParameter("day", weekday)
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = response.parseAsProto<LatestResponse>().list.map { it.toSManga() }
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotBlank()) {
            val url = "$apiUrl/v3/title_search".toHttpUrl().newBuilder()
                .addQueryParameter("os", "browser")
                .addQueryParameter("search_order", "related")
                .addQueryParameter("keyword", query)
                .build()
            return GET(url, headers)
        }

        val filter = filters.firstInstance<CategoryFilter>()
        val url = when (filter.type) {
            "day" -> {
                "$apiUrl/v3/rensai".toHttpUrl().newBuilder()
                    .addQueryParameter("os", "browser")
                    .addQueryParameter("day", filter.value)
            }
            "magazine" -> {
                "$magazineApiUrl/browser/${filter.value}".toHttpUrl().newBuilder()
                    .addQueryParameter("os", "browser")
            }
            else -> {
                "$apiUrl/v3/title_tag_search".toHttpUrl().newBuilder()
                    .addQueryParameter("os", "browser")
                    .addQueryParameter("tag_id", filter.value)
                    .addQueryParameter("search_order", "popular")
            }
        }
        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val segment = response.request.url.pathSegments.last()
        return when (segment) {
            "rensai" -> latestUpdatesParse(response)
            "magazine_list" -> {
                val result = response.parseAsProto<MagazineFilterResponse>()
                val mangas = with(result.magazines) {
                    (magazinesListAll + magazinesListMen + magazinesListWoman).map { it.toSManga() }
                }
                MangasPage(mangas, false)
            }
            else -> {
                val mangas = response.parseAsProto<SearchResponse>().list.map { it.toSManga() }
                MangasPage(mangas, false)
            }
        }
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaUrl = "$baseUrl/${manga.url}".toHttpUrl()
        val isMagazine = mangaUrl.fragment?.toInt()
        val magazineId = mangaUrl.pathSegments.first()
        if (isMagazine == 1) {
            val url = "$apiUrl/v3/magazine_detail".toHttpUrl().newBuilder()
                .addQueryParameter("os", "browser")
                .addQueryParameter("magazine_id", magazineId)
                .build()
            return GET(url, headers)
        }

        val url = "$apiUrl/browser/title_detail".toHttpUrl().newBuilder()
            .addQueryParameter("os", "browser")
            .addQueryParameter("title_id", manga.url)
            .addQueryParameter("tab", "detail")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val isTitleDetail = response.request.url.pathSegments.last().contains("title_detail")
        return if (isTitleDetail) {
            response.parseAsProto<MangaDetailsResponse>().details.toSManga()
        } else {
            response.parseAsProto<MagazineDetailsResponse>().details.toSManga()
        }
    }

    override fun getMangaUrl(manga: SManga): String {
        val mangaUrl = "$baseUrl/${manga.url}".toHttpUrl()
        val magazine = mangaUrl.fragment?.toInt()
        val magazineId = mangaUrl.pathSegments.first()
        return if (magazine == 1) "$baseUrl/magazine/$magazineId/detail" else "$baseUrl/title/${manga.url}"
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val mangaUrl = "$baseUrl/${manga.url}".toHttpUrl()
        if (mangaUrl.fragment?.toInt() == 1) return fetchMagazineChapters(mangaUrl.pathSegments.first())
        val secretKey = fetchSecret()
        val chapterUrl = "$apiUrl/v3/title_chapter_list".toHttpUrl().newBuilder()
            .addQueryParameter("os", "browser")
            .addQueryParameter("title_id", manga.url)
            .apply { secretKey?.let { addQueryParameter("secret", it) } }
            .build()

        val volumeUrl = "$apiUrl/browser/title_volume_list".toHttpUrl().newBuilder()
            .addQueryParameter("os", "browser")
            .addQueryParameter("title_id", manga.url)
            .apply { secretKey?.let { addQueryParameter("secret", it) } }
            .build()

        val chapters = client.newCall(GET(chapterUrl, headers))
            .asObservableSuccess()
            .map { it.parseChapterResponse() }

        val volumes = client.newCall(GET(volumeUrl, headers))
            .asObservableSuccess()
            .map { it.parseVolumeResponse() }

        return Observable.zip(volumes, chapters) { vols, chaps -> (vols + chaps).reversed() }
    }

    private fun Response.parseChapterResponse(): List<SChapter> {
        val result = parseAsProto<ChapterResponse>()
        val sessionMsg = result.chapterList
            ?.flatMap { it.chapters.orEmpty() }
            ?.firstNotNullOfOrNull { it.session?.message }
        checkSessionExpired(sessionMsg, request.url.queryParameter("secret"))
        return result.chapterList.orEmpty()
            .flatMap { it.chapters.orEmpty() }
            .filter { !hideLocked || !it.isLocked }
            .map { it.toSChapter() }
    }

    private fun Response.parseVolumeResponse(): List<SChapter> {
        val result = parseAsProto<VolumeResponse>()
        val sessionMsg = result.volumeData?.volumeList?.firstNotNullOfOrNull { it.session?.message }
        checkSessionExpired(sessionMsg, request.url.queryParameter("secret"))
        return result.volumeData?.volumeList.orEmpty()
            .filter { !hideLocked || !it.isLockedVolume }
            .map { it.toSChapter() }
    }

    private fun fetchMagazineChapters(magazineId: String, year: Int = Calendar.getInstance(jst).get(Calendar.YEAR)): Observable<List<SChapter>> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val secretKey = fetchSecret()
        val url = "$apiUrl/browser/magazine_backnumbers".toHttpUrl().newBuilder()
            .addQueryParameter("os", "browser")
            .addQueryParameter("magazine_id", magazineId)
            .addQueryParameter("year", year.toString())
            .apply { secretKey?.let { addQueryParameter("secret", it) } }
            .build()
        val response = client.newCall(GET(url, headers)).asObservableSuccess()
        return response.flatMap { response ->
            val data = response.parseAsProto<MagazineResponse>().magazineData?.magazineList
            val sessionMsg = data?.firstNotNullOfOrNull { it.session?.message }
            checkSessionExpired(sessionMsg, response.request.url.queryParameter("secret"))

            if (data.isNullOrEmpty()) {
                Observable.just(emptyList())
            } else {
                fetchMagazineChapters(magazineId, year - 1).map { prev ->
                    data.filter { !hideLocked || !it.isLockedMagazine }.map { it.toSChapter() } + prev
                }
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val id = url.pathSegments.first()
        val type = url.pathSegments.last().toInt()
        val fragment = url.fragment
        return when (type) {
            0 -> "$baseUrl/title/$fragment/chapter/$id/viewer"
            1 -> "$baseUrl/title/${fragment?.substringBefore(":")}/volume/$id/viewer"
            else -> "$baseUrl/magazine/$id/issue/${fragment?.substringBefore(":")}/viewer"
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val url = "$baseUrl/${chapter.url}".toHttpUrl()
        val id = url.pathSegments.first()
        val type = url.pathSegments.last().toInt()
        val fragment = url.fragment
        val secretKey = fetchSecret()
        return when (type) {
            0 -> {
                val requestUrl = "$apiUrl/v3/chapter_viewer"
                val body = FormBody.Builder() // application/x-www-form-urlencoded
                    .add("os", "browser")
                    .add("title_id", fragment!!)
                    .add("chapter_id", id)
                    .add("type", "normal")
                    .apply { secretKey?.let { add("secret", it) } }
                    .build()
                POST(requestUrl, headers, body)
            }

            1 -> {
                val (titleId, isTrial) = fragment!!.split(":")
                val requestUrl = "$apiUrl/v3/manga_volume_viewer".toHttpUrl().newBuilder()
                    .addQueryParameter("os", "browser")
                    .addQueryParameter("title_id", titleId)
                    .addQueryParameter("volume_id", id)
                    .apply { secretKey?.let { addQueryParameter("secret", it) } }
                    .addQueryParameter("is_trial", isTrial)
                    .build()
                GET(requestUrl, headers)
            }

            else -> {
                val (magazineIssueId, isTrial) = fragment!!.split(":")
                val requestUrl = "$magazineApiUrl/browser/magazine_viewer".toHttpUrl().newBuilder()
                    .addQueryParameter("os", "browser")
                    .addQueryParameter("magazine_id", id)
                    .addQueryParameter("magazine_issue_id", magazineIssueId)
                    .apply { secretKey?.let { addQueryParameter("secret", it) } }
                    .addQueryParameter("is_trial", isTrial)
                    .build()
                GET(requestUrl, headers)
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val isMagazineViewer = response.request.url.pathSegments.last() == "magazine_viewer"

        return if (isMagazineViewer) {
            val result = response.parseAsProto<MagazineViewerImages>()
            checkSessionExpired(result.session?.message, response.request.url.queryParameter("secret"))
            result.pages?.pagesList.orEmpty().mapIndexedNotNull { i, image ->
                image.page?.let { Page(i, imageUrl = "$it#key=${image.key}") }
            }.ifEmpty { throw Exception(LOCKED) }
        } else {
            val result = response.parseAsProto<ViewerResponse>()
            checkSessionExpired(result.session?.message, response.request.url.queryParameter("secret"))
            result.images.mapIndexedNotNull { i, image ->
                image.pages?.let { Page(i, imageUrl = "${it.page}#key=${it.key}") }
            }.ifEmpty { throw Exception(LOCKED) }
        }
    }

    private inline fun <reified T> Response.parseAsProto(): T = ProtoBuf.decodeFromByteArray(body.bytes())

    private fun getLatestDay(): String {
        val calendar = Calendar.getInstance(jst)
        val days = arrayOf("sun", "mon", "tue", "wed", "thu", "fri", "sat")
        return days[calendar.get(Calendar.DAY_OF_WEEK) - 1]
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Synchronized
    private fun fetchSecret(): String? {
        if (secret != null) return secret

        val latch = CountDownLatch(1)
        var token: String? = null

        Handler(Looper.getMainLooper()).post {
            val webView = WebView(Injekt.get<Application>())
            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript("window.localStorage.getItem('device_secret_key')") { value ->
                        token = value?.trim('"')
                        if (token == "null" || token.isNullOrBlank()) token = null

                        latch.countDown()
                        view.stopLoading()
                        view.destroy()
                    }
                }
            }
            webView.loadDataWithBaseURL("$baseUrl/", " ", "text/html", "utf-8", null)
        }

        latch.await(10, TimeUnit.SECONDS)

        secret = token
        return secret
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Synchronized
    private fun flushSecret(target: String?) {
        Handler(Looper.getMainLooper()).post {
            val webView = WebView(Injekt.get<Application>())
            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    val script = "if(window.localStorage.getItem('device_secret_key')==='$target'){window.localStorage.removeItem('device_secret_key');}"
                    view.evaluateJavascript(script) {
                        view.stopLoading()
                        view.destroy()
                    }
                }
            }
            webView.loadDataWithBaseURL("$baseUrl/", " ", "text/html", "utf-8", null)
        }
    }

    private fun Response.isSessionExpired(): Boolean = try {
        peekBody(Long.MAX_VALUE).string().contains(SESSION_EXPIRED)
    } catch (_: Exception) {
        false
    }

    private fun checkSessionExpired(sessionMsg: String?, failedSecret: String?) {
        if (sessionMsg == SESSION_EXPIRED) {
            flushSecret(failedSecret)
            throw Exception(LOCKED)
        }
    }

    @Synchronized
    private fun clearSecretIfCurrent(value: String?) {
        if (secret == value) secret = null
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override fun getFilterList() = FilterList(CategoryFilter())

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private const val SESSION_EXPIRED = "ログイン期限切れ"
        private const val LOCKED = "Log in via WebView and purchase this product to read."
    }

    override fun chapterListRequest(manga: SManga): Request = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
}
