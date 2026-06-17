package eu.kanade.tachiyomi.extension.ja.bookwalkerjp

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.lib.publus.Decoder
import keiyoushi.lib.publus.PublusFragment
import keiyoushi.lib.publus.PublusInterceptor
import keiyoushi.lib.publus.PublusPage
import keiyoushi.lib.publus.generatePages
import keiyoushi.utils.applicationContext
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class BookWalkerJp :
    HttpSource(),
    ConfigurableSource {
    override val name = "BookWalker Japan"
    private val domain = "bookwalker.jp"
    override val baseUrl = "https://$domain"
    override val lang = "ja"
    override val supportsLatest = true

    private val rimgUrl = "https://rimg.$domain"
    private val cUrl = "https://c.$domain"
    private val memberApiUrl = "https://member.$domain/api"
    private val viewerUrl = "https://viewer.$domain"
    private val trialUrl = "https://viewer-trial.$domain"
    private val preferences by getPreferencesLazy()
    private val desktopHeaders = headersBuilder()
        .set("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/149.0.0.0 Safari/537.36")
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override val client = network.client.newBuilder()
        .addInterceptor(PublusInterceptor())
        .addInterceptor(::coverFallbackIntercept)
        .build()

    override suspend fun getPopularManga(page: Int): MangasPage {
        val url = "$baseUrl/category/2/".toHttpUrl().newBuilder()
            .addQueryParameter("order", "rank")
            .addQueryParameter("np", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return client.newCall(GET(url, desktopHeaders)).awaitSuccess().asJsoup().toMangasPage()
    }

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        val url = "$baseUrl/category/2/".toHttpUrl().newBuilder()
            .addQueryParameter("order", "release")
            .addQueryParameter("np", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return client.newCall(GET(url, desktopHeaders)).awaitSuccess().asJsoup().toMangasPage()
    }

    override suspend fun getSearchManga(page: Int, query: String, filters: FilterList): MangasPage {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("word", query)
            .addQueryParameter("order", "score")
            .addQueryParameter("np", "1")
            .addQueryParameter("page", page.toString())
            .build()
        return client.newCall(GET(url, desktopHeaders)).awaitSuccess().asJsoup().toMangasPage()
    }

    private fun Document.toMangasPage(): MangasPage {
        val mangas = select(".m-tile").map {
            SManga.create().apply {
                title = it.selectFirst("a.m-book-item__title")!!.text()
                val cover = it.selectFirst(".m-thumb__image img")?.absUrl("data-original")
                thumbnail_url = cover.getHiResCoverFromLegacyUrl() ?: cover
                val href = it.selectFirst("a.m-book-item__title")!!.absUrl("href").toHttpUrl().pathSegments
                setUrlWithoutDomain(if (href.first() == "series") href[1] else href[0])
            }
        }
        return MangasPage(mangas, hasNextPage())
    }

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
            client.newCall(seriesListRequest(manga.url, 1)).awaitSuccess().asJsoup().firstBookId()
        }
        return client.newCall(booksUpdatesRequest(bookId)).awaitSuccess().parseAs<List<DetailsResponse>>().first()
    }

    private suspend fun chapterList(seriesId: String): List<SChapter> {
        val chapters = mutableListOf<SChapter>()
        var page = 1
        while (true) {
            val document = client.newCall(seriesListRequest(seriesId, page)).awaitSuccess().asJsoup()
            val pageChapters = document.toSChapters()
            if (pageChapters.isEmpty()) break
            chapters += pageChapters
            if (!document.hasNextPage()) break
            page++
        }
        return chapters.reversed()
    }

    private suspend fun resolveSeriesId(manga: SManga): String {
        if (!manga.url.startsWith("de")) return manga.url
        return client.newCall(booksUpdatesRequest(manga.url)).awaitSuccess().parseAs<List<DetailsResponse>>().first().seriesId.toString()
    }

    private fun booksUpdatesRequest(bookId: String): Request {
        val url = "$memberApiUrl/books/updates".toHttpUrl().newBuilder()
            .addQueryParameter("fileType", "EPUB")
            .addQueryParameter(bookId.removePrefix("de"), "0")
            .build()
        return GET(url, headers)
    }

    private fun seriesListRequest(seriesId: String, page: Int): Request {
        val url = "$baseUrl/series/$seriesId/list/".toHttpUrl().newBuilder()
            .addQueryParameter("order", "title")
            .addQueryParameter("detail", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, desktopHeaders)
    }

    private fun Document.toSChapters(): List<SChapter> = select(".m-tile-list .m-book-item").map {
        val link = it.bookLink()
        SChapter.create().apply {
            name = link.text()
            setUrlWithoutDomain(link.absUrl("href").toHttpUrl().pathSegments.first())
        }
    }

    private fun Document.firstBookId(): String = selectFirst(".m-tile-list .m-book-item")!!.bookId()
    private fun Document.hasNextPage(): Boolean = selectFirst(".o-pager-next a:not(.o-pager-box-btn_hidden)") != null
    private fun Element.bookLink(): Element = selectFirst("a.m-book-item__title")!!
    private fun Element.bookId(): String = bookLink().absUrl("href").toHttpUrl().pathSegments.first()

    private val authCache = ConcurrentHashMap<String, Pair<AuthInfo, Long>>()

    override fun getChapterUrl(chapter: SChapter): String = "$baseUrl/${chapter.url}"

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        val document = client.newCall(GET("$baseUrl/${chapter.url}", headers))
            .awaitSuccess()
            // .let { validateLogin(it) }
            .asJsoup()

        val readerUrl = document.selectFirst(".t-c-read-button a")?.absUrl("href")
            ?: document.selectFirst("#js-read-check-book-cover-main-button a")?.absUrl("href")
            ?: throw Exception("No preview available, or you aren't logged in.")

        val chapterUrl = client.newCall(GET(readerUrl, headers)).await().request.url
        val cid = chapterUrl.queryParameter("cid")
        val cty = chapterUrl.queryParameter("cty")?.toIntOrNull()
        val isTrial = chapterUrl.host == "viewer-trial.$domain"

        val cookies = client.cookieJar.loadForRequest(chapterUrl)
        val u1 = cookies.find { it.name == "u1" }?.value
        val u2 = cookies.find { it.name == "u2" }?.value

        return if (cid != null) {
            if (cty != null && cty != 1 && cty != 2) {
                throw Exception("Novels are not supported!")
            }

            var cr: String? = null
            if (!isTrial) {
                val loaderUrl = "$viewerUrl/browserWebApi/03/getLoader"
                val loaderScript =
                    client.newCall(GET(loaderUrl, headers)).awaitSuccess().body.string()
                cr = fetchCr(loaderScript, chapterUrl.toString())
            }

            val cApiBase = if (isTrial) "$trialUrl/trial-page/c" else "$viewerUrl/browserWebApi/c"
            val cApiUrl = cApiBase.toHttpUrl().newBuilder().apply {
                addQueryParameter("cid", cid)
                if (!isTrial) {
                    if (u1 != null) addQueryParameter("u1", u1)
                    if (u2 != null) addQueryParameter("u2", u2)
                    addQueryParameter("cr", cr)
                }
                addQueryParameter("BID", "0") // universal
            }.build()

            val cResponse = client.newCall(GET(cApiUrl, headers)).awaitSuccess()
            val content = cResponse.parseAs<CPhpResponse>()
            if (content.cty != 1 && content.cty != 2) {
                throw Exception("Novels are not supported!")
            }

            // For trial pages, auth data is valid for 1 hour.
            // For normal pages, auth data is valid for 60 seconds.
            if (!isTrial) {
                authCache[cid] = Pair(content.authInfo, System.currentTimeMillis())
            }

            val contentUrl = content.url
            val configUrl = (contentUrl + "configuration_pack.json").toHttpUrl().newBuilder().apply {
                if (!isTrial) {
                    addQueryParameter("hti", content.authInfo.hti)
                    addQueryParameter("cfg", content.authInfo.cfg.toString())
                    addQueryParameter("BID", "0")
                    addQueryParameter("uuid", content.authInfo.uuid)
                }
                addQueryParameter("pfCd", content.authInfo.pfCd)
                addQueryParameter("Policy", content.authInfo.policy)
                addQueryParameter("Signature", content.authInfo.signature)
                addQueryParameter("Key-Pair-Id", content.authInfo.keyPairId)
            }.build()

            val keys: List<IntArray>
            val rootJson: Map<String, JsonElement>

            val configResponse = client.newCall(GET(configUrl, headers)).awaitSuccess()
            if (isTrial) {
                rootJson = configResponse.parseAs()
                keys = listOf(IntArray(0), IntArray(0), IntArray(0))
            } else {
                val packData = configResponse.parseAs<ConfigPack>().data
                val result = Decoder(packData).decode()
                rootJson = result.json.parseAs()
                keys = result.keys
            }

            val configElement = rootJson["configuration"] ?: throw Exception("Configuration not found in decrypted JSON")
            val container = configElement.parseAs<PublusConfiguration>()

            val sessionData = mutableMapOf<String, String>().apply {
                put("cid", cid)
                put("isTrial", isTrial.toString())
                if (cr != null) put("cr", cr)
                if (u1 != null) put("u1", u1)
                if (u2 != null) put("u2", u2)
            }

            val pageContent = container.contents.map {
                val pageJson = rootJson[it.file]
                    ?: throw Exception("Page config not found for ${it.file}")

                val pageConfig = pageJson.toString().parseAs<PublusPageConfig>()
                val details = pageConfig.fileLinkInfo.pageLinkInfoList[0].page
                val isScrambled = !isTrial && details.blockWidth > 0 && details.blockHeight > 0
                val bw = if (details.blockWidth == 0) 32 else details.blockWidth
                val bh = if (details.blockHeight == 0) 32 else details.blockHeight

                PublusPage(
                    index = it.index,
                    filename = it.file,
                    no = details.no,
                    ns = details.ns,
                    ps = details.ps,
                    rs = details.rs,
                    blockWidth = bw,
                    blockHeight = bh,
                    width = details.size.width,
                    height = details.size.height,
                    hti = content.authInfo.hti,
                    cfg = content.authInfo.cfg?.toString(),
                    bid = "0",
                    uuid = content.authInfo.uuid,
                    pfCd = content.authInfo.pfCd,
                    policy = content.authInfo.policy,
                    signature = content.authInfo.signature,
                    keyPairId = content.authInfo.keyPairId,
                    extra = sessionData,
                    scrambled = isScrambled,
                )
            }

            generatePages(pageContent, keys, contentUrl)
        } else {
            throw Exception("Novels are not supported!")
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

    override fun imageRequest(page: Page): Request {
        val imageUrl = page.imageUrl!!
        if (imageUrl.contains("#")) {
            val fragment = imageUrl.substringAfter("#")
            val fragmentJson = String(Base64.decode(fragment, Base64.URL_SAFE), StandardCharsets.UTF_8)
            val params = fragmentJson.parseAs<PublusFragment>()
            val extra = params.extra
            if (extra?.get("isTrial") == "true") {
                return GET(imageUrl, headers)
            }

            if (extra != null && extra.containsKey("cid")) {
                val cid = extra["cid"]!!
                val cached = authCache[cid]

                var authInfo = cached?.first

                // Auth data expires after 60 seconds
                if (cached == null || System.currentTimeMillis() - cached.second > 45000) {
                    synchronized(authCache) {
                        val currentCache = authCache[cid]
                        if (currentCache == null || System.currentTimeMillis() - currentCache.second > 45000) {
                            try {
                                val refreshUrl = "$viewerUrl/browserWebApi/c".toHttpUrl().newBuilder().apply {
                                    addQueryParameter("cid", cid)
                                    addQueryParameter("BID", "0")
                                    addQueryParameter("cr", extra["cr"])
                                    extra["u1"]?.let { addQueryParameter("u1", it) }
                                    extra["u2"]?.let { addQueryParameter("u2", it) }
                                }.build()

                                val response = client.newCall(GET(refreshUrl, headers)).execute()
                                if (response.isSuccessful) {
                                    val newCData = response.parseAs<CPhpResponse>()
                                    authInfo = newCData.authInfo
                                    authCache[cid] = Pair(newCData.authInfo, System.currentTimeMillis())
                                }
                                response.close()
                            } catch (_: Exception) {
                            }
                        } else {
                            authInfo = currentCache.first
                        }
                    }
                }

                authInfo?.let {
                    val baseUrl = imageUrl.substringBefore("?")
                    val newUrl = baseUrl.toHttpUrl().newBuilder().apply {
                        addQueryParameter("hti", it.hti)
                        addQueryParameter("cfg", it.cfg.toString())
                        addQueryParameter("BID", "0")
                        addQueryParameter("uuid", it.uuid)
                        addQueryParameter("pfCd", it.pfCd)
                        addQueryParameter("Policy", it.policy)
                        addQueryParameter("Signature", it.signature)
                        addQueryParameter("Key-Pair-Id", it.keyPairId)
                    }.build().toString()

                    return GET("$newUrl#$fragment", headers)
                }
            }
            return GET(imageUrl, headers)
        }
        throw Exception("Invalid image URL")
    }

    private suspend fun validateLogin(response: Response): Response {
        /*
        if (!shouldValidateLogin) {
            return response
        }
         */
        val profileUrl = "https://member.bookwalker.jp/app/03/my/profile"
        val redirectedResponse = client.newCall(GET(profileUrl, headers)).await()
        if (redirectedResponse.request.url.toString() == "https://member.bookwalker.jp/app/03/login") {
            throw Exception("Auth expired, log in via WebView again.")
        }
        return response
    }

    private fun String?.getHiResCoverFromLegacyUrl(): String? {
        if (this.isNullOrEmpty()) return null
        val segments = this.toHttpUrlOrNull()?.pathSegments ?: return null
        val fileName = segments.last()
        val extension = fileName.substringAfterLast('.')
        val numericId = when {
            this.startsWith(rimgUrl) -> segments.first().reversed().toLongOrNull()
            fileName.startsWith("thumbnailImage_") -> fileName.substringAfter("thumbnailImage_").substringBefore('.').toLongOrNull()
            else -> null
        } ?: return null

        return "$cUrl/coverImage_${numericId - 1}.$extension#$this"
    }

    private fun coverFallbackIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val fallbackUrl = request.url.fragment
        if (response.isSuccessful || fallbackUrl.isNullOrEmpty() || !request.url.encodedPath.contains("/coverImage_")) {
            return response
        }
        response.close()
        return chain.proceed(GET(fallbackUrl, request.headers))
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = HIDE_LOCKED_PREF_KEY
            title = "Hide Locked Chapters"
            setDefaultValue(false)
        }.also(screen::addPreference)
    }

    companion object {
        private const val HIDE_LOCKED_PREF_KEY = "hide_locked"
    }
}
