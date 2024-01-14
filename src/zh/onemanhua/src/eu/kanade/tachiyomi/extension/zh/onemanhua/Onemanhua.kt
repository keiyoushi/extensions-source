package eu.kanade.tachiyomi.extension.zh.onemanhua

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
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
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

// Originally, the site was called One漫画. The name has been changing every once in awhile
class Onemanhua : ConfigurableSource, ParsedHttpSource() {
    override val id = 8252565807829914103 // name used to be "One漫画"
    override val lang = "zh"
    override val supportsLatest = true
    override val name = "COLA漫画 (OH漫画)"
    override val baseUrl = "https://www.colamanga.com"

    // Preference setting
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
        .addInterceptor { chain ->
            val response = chain.proceed(chain.request())

            if (response.request.url.fragment?.contains("key") != true) {
                return@addInterceptor response
            }

            val keyStr = response.request.url.fragment!!.substringAfter("key=")
            val key = keyStr.toByteArray()

            val cipher = Cipher.getInstance("AES/CBC/PKCS7Padding")
            cipher.init(
                Cipher.DECRYPT_MODE,
                SecretKeySpec(key, "AES"),
                IvParameterSpec("0000000000000000".toByteArray()),
            )

            val output = cipher.doFinal(response.body.bytes())
            response.newBuilder()
                .body(output.toResponseBody("image/jpeg".toMediaType()))
                .build()
        }
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Origin", baseUrl)
        .add("Referer", "$baseUrl/")

    // Common
    private var commonSelector = "li.fed-list-item"
    private var commonNextPageSelector = "a:contains(下页):not(.fed-btns-disad)"
    private fun commonMangaFromElement(element: Element): SManga {
        val picElement = element.selectFirst("a.fed-list-pics")!!
        val manga = SManga.create().apply {
            title = element.selectFirst("a.fed-list-title")!!.text()
            thumbnail_url = picElement.attr("data-original")
        }

        manga.setUrlWithoutDomain(picElement.attr("href"))

        return manga
    }

    // Popular Manga
    override fun popularMangaRequest(page: Int) = GET("$baseUrl/show?orderBy=dailyCount&page=$page", headers)
    override fun popularMangaNextPageSelector() = commonNextPageSelector
    override fun popularMangaSelector() = commonSelector
    override fun popularMangaFromElement(element: Element) = commonMangaFromElement(element)

    // Latest Updates
    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/show?orderBy=update&page=$page", headers)
    override fun latestUpdatesNextPageSelector() = commonNextPageSelector
    override fun latestUpdatesSelector() = commonSelector
    override fun latestUpdatesFromElement(element: Element) = commonMangaFromElement(element)

    // Filter
    private class StatusFilter : Filter.TriState("已完结")
    private class SortFilter : Filter.Select<String>("排序", arrayOf("更新日", "收录日", "日点击", "月点击"), 2)

    override fun getFilterList() = FilterList(
        SortFilter(),
        StatusFilter(),
    )

    // Search
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (query.isNotBlank()) {
            GET("$baseUrl/search?searchString=$query&page=$page", headers)
        } else {
            val url = "$baseUrl/show".toHttpUrl().newBuilder()
            url.addQueryParameter("page", page.toString())

            filters.forEach { filter ->
                when (filter) {
                    is StatusFilter -> {
                        if (!filter.isIgnored()) {
                            url.addQueryParameter("status", arrayOf("0", "2", "1")[filter.state])
                        }
                    }
                    is SortFilter -> {
                        url.addQueryParameter("orderBy", arrayOf("update", "create", "dailyCount", "weeklyCount", "monthlyCount")[filter.state])
                    }
                    else -> {}
                }
            }
            GET(url.toString(), headers)
        }
    }

    override fun searchMangaNextPageSelector() = commonNextPageSelector
    override fun searchMangaSelector() = "dl.fed-deta-info, $commonSelector"
    override fun searchMangaFromElement(element: Element): SManga {
        if (element.tagName() == "li") {
            return commonMangaFromElement(element)
        }

        val picElement = element.selectFirst("a.fed-list-pics")!!
        val manga = SManga.create().apply {
            title = element.selectFirst("h1.fed-part-eone a")!!.text()
            thumbnail_url = picElement.attr("data-original")
        }

        manga.setUrlWithoutDomain(picElement.attr("href"))

        return manga
    }

    override fun mangaDetailsParse(document: Document): SManga {
        val picElement = document.selectFirst("a.fed-list-pics")!!
        val detailElements = document.select("ul.fed-part-rows li.fed-col-xs12")
        return SManga.create().apply {
            title = document.selectFirst("h1.fed-part-eone")!!.text().trim()
            thumbnail_url = picElement.attr("data-original")

            status = when (
                detailElements.firstOrNull {
                    it.children().firstOrNull { it2 ->
                        it2.hasClass("fed-text-muted") && it2.ownText() == "状态"
                    } != null
                }?.select("a")?.first()?.text()
            ) {
                "连载中" -> SManga.ONGOING
                "已完结" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }

            author = detailElements.firstOrNull {
                it.children().firstOrNull { it2 ->
                    it2.hasClass("fed-text-muted") && it2.ownText() == "作者"
                } != null
            }?.select("a")?.first()?.text()

            genre = detailElements.firstOrNull {
                it.children().firstOrNull { it2 ->
                    it2.hasClass("fed-text-muted") && it2.ownText() == "类别"
                } != null
            }?.select("a")?.joinToString { it.text() }

            description = document.select("ul.fed-part-rows li.fed-col-xs12.fed-show-md-block .fed-part-esan")
                .firstOrNull()?.text()?.trim()
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

    private fun randomString(length: Int = 10): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    internal class JsObject(val latch: CountDownLatch, val cookieManager: CookieManager) {

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
            val webview = WebView(Injekt.get<Application>())
            webView = webview
            webview.settings.javaScriptEnabled = true
            webview.settings.domStorageEnabled = true
            webview.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            webview.settings.useWideViewPort = false
            webview.settings.loadWithOverviewMode = false
            webview.settings.userAgentString = webview.settings.userAgentString.replace("Mobile", "eliboM").replace("Android", "diordnA")
            webview.addJavascriptInterface(jsInterface, interfaceName)

            webview.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (consoleMessage == null) { return false }
                    val logContent = "wv: ${consoleMessage.message()} (${consoleMessage.sourceId()}, line ${consoleMessage.lineNumber()})"
                    when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.DEBUG -> Log.d("onemanhua", logContent)
                        ConsoleMessage.MessageLevel.ERROR -> Log.e("onemanhua", logContent)
                        ConsoleMessage.MessageLevel.LOG -> Log.i("onemanhua", logContent)
                        ConsoleMessage.MessageLevel.TIP -> Log.i("onemanhua", logContent)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w("onemanhua", logContent)
                        else -> Log.d("onemanhua", logContent)
                    }

                    return true
                }
            }
            webview.loadDataWithBaseURL(document.location(), document.toString(), "text/html", "UTF-8", null)
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
            Page(i, imageUrl = imageUrl + "#key=$key")
        }
    }

    override fun imageUrlParse(document: Document) = ""

    override fun setupPreferenceScreen(screen: androidx.preference.PreferenceScreen) {
        val mainSiteRatePermitsPreference = androidx.preference.ListPreference(screen.context).apply {
            key = MAINSITE_RATEPERMITS_PREF
            title = MAINSITE_RATEPERMITS_PREF_TITLE
            entries = MAINSITE_RATEPERMITS_PREF_ENTRIES_ARRAY
            entryValues = MAINSITE_RATEPERMITS_PREF_ENTRIES_ARRAY
            summary = MAINSITE_RATEPERMITS_PREF_SUMMARY

            setDefaultValue(MAINSITE_RATEPERMITS_PREF_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(MAINSITE_RATEPERMITS_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        val mainSiteRatePeriodPreference = androidx.preference.ListPreference(screen.context).apply {
            key = MAINSITE_RATEPERIOD_PREF
            title = MAINSITE_RATEPERIOD_PREF_TITLE
            entries = MAINSITE_RATEPERIOD_PREF_ENTRIES_ARRAY
            entryValues = MAINSITE_RATEPERIOD_PREF_ENTRIES_ARRAY
            summary = MAINSITE_RATEPERIOD_PREF_SUMMARY

            setDefaultValue(MAINSITE_RATEPERIOD_PREF_DEFAULT)
            setOnPreferenceChangeListener { _, newValue ->
                try {
                    val setting = preferences.edit().putString(MAINSITE_RATEPERIOD_PREF, newValue as String).commit()
                    setting
                } catch (e: Exception) {
                    e.printStackTrace()
                    false
                }
            }
        }

        screen.addPreference(mainSiteRatePermitsPreference)
        screen.addPreference(mainSiteRatePeriodPreference)
    }

    companion object {
        private const val MAINSITE_RATEPERMITS_PREF = "mainSiteRatePermitsPreference"
        private const val MAINSITE_RATEPERMITS_PREF_DEFAULT = "1"

        /** main site's connection limit */
        private const val MAINSITE_RATEPERMITS_PREF_TITLE = "主站连接限制"

        /** This value affects connection request amount to main site. Lowering this value may reduce the chance to get HTTP 403 error, but loading speed will be slower too. Tachiyomi restart required. Current value: %s" */
        private const val MAINSITE_RATEPERMITS_PREF_SUMMARY = "此值影响主站的连接请求量。降低此值可以减少获得HTTP 403错误的几率，但加载速度也会变慢。需要重启软件以生效。\n默认值：$MAINSITE_RATEPERMITS_PREF_DEFAULT \n当前值：%s"
        private val MAINSITE_RATEPERMITS_PREF_ENTRIES_ARRAY = (1..10).map { i -> i.toString() }.toTypedArray()

        private const val MAINSITE_RATEPERIOD_PREF = "mainSiteRatePeriodMillisPreference"
        private const val MAINSITE_RATEPERIOD_PREF_DEFAULT = "2500"

        /** main site's connection limit period */
        private const val MAINSITE_RATEPERIOD_PREF_TITLE = "主站连接限制期"

        /** This value affects the delay when hitting the connection limit to main site. Increasing this value may reduce the chance to get HTTP 403 error, but loading speed will be slower too. Tachiyomi restart required. Current value: %s" */
        private const val MAINSITE_RATEPERIOD_PREF_SUMMARY = "此值影响主站点连接限制时的延迟（毫秒）。增加这个值可能会减少出现HTTP 403错误的机会，但加载速度也会变慢。需要重启软件以生效。\n默认值：$MAINSITE_RATEPERIOD_PREF_DEFAULT\n当前值：%s"
        private val MAINSITE_RATEPERIOD_PREF_ENTRIES_ARRAY = (2000..6000 step 500).map { i -> i.toString() }.toTypedArray()
    }
}
