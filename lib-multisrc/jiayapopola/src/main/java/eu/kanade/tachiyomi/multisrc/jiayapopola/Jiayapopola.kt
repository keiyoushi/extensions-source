package eu.kanade.tachiyomi.multisrc.jiayapopola

import android.annotation.SuppressLint
import android.app.Application
import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.webkit.ConsoleMessage
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.preference.ListPreference
import androidx.preference.PreferenceScreen
import eu.kanade.tachiyomi.lib.synchrony.Deobfuscator
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Headers
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

abstract class Jiayapopola(
    override val name: String,
    final override val baseUrl: String,
    override val lang: String,
) : ConfigurableSource, ParsedHttpSource() {

    override val supportsLatest = true

    private val preferences: SharedPreferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    private val cookieManager by lazy { CookieManager.getInstance() }

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimitHost(
            baseUrl.toHttpUrl(),
            preferences.getString(MAINSITE_RATEPERMITS_PREF, MAINSITE_RATEPERMITS_PREF_DEFAULT)!!.toInt(),
            preferences.getString(MAINSITE_RATEPERIOD_PREF, MAINSITE_RATEPERIOD_PREF_DEFAULT)!!.toLong(),
            TimeUnit.MILLISECONDS,
        )
        .addInterceptor(::imageIntercept)
        .build()

    override fun headersBuilder() = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/show?orderBy=dailyCount&page=$page", headers)

    override fun popularMangaSelector() = "li.fed-list-item"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        val picElement = element.selectFirst("a.fed-list-pics")!!

        setUrlWithoutDomain(picElement.attr("href"))
        title = element.selectFirst("a.fed-list-title")!!.text()
        thumbnail_url = picElement.attr("data-original")
    }

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/show?orderBy=update&page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
                addQueryParameter("query", query)
                addQueryParameter("page", page.toString())
            }.build()

            GET(url, headers)
        } else {
            val url = "$baseUrl/show".toHttpUrl().newBuilder()
            url.addQueryParameter("page", page.toString())

            (if (filters.isEmpty()) getFilterList() else filters).filterIsInstance<UrlFilter>()
                .forEach { it.addToUrl(url) }

            GET(url.build(), headers)
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun searchMangaFromElement(element: Element): SManga {
        if (element.tagName() == "li") {
            return popularMangaFromElement(element)
        }

        val picElement = element.selectFirst("a.fed-list-pics")!!
        val manga = SManga.create().apply {
            setUrlWithoutDomain(picElement.attr("href"))
            title = element.selectFirst("h1.fed-part-eone a")!!.text()
            thumbnail_url = picElement.attr("data-original")
        }

        return manga
    }

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    protected abstract val statusTitle: String

    protected abstract val authorTitle: String

    protected abstract val genreTitle: String

    protected abstract val statusOngoing: String

    protected abstract val statusCompleted: String

    override fun mangaDetailsParse(document: Document): SManga {
        val picElement = document.selectFirst("a.fed-list-pics")!!
        val detailElements = document.select("ul.fed-part-rows li.fed-col-xs12")

        return SManga.create().apply {
            title = document.selectFirst("h1.fed-part-eone")!!.text().trim()
            thumbnail_url = picElement.attr("data-original")

            status = when (
                detailElements.firstOrNull {
                    it.children().firstOrNull { it2 ->
                        it2.hasClass("fed-text-muted") && it2.ownText() == statusTitle
                    } != null
                }?.select("a")?.first()?.text()
            ) {
                statusOngoing -> SManga.ONGOING
                statusCompleted -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            author = detailElements.firstOrNull {
                it.children().firstOrNull { it2 ->
                    it2.hasClass("fed-text-muted") && it2.ownText() == authorTitle
                } != null
            }?.select("a")?.first()?.text()

            genre = detailElements.firstOrNull {
                it.children().firstOrNull { it2 ->
                    it2.hasClass("fed-text-muted") && it2.ownText() == genreTitle
                } != null
            }?.select("a")?.joinToString { it.text() }

            description = document
                .selectFirst("ul.fed-part-rows li.fed-col-xs12.fed-show-md-block .fed-part-esan")
                ?.ownText()
        }
    }

    override fun chapterListSelector(): String = "div:not(.fed-hidden) > div.all_data_list > ul.fed-part-rows a"

    override fun chapterFromElement(element: Element): SChapter {
        val chapter = SChapter.create().apply {
            name = element.attr("title")
        }
        chapter.setUrlWithoutDomain(element.attr("href"))
        return chapter
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun pageListParse(document: Document): List<Page> {
        val interfaceName = randomString()
        document.body().prepend(
            """
            <script>
                (function () {
                    __cr.init();
                    __cad.setCookieValue();

                    const pageCountKey = __cad.getCookieValue()[1] + mh_info.pageid.toString();
                    const pageCount = parseInt($.cookie(pageCountKey) || "0");

                    const images = [...Array(pageCount).keys()].map((i) => __cr.getPicUrl(i + 1));

                    __cr.isfromMangaRead = 1
                    const key = CryptoJS.enc.Utf8.stringify(__js.getDataParse())

                    if (!window.image_info.keyType || window.image_info.keyType != "0") {
                        window.$interfaceName.passKeyType(window.image_info.keyType)
                    }
                    window.$interfaceName.passJsonData(JSON.stringify({ images, key }))
                })();
            </script>
            """.trimIndent(),
        )

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsObject(latch, cookieManager)
        var webView: WebView? = null

        handler.post {
            val innerWv = WebView(Injekt.get<Application>())
            webView = innerWv
            innerWv.settings.javaScriptEnabled = true
            innerWv.settings.domStorageEnabled = true
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.settings.useWideViewPort = false
            innerWv.settings.loadWithOverviewMode = false
            innerWv.settings.userAgentString = innerWv.settings.userAgentString.replace("Mobile", "eliboM").replace("Android", "diordnA")
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            innerWv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (consoleMessage == null) { return false }
                    val logContent = "wv: ${consoleMessage.message()} (${consoleMessage.sourceId()}, line ${consoleMessage.lineNumber()})"
                    when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.DEBUG -> Log.d(logTag, logContent)
                        ConsoleMessage.MessageLevel.ERROR -> Log.e(logTag, logContent)
                        ConsoleMessage.MessageLevel.LOG -> Log.i(logTag, logContent)
                        ConsoleMessage.MessageLevel.TIP -> Log.i(logTag, logContent)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w(logTag, logContent)
                        else -> Log.d(logTag, logContent)
                    }

                    return true
                }
            }
            innerWv.loadDataWithBaseURL(document.location(), document.toString(), "text/html", "UTF-8", null)
        }

        latch.await()
        handler.post { webView?.destroy() }

        val key = if (jsInterface.keyType.isNotEmpty()) {
            keyMapping[jsInterface.keyType]
                ?: throw Exception("Could not find key mapping for keyType ${jsInterface.keyType}")
        } else {
            jsInterface.key
        }

        return jsInterface.images.mapIndexed { i, url ->
            var imageUrl = url

            if (imageUrl.startsWith("//")) {
                imageUrl = "https:$imageUrl"
            }

            Page(i, imageUrl = "$imageUrl#key=$key")
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        ListPreference(screen.context).apply {
            key = MAINSITE_RATEPERMITS_PREF
            title = mainSiteRateLimitPrefTitle
            entries = MAINSITE_RATEPERMITS_PREF_ENTRIES_ARRAY
            entryValues = MAINSITE_RATEPERMITS_PREF_ENTRIES_ARRAY
            summary = mainSiteRateLimitPrefSummary

            setDefaultValue(MAINSITE_RATEPERMITS_PREF_DEFAULT)
        }.apply(screen::addPreference)

        ListPreference(screen.context).apply {
            key = MAINSITE_RATEPERIOD_PREF
            title = mainSiteRateLimitPeriodPrefTitle
            entries = MAINSITE_RATEPERIOD_PREF_ENTRIES_ARRAY
            entryValues = MAINSITE_RATEPERIOD_PREF_ENTRIES_ARRAY
            summary = mainSiteRateLimitPeriodPrefSummary

            setDefaultValue(MAINSITE_RATEPERIOD_PREF_DEFAULT)
        }.apply(screen::addPreference)
    }

    private fun randomString(length: Int = 10): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    @Suppress("UNUSED")
    internal class JsObject(private val latch: CountDownLatch, val cookieManager: CookieManager) {
        private val json: Json by injectLazy()

        var images: List<String> = listOf()
            private set

        var key: String = ""
            private set

        var keyType: String = ""
            private set

        @JavascriptInterface
        fun passJsonData(rawData: String) {
            val data = json.parseToJsonElement(rawData).jsonObject
            images = data["images"]!!.jsonArray.map { it.jsonPrimitive.content }
            key = data["key"]!!.jsonPrimitive.content

            latch.countDown()
        }

        @JavascriptInterface
        fun passKeyType(key: String) {
            keyType = key
        }
    }

    private val keyMappingRegex = Regex("""[0-9A-Za-z_]+\s*==\s*['"](?<keyType>\d+)['"]\s*&&\s*\([0-9A-Za-z_]+\s*=\s*['"](?<key>[a-zA-Z0-9]+)['"]\)""")

    private val keyMapping by lazy {
        val obfuscatedReadJs = client.newCall(GET("$baseUrl/js/manga.read.js")).execute().body.string()
        val readJs = Deobfuscator.deobfuscateScript(obfuscatedReadJs)
            ?: throw Exception("Could not deobufuscate manga.read.js")

        keyMappingRegex.findAll(readJs).associate { it.groups["keyType"]!!.value to it.groups["key"]!!.value }
    }

    protected interface UrlFilter {
        fun addToUrl(builder: HttpUrl.Builder)
    }

    protected open class QueryFilter(
        name: String,
        private val query: String,
        private val vals: Array<Pair<String, String>>,
        state: Int = 0,
    ) : Filter.Select<String>(name, vals.map { it.first }.toTypedArray(), state), UrlFilter {
        override fun addToUrl(builder: HttpUrl.Builder) {
            builder.addQueryParameter(query, vals[state].second)
        }
    }

    private fun imageIntercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)

        val fragment = request.url.fragment ?: return response
        val keyStr = fragment.substringAfter("key=")

        if (keyStr.isBlank()) {
            return response
        }

        val key = keyStr.toByteArray()

        val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(ByteArray(16) { _ -> 0x30 }),
        )

        val output = cipher.doFinal(response.body.bytes())

        return response.newBuilder()
            .body(output.toResponseBody("image/jpeg".toMediaType()))
            .build()
    }

    protected abstract val logTag: String

    protected abstract val mainSiteRateLimitPrefTitle: String

    protected abstract val mainSiteRateLimitPrefSummary: String

    protected abstract val mainSiteRateLimitPeriodPrefTitle: String

    protected abstract val mainSiteRateLimitPeriodPrefSummary: String

    companion object {
        private const val MAINSITE_RATEPERMITS_PREF = "mainSiteRatePermitsPreference"
        const val MAINSITE_RATEPERMITS_PREF_DEFAULT = "1"

        private val MAINSITE_RATEPERMITS_PREF_ENTRIES_ARRAY = (1..10).map { i -> i.toString() }.toTypedArray()

        private const val MAINSITE_RATEPERIOD_PREF = "mainSiteRatePeriodMillisPreference"
        const val MAINSITE_RATEPERIOD_PREF_DEFAULT = "2500"

        private val MAINSITE_RATEPERIOD_PREF_ENTRIES_ARRAY = (2000..6000 step 500).map { i -> i.toString() }.toTypedArray()
    }
}
