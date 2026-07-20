package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

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
import keiyoushi.annotation.Source
import keiyoushi.lib.cookieinterceptor.CookieInterceptor
import keiyoushi.lib.publus.PublusAuthHandler
import keiyoushi.lib.publus.PublusContent
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.fetchPages
import keiyoushi.lib.publus.parseFragmentOrNull
import keiyoushi.network.get
import keiyoushi.source.KeiSource
import keiyoushi.utils.WebViewTimeoutException
import keiyoushi.utils.firstInstance
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.string
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.io.IOException
import kotlin.time.Duration.Companion.seconds

@Source
abstract class BookWalkerJp :
    KeiSource(),
    ConfigurableSource {
    private val memberApiUrl = "https://member.$DOMAIN/api"
    private val viewerUrl = "https://viewer.$DOMAIN"
    private val trialUrl = "https://viewer-trial.$DOMAIN"
    private val dfViewer = "https://viewer-df.$DOMAIN"
    private val preferences by getPreferencesLazy()
    private val desktopHeaders = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
        .build()

    override fun OkHttpClient.Builder.configureClient() = apply {
        addNetworkInterceptor(CookieInterceptor(DOMAIN, listOf("holdBook-series" to "1", "safeSearch" to "111", "mySetting/showCoverR15" to "1")))
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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        val search = filters.firstInstance<SearchFilter>()
        val sort = filters.firstInstance<SortFilter>()
        val library = filters.firstInstance<LibraryFilter>()
        if (library.state) {
            val response = client.get("$baseUrl/prx/holdBooks-api/hold-book-list/?page=$page")
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
        return client.get(url, desktopHeaders).asJsoup().toMangasPage()
    }

    override fun getFilterList(data: JsonElement?) = FilterList(
        Filter.Header("Note: Search and active filters are applied together (except library)"),
        Filter.Header("Note: Novels are not supported!"),
        SearchFilter(),
        SortFilter(),
        Filter.Header("Show your library (replaces all other filters)"),
        LibraryFilter(),
    )

    private fun Document.toMangasPage(): MangasPage {
        val mangas = select(".m-tile, .o-tile--series").map {
            val link = it.selectFirst("a.m-book-item__title, .o-tile-ttl a")!!
            SManga.create().apply {
                title = link.text()
                val cover = it.selectFirst(".m-thumb__image img, .o-tile-book-img img")
                    ?.absUrl("data-original")
                    ?.takeIf(String::isNotEmpty)
                thumbnail_url = cover?.getHiResCoverFromLegacyUrl()
                val href = link.absUrl("href").toHttpUrl().pathSegments
                url = (if (href.first() == "series") href[1] else href[0])
            }
        }

        return MangasPage(mangas, hasNextPage())
    }

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        val isBook = manga.url.startsWith("de")
        val seriesDoc = if (!isBook) client.get(seriesUrl(manga.url), desktopHeaders).asJsoup() else null
        val wayomi = seriesDoc?.selectFirst(".p-episode__list") != null
        val (details, firstListPage) = fetchBookDetails(manga, seriesDoc, wayomi)

        return SMangaUpdate(
            manga = details.toSManga(),
            chapters = when {
                !fetchChapters -> chapters
                wayomi -> seriesDoc.toWayomiChapters(hideLocked)
                else -> chapterList(details.seriesId.toString(), hideLocked, firstListPage)
            },
        )
    }

    override fun getMangaUrl(manga: SManga): String {
        val seriesId = manga.url.takeUnless { it.startsWith("de") } ?: runBlocking { resolveSeriesId(manga) }
        return "$baseUrl/series/$seriesId/list/"
    }

    private suspend fun fetchBookDetails(manga: SManga, seriesDoc: Document?, wayomi: Boolean): Pair<DetailsResponse, Document?> {
        var firstListPage: Document? = null
        val bookId = when {
            manga.url.startsWith("de") -> manga.url
            wayomi -> seriesDoc!!.firstEpisodeId()
            else -> client.get(seriesListUrl(manga.url, 1), desktopHeaders).asJsoup()
                .also { firstListPage = it }
                .firstBookId()
        }
        val details = client.get(booksUpdatesUrl(bookId)).parseAs<List<DetailsResponse>>().first()
        return details to firstListPage
    }

    private suspend fun chapterList(seriesId: String, hideLocked: Boolean, firstPage: Document? = null): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        var document = firstPage ?: client.get(seriesListUrl(seriesId, page), desktopHeaders).asJsoup()
        while (true) {
            val items = document.select(".m-tile-list .m-book-item")
            if (items.isEmpty()) break
            items.mapNotNullTo(chapters) { it.toSChapter(hideLocked) }
            if (!document.hasNextPage()) break
            page++
            document = client.get(seriesListUrl(seriesId, page), desktopHeaders).asJsoup()
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
        val viewerUrl = (readButton ?: trialButton)?.absUrl("href") ?: link.absUrl("href")

        return SChapter.create().apply {
            name = prefix + link.text()
            url = link.attr("data-uuid")
            memo = buildJsonObject { put("viewerUrl", viewerUrl) }
        }
    }

    private fun Document.toWayomiChapters(hideLocked: Boolean): List<SChapter> = select(".p-episode__list a[data-book-uuid]")
        .mapNotNull { it.toWayomiChapter(hideLocked) }
        .reversed()

    private fun Element.toWayomiChapter(hideLocked: Boolean): SChapter? {
        val free = attr("data-is-free") == "1"
        val rented = attr("data-is-now-rental") == "1"
        val purchased = attr("data-is-settled") == "1"
        val unlocked = free || rented || purchased
        if (hideLocked && !unlocked) return null

        return SChapter.create().apply {
            name = (if (unlocked) "" else "🔒 ") + selectFirst(".o-ttsk-list-item__title")!!.text()
            url = attr("data-book-uuid")
            val viewerUrl = when {
                rented -> attr("data-df-viewer-url")
                free -> absUrl("href")
                else -> attr("data-viewer-url")
            }
            memo = buildJsonObject { put("viewerUrl", viewerUrl) }
        }
    }

    private fun seriesUrl(seriesId: String): HttpUrl = "$baseUrl/series/$seriesId/".toHttpUrl()
    private fun Document.firstEpisodeId(): String = "de" + selectFirst(".p-episode__list a[data-book-uuid]")!!.attr("data-book-uuid")
    private fun Document.firstBookId(): String = selectFirst(".m-tile-list .m-book-item")!!.bookId()
    private fun Document.hasNextPage(): Boolean = selectFirst(".o-pager-next a:not(.o-pager-box-btn_hidden), a[data-action-label=次のページへ]:not(.o-pager-box-btn_hidden)") != null
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

    override fun getChapterUrl(chapter: SChapter): String = chapter.memo["viewerUrl"]?.string ?: throw Exception("Refresh Chapter List")

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
                    "$dfViewer/browserWebApi4/04/getLoader"
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

            if (content.status == "401" || content.status == "403") {
                throw Exception("Log in via WebView and rent or purchase this chapter to read.")
            }

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
            throw Exception("No preview available, or you need to purchase this volume.")
        }
    }

    private suspend fun fetchCr(scriptContent: String, viewerUrl: String): String? {
        val match = SCRIPT_REGEX.find(scriptContent) ?: return null
        val functionName = match.groupValues[1]

        return try {
            runWebView(timeout = 10.seconds) {
                blockImages = true
                onPageFinished {
                    evaluateJs(scriptContent) {
                        // c9P = function()
                        evaluateJs("$functionName()") { value ->
                            val result = value.trim('"')
                            resolve(result.takeIf { it != "null" && it.isNotBlank() })
                        }
                    }
                }
                loadData(viewerUrl, " ")
            }
        } catch (_: WebViewTimeoutException) {
            null
        }
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

    companion object {
        // Normal (non-trial) auth data expires after 60s
        private const val AUTH_REFRESH_SECONDS = 45L
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
        private val SCRIPT_REGEX = Regex("""^(\w+)=function\(\)\{[\s\S]*?\};""", RegexOption.MULTILINE)
    }
}
