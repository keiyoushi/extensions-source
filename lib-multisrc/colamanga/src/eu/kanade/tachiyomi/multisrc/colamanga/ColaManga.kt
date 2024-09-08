package eu.kanade.tachiyomi.multisrc.colamanga

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.synchrony.Deobfuscator
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

abstract class ColaManga(
    final override val name: String,
    final override val baseUrl: String,
    final override val lang: String,
) : ParsedHttpSource(), ConfigurableSource {

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val intl = ColaMangaIntl(lang)

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

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

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.fed-part-eone")!!.text()
        thumbnail_url = document.selectFirst("a.fed-list-pics")?.absUrl("data-orignal")
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

                    window.$interfaceName.passData(JSON.stringify({ images, key }), window.image_info.keyType || "0");
                }();
            </script>
            """.trimIndent(),
        )

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch, json)
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
            throw Exception(intl.timedOutDecryptingImageLinks)
        }

        val key = if (jsInterface.keyType.isNotEmpty()) {
            keyMapping[jsInterface.keyType]
                ?: throw Exception(intl.couldNotFindKey(jsInterface.keyType))
        } else {
            jsInterface.key
        }

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

    private val keyMappingRegex = Regex("""if\s*\(\s*([a-zA-Z0-9_]+)\s*==\s*(?<keyType>\d+)\s*\)\s*\{\s*return\s*'(?<key>[a-zA-Z0-9_]+)'\s*;""")

    private val keyMapping by lazy {
        val obfuscatedReadJs = client.newCall(GET("$baseUrl/js/manga.read.js")).execute().body.string()
        val readJs = Deobfuscator.deobfuscateScript(obfuscatedReadJs)
            ?: throw Exception(intl.couldNotDeobufscateScript)

        keyMappingRegex.findAll(readJs).associate { it.groups["keyType"]!!.value to it.groups["key"]!!.value }
    }

    private fun randomString() = buildString(15) {
        val charPool = ('a'..'z') + ('A'..'Z')

        for (i in 0 until 15) {
            append(charPool.random())
        }
    }

    @Suppress("UNUSED")
    private class JsInterface(private val latch: CountDownLatch, private val json: Json) {
        var images: List<String> = listOf()
            private set

        var key: String = ""
            private set

        var keyType: String = ""
            private set

        @JavascriptInterface
        fun passData(rawData: String, keyType: String) {
            val data = json.parseToJsonElement(rawData).jsonObject

            images = data["images"]!!.jsonArray.map { it.jsonPrimitive.content }
            key = data["key"]!!.jsonPrimitive.content

            if (keyType != "0") {
                this.keyType = keyType
            }

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
