package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.lib.publus.PublusAuthHandler
import keiyoushi.lib.publus.PublusContent
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.lib.publus.parseFragmentOrNull
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.applicationContext
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BookWalkerJp :
    KeiSource(),
    ConfigurableSource {
    override val name = "BookWalker Japan"
    override val baseUrl = "https://$DOMAIN"
    override val lang = "ja"

    private val memberApiUrl = "https://member.$DOMAIN/api"
    private val viewerUrl = "https://viewer.$DOMAIN"
    private val trialUrl = "https://viewer-trial.$DOMAIN"
    private val dfViewer = "https://viewer-df.$DOMAIN"
    private val preferences by getPreferencesLazy()
    private val desktopHeaders = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36")
        .build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addInterceptor(CookieInterceptor(DOMAIN, listOf("holdBook-series" to "1", "safeSearch" to "111", "mySetting/showCoverR15" to "1")))
        addInterceptor(PublusInterceptor())
        addInterceptor {
            val request = it.request()
            val response = it.proceed(request)
            val fallbackUrl = request.url.fragment
            val url = response.request.url
            if (response.code == 401 && url.pathSegments[1] == "holdBooks-api") {
                throw IOException("Log in via WebView to access your library.")
            }

            if (url.encodedPath == "/app/03/login") {
                throw IOException("Auth expired. Log in via WebView again.")
            }

            if (response.isSuccessful || fallbackUrl.isNullOrEmpty() || !request.url.encodedPath.contains("/coverImage_")) {
                return@addInterceptor response
            }
            response.close()
            it.proceed(GET(fallbackUrl, request.headers))
        }
    }

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/category/2/".toHttpUrl().newBuilder()
            .addQueryParameter("order", "rank")
            .addQueryParameter("np", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, desktopHeaders).asJsoup().toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/category/2/".toHttpUrl().newBuilder()
            .addQueryParameter("order", "release")
            .addQueryParameter("np", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, desktopHeaders).asJsoup().toMangasPage()
    }

    override suspend fun getSearchMangaList(page: Int, query: String, filterList: FilterList): MangasPage {
        val search = filterList.firstInstance<SearchFilter>()
        val sort = filterList.firstInstance<SortFilter>()
        val library = filterList.firstInstance<LibraryFilter>()
        if (library.state) {
            val response = client.get("$baseUrl/prx/holdBooks-api/hold-book-list/?page=$page", headers)
            val result = response.parseAs<LibraryResponse>().holdBookList
            val hasNextPage = response.request.url.queryParameter("page")!!.toInt() < result.totalPage
            val mangas = result.entities.map { it.toSManga() }
            return MangasPage(mangas, hasNextPage)
        }

        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("word", query)
            .addQueryParameter("order", sort.value)
            .addQueryParameter("wa", search.value)
            .addQueryParameter("np", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return client.get(url, desktopHeaders).asJsoup().toMangasPage(wayomi = search.value == "1")
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Search and active filters are applied together (except library)"),
        Filter.Header("Note: Novels are not supported!"),
        SearchFilter(),
        SortFilter(),
        Filter.Header("Show your library (replaces all other filters)"),
        LibraryFilter(),
    )

    private fun Document.toMangasPage(wayomi: Boolean = false): MangasPage {
        val tileSelector = if (wayomi) ".o-tile--series" else ".m-tile"
        val linkSelector = if (wayomi) ".o-tile-ttl a" else "a.m-book-item__title"
        val imgSelector = if (wayomi) ".o-tile-book-img img" else ".m-thumb__image img"

        val mangas = select(tileSelector).map {
            val link = it.selectFirst(linkSelector)!!
            SManga.create().apply {
                title = link.text()
                val cover = it.selectFirst(imgSelector)?.absUrl("data-original")
                thumbnail_url = cover.getHiResCoverFromLegacyUrl() ?: cover
                val href = link.absUrl("href").toHttpUrl().pathSegments
                setUrlWithoutDomain(if (href.first() == "series") href[1] else href[0])
            }
        }

        val hasNext = if (wayomi) selectFirst("a[data-action-label=次のページへ]:not(.o-pager-box-btn_hidden)") != null else hasNextPage()
        return MangasPage(mangas, hasNext)
    }

    // TODO how implement wayomi?
    override suspend fun getMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val details = if (fetchDetails || (fetchChapters && manga.url.startsWith("de"))) {
            fetchBookDetails(manga)
        } else {
            null
        }
        val seriesId = details?.seriesId?.toString() ?: manga.url

        return SMangaUpdate(
            manga = if (fetchDetails) details!!.toSManga() else manga,
            chapters = if (fetchChapters) chapterList(seriesId) else chapters,
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        val seriesId = manga.url.takeUnless { it.startsWith("de") } ?: runBlocking { resolveSeriesId(manga) }
        return "$baseUrl/series/$seriesId/list/"
    }

    private suspend fun fetchBookDetails(manga: SManga): DetailsResponse {
        val bookId = if (manga.url.startsWith("de")) {
            manga.url
        } else {
            client.get(seriesListUrl(manga.url, 1), desktopHeaders).asJsoup().firstBookId()
        }
        return client.get(booksUpdatesUrl(bookId)).parseAs<List<DetailsResponse>>().first()
    }

    private suspend fun chapterList(seriesId: String): List<SChapter> {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val chapters = mutableListOf<SChapter>()
        var page = 1
        while (true) {
            val document = client.get(seriesListUrl(seriesId, page), desktopHeaders).asJsoup()
            val items = document.select(".m-tile-list .m-book-item")
            if (items.isEmpty()) break
            items.mapNotNullTo(chapters) { it.toSChapter(hideLocked) }
            if (!document.hasNextPage()) break
            page++
        }
        return chapters.reversed()
    }

    private suspend fun resolveSeriesId(manga: SManga): String {
        if (!manga.url.startsWith("de")) return manga.url
        return client.get(booksUpdatesUrl(manga.url)).parseAs<List<DetailsResponse>>().first().seriesId.toString()
    }

    private fun booksUpdatesUrl(bookId: String): HttpUrl = "$memberApiUrl/books/updates".toHttpUrl().newBuilder()
        .addQueryParameter("fileType", "EPUB")
        .addQueryParameter(bookId.removePrefix("de"), "0")
        .build()

    private fun seriesListUrl(seriesId: String, page: Int): HttpUrl = "$baseUrl/series/$seriesId/list/".toHttpUrl().newBuilder()
        .addQueryParameter("order", "title")
        .addQueryParameter("detail", "0")
        .addQueryParameter("page", page.toString())
        .build()

    // TODO check memo, how?
    private fun Element.toSChapter(hideLocked: Boolean): SChapter? {
        val readButton = selectFirst(".a-icon-btn--read, .a-icon-btn--free")
        val trialButton = selectFirst(".a-icon-btn--trial")
        val prefix = when {
            readButton != null -> ""
            trialButton != null -> "🔒 (Preview) "
            else -> "🔒 "
        }
        if (hideLocked && prefix.isNotEmpty()) return null

        val link = bookLink()
        return SChapter.create().apply {
            name = prefix + link.text()
            url = (readButton ?: trialButton)?.absUrl("href") ?: link.absUrl("href")
        }
    }

    private fun Document.firstBookId(): String = selectFirst(".m-tile-list .m-book-item")!!.bookId()
    private fun Document.hasNextPage(): Boolean = selectFirst(".o-pager-next a:not(.o-pager-box-btn_hidden)") != null
    private fun Element.bookLink(): Element = selectFirst("a.m-book-item__title")!!
    private fun Element.bookId(): String = bookLink().absUrl("href").toHttpUrl().pathSegments.first()

    private val publusAuth = PublusAuthHandler(
        client = client,
        refreshSeconds = AUTH_REFRESH_SECONDS,
        bid = "0",
    ) { session, _ ->
        val cUrl = session["cUrl"]!!.toHttpUrl()
        val cookies = client.cookieJar.loadForRequest(cUrl)
        val refreshUrl = cUrl.newBuilder().apply {
            addQueryParameter("cid", session["cid"])
            addQueryParameter("BID", "0")
            addQueryParameter("cr", session["cr"])
            cookies.find { it.name == "u1" }?.let { addQueryParameter("u1", it.value) }
            cookies.find { it.name == "u2" }?.let { addQueryParameter("u2", it.value) }
        }.build()

        GET(refreshUrl, headers)
    }

    override fun getChapterUrl(chapter: SChapter): String = chapter.url

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val chapterUrl = client.get(getChapterUrl(chapter), desktopHeaders, ensureSuccess = false).request.url
        val cid = chapterUrl.queryParameter("cid")
        val cty = chapterUrl.queryParameter("cty")?.toIntOrNull()
        val host = chapterUrl.host
        val isTrial = host == trialUrl.toHttpUrl().host
        val isDf = host == dfViewer.toHttpUrl().host

        val cookies = client.cookieJar.loadForRequest(chapterUrl)
        val u1 = cookies.find { it.name == "u1" }?.value
        val u2 = cookies.find { it.name == "u2" }?.value

        return if (cid != null) {
            if (cty != null && cty != 1 && cty != 2) {
                throw Exception("Novels are not supported!")
            }

            var cr: String? = null
            if (!isTrial) {
                val loaderUrl = if (isDf) {
                    "$viewerUrl/browserWebApi4/04/getLoader"
                } else {
                    "$viewerUrl/browserWebApi/03/getLoader"
                }
                val loaderScript = client.get(loaderUrl).body.string()
                cr = fetchCr(loaderScript, chapterUrl.toString())
            }

            val cApiBase = when {
                isTrial -> "$trialUrl/trial-page/c"
                isDf -> "$dfViewer/browserWebApi4/c"
                else -> "$viewerUrl/browserWebApi/c"
            }

            val cApiUrl = cApiBase.toHttpUrl().newBuilder().apply {
                addQueryParameter("cid", cid)
                if (!isTrial) {
                    if (u1 != null) addQueryParameter("u1", u1)
                    if (u2 != null) addQueryParameter("u2", u2)
                    addQueryParameter("cr", cr)
                }
                addQueryParameter("BID", "0") // universal
            }.build()

            val content = client.get(cApiUrl).parseAs<PublusContent>()
            if (content.cty != 1 && content.cty != 2) {
                throw Exception("Novels are not supported!")
            }

            val sessionData = buildMap {
                put("cid", cid)
                put("isTrial", isTrial.toString())
                put("cUrl", cApiBase)
                if (cr != null) put("cr", cr)
            }

            val auth = content.authInfo?.toAuth(bid = "0", includeBookAuth = !isTrial)

            if (!isTrial && auth != null) {
                publusAuth.store(cid, auth)
            }

            fetchPages(content.url!!, headers, client, auth, sessionData)
        } else {
            throw Exception("No preview available, or you aren't logged in via Webview.")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun fetchCr(scriptContent: String, viewerUrl: String): String? {
        val match = Regex("""^(\w+)=function\(\)\{[\s\S]*?\};""", RegexOption.MULTILINE).find(scriptContent) ?: return null
        val functionName = match.groupValues[1]
        val latch = CountDownLatch(1)

        var result: String? = null

        Handler(Looper.getMainLooper()).post {
            val webView = WebView(applicationContext)
            with(webView.settings) {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                blockNetworkImage = true
            }
            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String?) {
                    view.evaluateJavascript(scriptContent) {
                        view.evaluateJavascript("$functionName()") {
                            // c9P = function()
                            result = it?.trim('"')
                            if (result == "null" || result.isNullOrBlank()) result = null
                            latch.countDown()
                        }
                    }
                }
            }
            webView.loadDataWithBaseURL(viewerUrl, " ", "text/html", "utf-8", null)
        }

        latch.await(10, TimeUnit.SECONDS)
        return result
    }

    private fun authorize(imageUrl: String): String {
        val url = imageUrl.toHttpUrlOrNull() ?: return imageUrl
        val session = url.fragment?.parseFragmentOrNull()?.extra ?: return imageUrl
        if (session["isTrial"] == "true") return imageUrl
        val key = session["cid"] ?: return imageUrl

        val auth = publusAuth.currentAuth(key, session) ?: return imageUrl
        return auth.applyTo(url.newBuilder().query(null)).build().toString()
    }

    override fun imageRequest(page: Page): Request = GET(authorize(page.imageUrl!!), headers)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    override suspend fun fetchRelatedMangaList(manga: SManga): List<SManga> = emptyList()
    override suspend fun getMangaByUrl(url: HttpUrl): SManga? = null

    companion object {
        // Normal (non-trial) auth data expires after 60s
        private const val AUTH_REFRESH_SECONDS = 45L
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
