package eu.kanade.tachiyomi.extension.zh.onemanhua

import android.annotation.SuppressLint
import android.app.Application
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.dataimage.DataImageInterceptor
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.tryParse
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.publicsuffix.PublicSuffixDatabase
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.text.SimpleDateFormat
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class ColaManga(
    final override val name: String,
    final override val baseUrl: String,
    final override val lang: String,
) : ParsedHttpSource(), ConfigurableSource {

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(
            baseUrl.toHttpUrl(),
            preferences.getString(RATE_LIMIT_PREF_KEY, RATE_LIMIT_PREF_DEFAULT)!!.toInt(),
            preferences.getString(RATE_LIMIT_PERIOD_PREF_KEY, RATE_LIMIT_PERIOD_PREF_DEFAULT)!!.toLong(),
            TimeUnit.MILLISECONDS,
        )
        .addInterceptor(DataImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) =
        GET("$baseUrl/show?orderBy=dailyCount&page=$page", headers)

    override fun popularMangaSelector() = "li.fed-list-item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("a.fed-list-title")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }
        thumbnail_url = element.selectFirst("a.fed-list-pics")?.absUrl("data-original")
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/show?orderBy=update&page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = if (query.isNotEmpty()) {
            "$baseUrl/search".toHttpUrl().newBuilder().apply {
                filters.ifEmpty { getFilterList() }
                    .firstOrNull { it is SearchTypeFilter }
                    ?.let { (it as SearchTypeFilter).addToUri(this) }

                addQueryParameter("searchString", query)
                addQueryParameter("page", page.toString())
            }.build()
        } else {
            "$baseUrl/show".toHttpUrl().newBuilder().apply {
                filters.ifEmpty { getFilterList() }
                    .filterIsInstance<UriFilter>()
                    .filterNot { it is SearchTypeFilter }
                    .forEach { it.addToUri(this) }

                addQueryParameter("page", page.toString())
            }.build()
        }

        return GET(url, headers)
    }

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SLUG_SEARCH)
            val url = "/$slug/"

            fetchMangaDetails(SManga.create().apply { this.url = url })
                .map { MangasPage(listOf(it.apply { this.url = url }), false) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaSelector() = "dl.fed-deta-info, ${popularMangaSelector()}"

    override fun searchMangaFromElement(element: Element): SManga {
        if (element.tagName() == "li") {
            return popularMangaFromElement(element)
        }

        return SManga.create().apply {
            element.selectFirst("h1.fed-part-eone a")!!.let {
                setUrlWithoutDomain(it.attr("href"))
                title = it.text()
            }

            thumbnail_url = element.selectFirst("a.fed-list-pics")?.absUrl("data-original")
        }
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    protected abstract val statusTitle: String
    protected abstract val authorTitle: String
    protected abstract val genreTitle: String
    protected abstract val statusOngoing: String
    protected abstract val statusCompleted: String
    protected abstract val lastUpdated: String
    private val dateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd")

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.fed-part-eone")!!.text()
        thumbnail_url = document.selectFirst("a.fed-list-pics")?.absUrl("data-original")
        author = document.selectFirst("span.fed-text-muted:contains($authorTitle) + a")?.text()
        genre = document.select("span.fed-text-muted:contains($genreTitle) ~ a").joinToString { it.text() }
        description = document
            .selectFirst("ul.fed-part-rows li.fed-col-xs12.fed-show-md-block .fed-part-esan")
            ?.ownText()
        status = when (document.selectFirst("span.fed-text-muted:contains($statusTitle) + a")?.text()) {
            statusOngoing -> SManga.ONGOING
            statusCompleted -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListSelector(): String = "div:not(.fed-hidden) > div.all_data_list > ul.fed-part-rows a"

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(chapterListSelector()).map { chapterFromElement(it) }.apply {
            if (isNotEmpty()) {
                this[0].date_upload = dateFormat.tryParse(document.selectFirst("span.fed-text-muted:contains($lastUpdated) + a")?.text())
            }
        }
    }

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.attr("title")
    }

    override fun pageListParse(document: Document) = throw UnsupportedOperationException()

    private var pagesMap: MutableMap<String, ArrayList<Page>> = mutableMapOf()
    private val handler = Handler(Looper.getMainLooper())
    private val webViewCache = object : LinkedHashMap<String, WebView>() {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, WebView>): Boolean {
            if (size <= 5) return false
            handler.post {
                eldest.value.destroy()
            }
            handler.removeCallbacksAndMessages(eldest.key)
            return true
        }
    }
    private val interfaceName = randomString()
    private val webViewIdleDelayMillis: Long = 1800000
    private val webviewScript by lazy {
        javaClass.getResource("/assets/webview-script.js")?.readText() ?: throw Exception("WebView 脚本不存在")
    }
    private val baseUrlTopPrivateDomain = baseUrl.toHttpUrl().topPrivateDomain()
    private val emptyResourceResponse = WebResourceResponse(null, null, 204, "No Content", null, null)

    private fun postDelayed(
        r: Runnable,
        token: Any?,
        delayMillis: Long,
        handler: Handler,
    ): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            handler.postDelayed(r, token, delayMillis)
        } else {
            return handler.postAtTime(r, token, SystemClock.uptimeMillis() + delayMillis)
        }
    }

    private fun destroyWebView(chapterUrl: String) {
        handler.post { webViewCache.remove(chapterUrl)?.destroy() }
        handler.removeCallbacksAndMessages(chapterUrl)
    }


    @SuppressLint("SetJavaScriptEnabled")
    private fun pageListParse(chapterUrl: String): List<Page> {
        val url = baseUrl + chapterUrl
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch, chapterUrl, pagesMap, webViewCache)
        destroyWebView(chapterUrl)
        handler.post {
            val webview = WebView(Injekt.get<Application>())
            webViewCache.put(chapterUrl, webview)
            webview.settings.domStorageEnabled = true
            webview.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            webview.settings.javaScriptEnabled = true
            webview.addJavascriptInterface(jsInterface, interfaceName)
            webview.webViewClient = object : WebViewClient() {
                override fun onLoadResource(view: WebView?, url: String?) {
                    if (url == "$baseUrl/counting") { // the time when document.body is ready
                        view?.evaluateJavascript(
                            webviewScript.replace(
                                "__interface__",
                                interfaceName,
                            ),
                        ) {}
                    }
                    super.onLoadResource(view, url)
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): Boolean {
                    request?.url?.host?.let {
                        if (PublicSuffixDatabase.get().getEffectiveTldPlusOne(it) != baseUrlTopPrivateDomain) {
                            return true // prevent redirects to external sites, some ad scheme is intent and break the webview
                        }
                    }
                    return super.shouldOverrideUrlLoading(view, request)
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    request?.url?.host?.let {
                        if (PublicSuffixDatabase.get().getEffectiveTldPlusOne(it) != baseUrlTopPrivateDomain) {
                            return emptyResourceResponse // prevent loading resources from external sites, all of them are ads
                        }
                    }
                    return super.shouldInterceptRequest(view, request)
                }

                override fun onRenderProcessGone(
                    view: WebView,
                    detail: RenderProcessGoneDetail,
                ): Boolean {
                    destroyWebView(chapterUrl)
                    return super.onRenderProcessGone(view, detail)
                }
            }
            webview.loadUrl(url)
        }

        postDelayed(
            {
                destroyWebView(chapterUrl)
            },
            chapterUrl,
            webViewIdleDelayMillis,
            handler,
        )

        latch.await(30L, TimeUnit.SECONDS)
        if (latch.count == 1L) {
            destroyWebView(chapterUrl)
            throw Exception("加载章节超时")
        }
        return pagesMap[chapterUrl]?.toList() ?: run {
            destroyWebView(chapterUrl)
            throw Exception("加载章节失败：页面列表为空")
        }
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.fromCallable {
            pageListParse(chapter.url)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    override fun fetchImageUrl(page: Page): Observable<String> {
        val chapterUrl = page.url
        webViewCache[chapterUrl]?.let {
            handler.removeCallbacksAndMessages(chapterUrl)
            postDelayed({ destroyWebView(chapterUrl) }, chapterUrl, webViewIdleDelayMillis, handler)
        } ?: pagesMap[chapterUrl]?.let { pages ->
            if (pages.any { it.imageUrl == null }) {
                pageListParse(chapterUrl)
            }
        }
        return Observable.create { emitter ->
            val imageUrl = pagesMap[chapterUrl]?.get(page.index)?.imageUrl
            if (imageUrl == null) {
                handler.post {
                    webViewCache[chapterUrl]?.evaluateJavascript("loadPic(${page.index})") {}
                }
            }
            GlobalScope.launch {
                val startTime = System.currentTimeMillis()
                while (true) {
                    val url = pagesMap[chapterUrl]?.get(page.index)?.imageUrl
                    if (url != null && url.startsWith("data")) {
                        emitter.onNext("https://127.0.0.1/?image${url.substringAfter(":")}")
                        emitter.onCompleted()
                        break
                    }
                    if (System.currentTimeMillis() - startTime > 30000) {
                        handler.post { webViewCache[chapterUrl]?.evaluateJavascript("scroll()") {} }
                        emitter.onError(Exception("加载图片超时"))
                        break
                    }
                    delay(100)
                }
            }
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        SearchTypeFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        val context = screen.context
        ListPreference(context).apply {
            key = RATE_LIMIT_PREF_KEY
            title = "主站连接限制"
            summary = "此值影响主站的连接请求量。降低此值可以减少获得HTTP 403错误的几率，但加载速度也会变慢。需要重启软件以生效。\n默认值：${RATE_LIMIT_PREF_DEFAULT}\n当前值：%s"
            entries = RATE_LIMIT_PREF_ENTRIES
            entryValues = RATE_LIMIT_PREF_ENTRIES

            setDefaultValue(RATE_LIMIT_PREF_DEFAULT)
        }.also(screen::addPreference)

        ListPreference(context).apply {
            key = RATE_LIMIT_PERIOD_PREF_KEY
            title = "主站连接限制期"
            summary = "此值影响主站点连接限制时的延迟（毫秒）。增加这个值可能会减少出现HTTP 403错误的机会，但加载速度也会变慢。需要重启软件以生效。\n默认值：${RATE_LIMIT_PERIOD_PREF_DEFAULT}\n当前值：%s"
            entries = RATE_LIMIT_PERIOD_PREF_ENTRIES
            entryValues = RATE_LIMIT_PERIOD_PREF_ENTRIES

            setDefaultValue(RATE_LIMIT_PERIOD_PREF_DEFAULT)
        }.also(screen::addPreference)
    }

    private fun randomString() = buildString(15) {
        val charPool = ('a'..'z') + ('A'..'Z')

        for (i in 0 until 15) {
            append(charPool.random())
        }
    }

    @Suppress("UNUSED")
    private class JsInterface(
        private var latch: CountDownLatch,
        private val chapterUrl: String,
        private val pagesMap: MutableMap<String, ArrayList<Page>>,
        private val webViewCache: LinkedHashMap<String, WebView>,
    ) {
        val handler = Handler(Looper.getMainLooper())
        var pageCount = 0
            private set

        @JavascriptInterface
        fun setPageCount(count: Int) {
            if (count <= 0) {
                latch.countDown()
                return
            }
            val pageList = ArrayList<Page>(count).apply {
                for (i in 0 until count) {
                    add(Page(i, url = chapterUrl))
                }
            }
            pagesMap[chapterUrl] = pageList
            pageCount = count
            latch.countDown()
        }

        @JavascriptInterface
        fun setPage(index: Int, url: String) {
            pagesMap[chapterUrl]?.get(index)?.let { it.imageUrl = url }
            pagesMap[chapterUrl]?.let {
                if (it.all { page -> page.imageUrl != null }) {
                    handler.post {
                        webViewCache.remove(chapterUrl)?.destroy()
                    }
                    handler.removeCallbacksAndMessages(chapterUrl)
                }
            }
        }
    }

    companion object {
        internal const val PREFIX_SLUG_SEARCH = "slug:"
    }
}

private const val RATE_LIMIT_PREF_KEY = "mainSiteRatePermitsPreference"
private const val RATE_LIMIT_PREF_DEFAULT = "1"
private val RATE_LIMIT_PREF_ENTRIES = (1..10).map { i -> i.toString() }.toTypedArray()

private const val RATE_LIMIT_PERIOD_PREF_KEY = "mainSiteRatePeriodMillisPreference"
private const val RATE_LIMIT_PERIOD_PREF_DEFAULT = "2500"
private val RATE_LIMIT_PERIOD_PREF_ENTRIES = (2000..6000 step 500).map { i -> i.toString() }.toTypedArray()
