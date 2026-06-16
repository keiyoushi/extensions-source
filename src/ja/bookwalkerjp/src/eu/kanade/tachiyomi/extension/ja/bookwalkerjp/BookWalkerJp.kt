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
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonElement
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import rx.Single
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
        .build()

    override fun popularMangaRequest(page: Int): Request {
        val url = "$baseUrl/category/2/".toHttpUrl().newBuilder()
            .addQueryParameter("order", "rank")
            .addQueryParameter("np", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, desktopHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$baseUrl/category/2/".toHttpUrl().newBuilder()
            .addQueryParameter("order", "release")
            .addQueryParameter("np", "0")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, desktopHeaders)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search/".toHttpUrl().newBuilder()
            .addQueryParameter("word", query)
            .addQueryParameter("order", "score")
            .addQueryParameter("np", "1")
            .addQueryParameter("page", page.toString())
            .build()
        return GET(url, desktopHeaders)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(".m-tile").map {
            SManga.create().apply {
                title = it.selectFirst("a.m-book-item__title")!!.text()
                thumbnail_url = it.selectFirst(".m-thumb__image img")?.absUrl("data-original")
                val href = it.selectFirst("a.m-book-item__title")!!.absUrl("href").toHttpUrl().pathSegments
                val path = if (href.first() == "series") href[1] else href[0]
                setUrlWithoutDomain(path)
            }
        }
        val hasNextPage = document.selectFirst(".o-pager-next a:not(.o-pager-box-btn_hidden)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        if (manga.url.startsWith("de")) {
            val url = "$memberApiUrl/books/updates".toHttpUrl().newBuilder()
                .addQueryParameter("fileType", "EPUB")
                .addQueryParameter(manga.url, "0")
                .build()
            return GET(url, headers)
        }
        val listUrl = "$baseUrl/series/${manga.url}/list/".toHttpUrl().newBuilder()
            .addQueryParameter("order", "title")
            .addQueryParameter("detail", "0")
            .build()
        val listDocument = client.newCall(GET(listUrl, desktopHeaders)).execute().asJsoup()
        val uuid = listDocument.first() // first uuid
        val url = "$memberApiUrl/books/updates".toHttpUrl().newBuilder()
            .addQueryParameter("fileType", "EPUB")
            .addQueryParameter(uuid, "0")
            .build()
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response): SManga = response.parseAs<DetailsResponse>().toSManga()

    override fun chapterListRequest(manga: SManga): Request {
        if (manga.url.startsWith("de")) {
            val detailsUrl = "$memberApiUrl/books/updates".toHttpUrl().newBuilder()
                .addQueryParameter("fileType", "EPUB")
                .addQueryParameter(manga.url, "0")
                .build()
            val detailsResponse = client.newCall(GET(detailsUrl, headers)).execute()
            val result = detailsResponse.parseAs<DetailsResponse>().seriesId
            val url = "$baseUrl/series/$result/list/".toHttpUrl().newBuilder()
                .addQueryParameter("order", "title")
                .addQueryParameter("detail", "0")
                .build()
            return GET(url, desktopHeaders)
        }

        val url = "$baseUrl/series/${manga.url}/list/".toHttpUrl().newBuilder()
            .addQueryParameter("order", "title")
            .addQueryParameter("detail", "0")
            .addQueryParameter("page", page)
            .build()
        return GET(url, desktopHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        // val hideLocked = preferences.getBoolean(HIDE_LOCKED_PREF_KEY, false)
        TODO("Not yet implemented")
    }

    private val authCache = ConcurrentHashMap<String, Pair<AuthInfo, Long>>()

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = rxSingle {
        val document = client.newCall(GET(baseUrl + chapter.url, headers))
            .awaitSuccess()
            .let { validateLogin(it) }
            .asJsoup()

        val isFreeChapter = document.selectFirst(".a-cart-btn:contains(Free)") != null
        val readerUrl = document.selectFirst("a.a-read-on-btn")?.attr("href")
            ?: if (isFreeChapter) {
                document.selectFirst(".free-preview > a")?.attr("href")
                    ?: throw Exception("No preview available")
            } else {
                throw Exception("You don't own this chapter, or you aren't logged in")
            }

        val chapterUrl = client.newCall(GET(readerUrl, headers)).await().request.url
        val cid = chapterUrl.queryParameter("cid")
        val cty = chapterUrl.queryParameter("cty")?.toIntOrNull()
        val isTrial = chapterUrl.host == "viewer-trial.$domain"

        val cookies = client.cookieJar.loadForRequest(chapterUrl)
        val u1 = cookies.find { it.name == "u1" }?.value
        val u2 = cookies.find { it.name == "u2" }?.value

        if (cid != null) {
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
    }.toObservable()

    override fun pageListParse(response: Response): List<Page> {
        TODO("Not yet implemented")
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

    override fun imageUrlParse(response: Response): String = response.request.url.toString()

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

    private fun <T> rxSingle(dispatcher: CoroutineDispatcher = Dispatchers.IO, block: suspend CoroutineScope.() -> T): Single<T> = Single.create { sub ->
        CoroutineScope(dispatcher).launch {
            try {
                sub.onSuccess(block())
            } catch (e: Throwable) {
                sub.onError(e)
            }
        }
    }

    private fun getHiResCoverFromLegacyUrl(url: String?): String? {
        if (url.isNullOrEmpty()) return url
        val segments = url.toHttpUrlOrNull()?.pathSegments ?: return url
        val fileName = segments.last()
        val extension = fileName.substringAfterLast('.')
        val numericId = when {
            url.startsWith(rimgUrl) -> segments.first().reversed().toLongOrNull()
            fileName.startsWith("thumbnailImage_") -> fileName.substringAfter("thumbnailImage_").substringBefore('.').toLongOrNull()
            else -> null
        } ?: return url

        return "$cUrl/coverImage_${numericId - 1}.$extension"
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
