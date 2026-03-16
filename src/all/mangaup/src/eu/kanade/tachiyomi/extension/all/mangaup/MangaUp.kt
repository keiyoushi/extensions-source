package eu.kanade.tachiyomi.extension.all.mangaup

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
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class MangaUp(override val lang: String) :
    HttpSource(),
    ConfigurableSource {
    override val name = "Manga UP!"
    private val domain = "manga-up.com"
    override val baseUrl = "https://global.$domain"
    override val supportsLatest = true

    private val apiUrl = "https://global-api.$domain/api"
    private val imgUrl = "https://global-img.$domain"
    private val preferences: SharedPreferences by getPreferencesLazy()

    private var secret: String? = null

    @SuppressLint("SetJavaScriptEnabled")
    @Synchronized
    private fun fetchSecret(): String? {
        val useSession = preferences.getBoolean(LOGGED_IN_SESSION_PREF, true)
        if (!useSession) return null

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
                    view.evaluateJavascript("window.localStorage.getItem('secret')") { value ->
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
    private fun flushSecret(target: String) {
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
                    val script = "if(window.localStorage.getItem('secret')==='$target'){window.localStorage.removeItem('secret');}"
                    view.evaluateJavascript(script) {
                        view.stopLoading()
                        view.destroy()
                    }
                }
            }
            webView.loadDataWithBaseURL("$baseUrl/", " ", "text/html", "utf-8", null)
        }
    }

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor())
        .addInterceptor { chain ->
            val request = chain.request()
            val response = chain.proceed(request)

            // 410: expired secret -> device got removed OR more than 3 secrets/devices active, oldest one expires
            // 401: invalid secret -> login was aborted; UA mismatch from previous login
            if (response.code == 410 || response.code == 401) {
                response.close()

                val failedSecret = request.url.queryParameter("secret")

                if (failedSecret != null) {
                    flushSecret(failedSecret)

                    synchronized(this) {
                        if (secret == failedSecret) {
                            secret = null
                        }
                    }
                }

                val newSecret = fetchSecret()
                val isMyPage = request.url.pathSegments.lastOrNull() == "my_page"
                val isSecretValid = !newSecret.isNullOrEmpty() && newSecret != failedSecret

                if (!isSecretValid && isMyPage) {
                    val target = request.url.fragment
                    throw IOException("Log in via WebView to access your $target")
                }

                val newUrl = request.url.newBuilder()

                if (isSecretValid) {
                    newUrl.setQueryParameter("secret", newSecret)
                } else {
                    newUrl.removeAllQueryParameters("secret")

                    synchronized(this) {
                        if (secret == newSecret) {
                            secret = null
                        }
                    }
                }

                val newRequest = request.newBuilder()
                    .url(newUrl.build())
                    .build()

                return@addInterceptor chain.proceed(newRequest)
            }
            response
        }
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/search".toHttpUrl().newBuilder()
            .apply {
                fetchSecret()?.let { addQueryParameter("secret", it) }
            }
            .addQueryParameter("app_ver", "0")
            .addQueryParameter("os_ver", "0")
            .addQueryParameter("lang", lang)
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAsProto<PopularResponse>()
        val mangas = result.titles?.map { it.toSManga(imgUrl) } ?: emptyList()
        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/home_v2".toHttpUrl().newBuilder()
            .apply {
                fetchSecret()?.let { addQueryParameter("secret", it) }
            }
            .addQueryParameter("app_ver", "0")
            .addQueryParameter("os_ver", "0")
            .addQueryParameter("lang", lang)
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val result = response.parseAsProto<HomeResponse>()
        val list = if (result.type == "Updates for you") {
            result.updates
        } else {
            result.newSeries
        }
        val mangas = list?.map { it.toSManga(imgUrl) } ?: emptyList()
        return MangasPage(mangas, false)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.startsWith(PREFIX_ID_SEARCH) && query.matches(ID_SEARCH_PATTERN)) {
            return mangaDetailsRequest(SManga.create().apply { url = "/manga/${query.removePrefix(PREFIX_ID_SEARCH)}" })
        }

        val currentSecret = fetchSecret()
        val url = apiUrl.toHttpUrl().newBuilder()
            .apply {
                currentSecret?.let { addQueryParameter("secret", it) }
            }
            .addQueryParameter("app_ver", "0")
            .addQueryParameter("os_ver", "0")
            .addQueryParameter("lang", lang)

        val genreFilter = filters.firstInstance<SelectFilter>()

        if (query.isNotEmpty()) {
            url.addPathSegments("manga/search")
            url.addQueryParameter("word", query)
        } else if (genreFilter.selectedValue.isNotEmpty()) {
            if (genreFilter.selectedValue == "favorites" || genreFilter.selectedValue == "history") {
                if (currentSecret == null) {
                    throw Exception("Log in via WebView to access your ${genreFilter.selectedValue}")
                }
                url.addPathSegment("my_page")
                url.fragment(genreFilter.selectedValue)
            } else {
                url.addPathSegments("manga/tag")
                url.addQueryParameter("tag_id", genreFilter.selectedValue)
            }
        } else {
            return popularMangaRequest(page)
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (response.request.url.pathSegments.last() == "detail_v2") {
            val result = response.parseAsProto<MangaDetailResponse>()
            val mangaId = response.request.url.queryParameter("title_id")!!
            return MangasPage(listOf(result.toSManga(mangaId, imgUrl)), false)
        }

        val fragment = response.request.url.fragment
        if (fragment == "favorites" || fragment == "history") {
            val result = response.parseAsProto<MyPageResponse>()
            val list = if (fragment == "favorites") result.favorites else result.history
            val mangas = list?.map { it.toSManga(imgUrl) } ?: emptyList()
            return MangasPage(mangas, false)
        }

        if (response.request.url.pathSegments.contains("manga") && response.request.url.pathSegments.last() != "detail_v2") {
            val result = response.parseAsProto<SearchResponse>()
            val mangas = result.titles?.map { it.toSManga(imgUrl) } ?: emptyList()
            return MangasPage(mangas, false)
        }

        return popularMangaParse(response)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val titleId = manga.url.substringAfterLast("/")
        val url = "$apiUrl/manga/detail_v2".toHttpUrl().newBuilder()
            .apply {
                fetchSecret()?.let { addQueryParameter("secret", it) }
            }
            .addQueryParameter("app_ver", "0")
            .addQueryParameter("os_ver", "0")
            .addQueryParameter("title_id", titleId)
            .addQueryParameter("quality", "high")
            .addQueryParameter("ui_lang", lang)
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAsProto<MangaDetailResponse>()
        val mangaId = response.request.url.queryParameter("title_id")!!
        return result.toSManga(mangaId, imgUrl)
    }

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAsProto<MangaDetailResponse>()
        val mangaId = response.request.url.queryParameter("title_id")!!
        val hidePaid = preferences.getBoolean(HIDE_PAID_PREF, false)
        return result.chapters
            .filter { !hidePaid || it.price == null }
            .map { it.toSChapter(mangaId) }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = chapter.url.substringAfterLast("/")
        val url = "$apiUrl/manga/viewer_v2".toHttpUrl().newBuilder()
            .apply {
                fetchSecret()?.let { addQueryParameter("secret", it) }
            }
            .addQueryParameter("app_ver", "0")
            .addQueryParameter("os_ver", "0")
            .addQueryParameter("chapter_id", chapterId)
            .addQueryParameter("quality", "high")
            .addQueryParameter("lang", lang)
            .build()
            .toString()

        return POST(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAsProto<ViewerResponse>()
        val pages = result.pageBlocks.flatMap { it.pages }
            .filter { !it.url.contains("tutorial") }

        if (pages.isEmpty()) {
            throw Exception("Log in via WebView and purchase this chapter")
        }

        return pages.mapIndexed { i, page ->
            val img = imgUrl + page.url + "#key=${page.key}#iv=${page.iv}"
            Page(i, imageUrl = img)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    // Helpers
    private inline fun <reified T> Response.parseAsProto(): T = ProtoBuf.decodeFromByteArray(body.bytes())

    override fun getFilterList() = FilterList(
        SelectFilter(
            "Genres",
            arrayOf(
                Pair("All", ""),
                Pair("Action", "13"),
                Pair("Adventure", "14"),
                Pair("Comedy", "15"),
                Pair("School Life", "16"),
                Pair("Dark Fantasy", "17"),
                Pair("Suspense", "18"),
                Pair("Historical", "19"),
                Pair("Game", "20"),
                Pair("Media Tie-ins", "21"),
                Pair("LGBTQ+", "253"),
                Pair("Completed", "256"),
                Pair("History", "history"),
                Pair("Favorites", "favorites"),
            ),
        ),
    )

    private open class SelectFilter(displayName: String, private val vals: Array<Pair<String, String>>) : Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        val selectedValue: String get() = vals[state].second
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_PAID_PREF
            title = "Hide paid chapters"
            summary = "Hide chapters that require points to unlock."
            setDefaultValue(false)
        }.also(screen::addPreference)

        SwitchPreferenceCompat(screen.context).apply {
            key = LOGGED_IN_SESSION_PREF
            title = "Logged-in Session"
            summary = "If enabled, uses the session from the WebView to access your content.\nDisable to browse as a guest."
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    companion object {
        const val PREFIX_ID_SEARCH = "id:"
        private val ID_SEARCH_PATTERN = "^id:(\\d+)$".toRegex()
        private const val HIDE_PAID_PREF = "hide_paid_chapters"
        private const val LOGGED_IN_SESSION_PREF = "logged_in_session"
    }
}
