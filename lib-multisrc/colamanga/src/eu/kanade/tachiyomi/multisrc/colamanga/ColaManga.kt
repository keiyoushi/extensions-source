package eu.kanade.tachiyomi.multisrc.colamanga

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.JavascriptInterface
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
) : ParsedHttpSource(), ConfigurableSource {

    override val supportsLatest = true

    private val intl = ColaMangaIntl(lang)

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

    override fun latestUpdatesRequest(page: Int) =
        GET("$baseUrl/show?orderBy=update&page=$page", headers)

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

    @SuppressLint("SetJavaScriptEnabled")
    override fun pageListParse(document: Document): List<Page> {
        val interfaceName = randomString()

        document.body().prepend(
            """
            <script>
                let pageCount;

                !function() {
                    __cr.isfromMangaRead = 1;
                    __cad.setCookieValue();
                    pageCount = parseInt($.cookie(__cad.getCookieValue()[1] + mh_info.pageid.toString()) || "0");
                    window.$interfaceName.pageCount = pageCount;
                }();

                function imgToBase64(img) {
                    const canvas = document.createElement('canvas');
                    canvas.width = img.naturalWidth, canvas.height = img.naturalHeight;
                    const ctx = canvas.getContext('2d');
                    ctx.drawImage(img, 0, 0);
                    const base64 = canvas.toDataURL('image/jpeg');
                    return base64;
                }

                function newComicPic() {
                    let existPageCount = document.querySelectorAll('.mh_comicpic').length;
                    for (let i = existPageCount; i <= pageCount; i++) {
                        const div = document.createElement('div');
                        div.className = 'mh_comicpic';
                        div.setAttribute('p', i.toString());

                        const img = document.createElement('img');
                        img.setAttribute('d', '');
                        img.setAttribute('waitBind', '');

                        div.appendChild(img);
                        document.body.appendChild(div);
                    }
                }

                function passData() {
                    const divs = document.querySelectorAll('.mh_comicpic');
                    const onLoad = (index, img) => {
                        window.$interfaceName.passData(index, imgToBase64(img));
                    }
                    const onError = (index) => {
                        window.$interfaceName.invalidSrcOrNotCompleted(img.complete, index, img.src.toString());
                    }
                    divs.forEach(div => {
                        const index = parseInt(div.getAttribute('p'));
                        const img = div.querySelector('img');
                        if (!img) return;
                        if (img.complete && img.src.startsWith('blob:')) {
                            onLoad(index, img);
                        } else {
                            img.onload = () => onLoad(index, img);
                            img.onerror = () => onError(index);
                        }
                    });
                }
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
            innerWv.settings.blockNetworkImage = false
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            innerWv.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    view?.evaluateJavascript(
                        """
                        !function() {
                            if (__cr.getPicUrl(1).includes('.enc.webp')) {
                                __cr.thispage = 1;
                                __js.imageLoaded = [];
                                __cr.showPic();

                                newComicPic();
                                __cr.bindEvent;
                                __cr.lazyLoad();

                                passData();
                            }
                            else {
                                for (let i = 1; i <= pageCount; i++) {
                                    window.$interfaceName.passData(i, __cr.getPicUrl(i));
                                }
                            }
                        }();
                        """.trimIndent(),
                        null,
                    )
                }
            }

            innerWv.loadDataWithBaseURL(document.location(), document.outerHtml(), "text/html", "UTF-8", null)
        }

        latch.await(30L, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception(intl.couldNotFetchImages(jsInterface.pageCount, jsInterface.imageList.size))
        }

        return jsInterface.imageList.map { (index, imgSrc) ->
            Page(index, "", urlConverter(imgSrc))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        SearchTypeFilter(intl),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = RATE_LIMIT_PREF_KEY
            title = intl.rateLimitPrefTitle
            summary = intl.rateLimitPrefSummary(RATE_LIMIT_PREF_DEFAULT)
            entries = RATE_LIMIT_PREF_ENTRIES
            entryValues = RATE_LIMIT_PREF_ENTRIES

            setDefaultValue(RATE_LIMIT_PREF_DEFAULT)
        }.also(screen::addPreference)

        ListPreference(screen.context).apply {
            key = RATE_LIMIT_PERIOD_PREF_KEY
            title = intl.rateLimitPeriodPrefTitle
            summary = intl.rateLimitPeriodPrefSummary(RATE_LIMIT_PERIOD_PREF_DEFAULT)
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

    private fun urlConverter(imgSrc: String): String {
        return when {
            imgSrc.startsWith("data:") -> "https://127.0.0.1/?${imgSrc.substringAfter("data:")}"

            imgSrc.startsWith("http") or imgSrc.startsWith("https") -> imgSrc

            else -> "https:$imgSrc"
        }
    }

    @Suppress("UNUSED")
    private class JsInterface(private val latch: CountDownLatch) {
        private val _imageList: MutableMap<Int, String> = mutableMapOf()

        val imageList: Map<Int, String>
            get() = _imageList.toSortedMap()

        @set:JavascriptInterface
        var pageCount: Int = 0
            private set

        @JavascriptInterface
        fun passData(index: Int, imgSrc: String) {
            _imageList[index] = imgSrc
            if (_imageList.size == pageCount) {
                latch.countDown()
            }
        }

        @JavascriptInterface
        fun invalidSrcOrNotCompleted(isCompleted: Boolean, index: Int, imgSrc: String) {
            Log.w("Colamanga", "Invalid img src or not completely loaded: isCompleted: $isCompleted, index: $index, src: $imgSrc")
            latch.countDown()
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
