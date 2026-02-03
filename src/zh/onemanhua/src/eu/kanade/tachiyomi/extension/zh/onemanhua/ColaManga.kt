package eu.kanade.tachiyomi.extension.zh.onemanhua

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
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
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
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
) : ParsedHttpSource(),
    ConfigurableSource {

    override val supportsLatest = true

    private val preferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(
            baseUrl.toHttpUrl(),
            preferences.getString(RATE_LIMIT_PREF_KEY, RATE_LIMIT_PREF_DEFAULT)!!.toInt(),
            preferences.getString(RATE_LIMIT_PERIOD_PREF_KEY, RATE_LIMIT_PERIOD_PREF_DEFAULT)!!.toLong(),
            TimeUnit.MILLISECONDS,
        )
        .addInterceptor(ColaMangaImageInterceptor())
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/show?orderBy=dailyCount&page=$page", headers)

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
    ): Observable<MangasPage> = if (query.startsWith(PREFIX_SLUG_SEARCH)) {
        val slug = query.removePrefix(PREFIX_SLUG_SEARCH)
        val url = "/$slug/"

        fetchMangaDetails(SManga.create().apply { this.url = url })
            .map { MangasPage(listOf(it.apply { this.url = url }), false) }
    } else {
        super.fetchSearchManga(page, query, filters)
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

    @SuppressLint("SetJavaScriptEnabled")
    override fun pageListParse(document: Document): List<Page> {
        val interfaceName = randomString()

        document.body().prepend(
            """
            <script>
                !function () {
                    __cr.init();
                    __cad.setCookieValue();

                    const pageCountKey = __cad.getCookieValue()[1] + mh_info.pageid.toString();
                    const pageCount = parseInt($.cookie(pageCountKey) || "0");
                    const images = [...Array(pageCount).keys()].map((i) => __cr.getPicUrl(i + 1));

                    __cr.isfromMangaRead = 1;

                    const key = CryptoJS.enc.Utf8.stringify(__js.getDataParse());

                    const passData = (keyData = key) => {
                        window.$interfaceName.passData(JSON.stringify({ images, key: keyData }));
                    };

                    $webviewScript
                }();
            </script>
            """.trimIndent(),
        )

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch)
        var webView: WebView? = null

        handler.post {
            val innerWv = WebView(Injekt.get<Application>())
            webView = innerWv
            innerWv.settings.javaScriptEnabled = true
            innerWv.settings.domStorageEnabled = true
            innerWv.settings.blockNetworkImage = true
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            innerWv.loadDataWithBaseURL(document.location(), document.outerHtml(), "text/html", "UTF-8", null)
        }

        latch.await(30L, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception("加载图片超时")
        }

        val key = jsInterface.key

        return jsInterface.images.mapIndexed { i, it ->
            val imageUrl = buildString(it.length + 6) {
                if (it.startsWith("//")) {
                    append("https:")
                }

                append(it)

                if (key.isNotEmpty()) {
                    append("#")
                    append(ColaMangaImageInterceptor.KEY_PREFIX)
                    append(key)
                }
            }

            Page(i, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        SearchTypeFilter(),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = RATE_LIMIT_PREF_KEY
            title = "主站连接限制"
            summary = "此值影响主站的连接请求量。降低此值可以减少获得HTTP 403错误的几率，但加载速度也会变慢。需要重启软件以生效。\n默认值：$RATE_LIMIT_PREF_DEFAULT\n当前值：%s"
            entries = RATE_LIMIT_PREF_ENTRIES
            entryValues = RATE_LIMIT_PREF_ENTRIES

            setDefaultValue(RATE_LIMIT_PREF_DEFAULT)
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = RATE_LIMIT_PERIOD_PREF_KEY
            title = "主站连接限制期"
            summary = "此值影响主站点连接限制时的延迟（毫秒）。增加这个值可能会减少出现HTTP 403错误的机会，但加载速度也会变慢。需要重启软件以生效。\n默认值：$RATE_LIMIT_PERIOD_PREF_DEFAULT\n当前值：%s"
            entries = RATE_LIMIT_PERIOD_PREF_ENTRIES
            entryValues = RATE_LIMIT_PERIOD_PREF_ENTRIES

            setDefaultValue(RATE_LIMIT_PERIOD_PREF_DEFAULT)
        }.also(screen::addPreference)
    }

    private val webviewScript by lazy {
        javaClass.getResource("/assets/webview-script.js")?.readText()
            ?: throw Exception("WebView 脚本不存在")
    }

    private fun randomString() = buildString(15) {
        val charPool = ('a'..'z') + ('A'..'Z')

        for (i in 0 until 15) {
            append(charPool.random())
        }
    }

    @Suppress("UNUSED")
    private class JsInterface(private val latch: CountDownLatch) {
        var images: List<String> = listOf()
            private set

        var key: String = ""
            private set

        @JavascriptInterface
        fun passData(rawData: String) {
            val data = rawData.parseAs<Data>(Json)

            images = data.images
            key = data.key

            latch.countDown()
        }

        @Serializable
        private class Data(val images: List<String>, val key: String)
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
