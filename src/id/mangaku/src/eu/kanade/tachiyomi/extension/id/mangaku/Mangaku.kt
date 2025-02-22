package eu.kanade.tachiyomi.extension.id.mangaku

import android.annotation.SuppressLint
import android.app.Application
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Mangaku : ParsedHttpSource() {

    override val name = "Mangaku"

    override val baseUrl = "https://mangaku.lat"

    override val lang = "id"

    override val supportsLatest = true

    override val client = network.cloudflareClient

    private lateinit var directory: Elements

    override fun headersBuilder(): Headers.Builder =
        super.headersBuilder().add("Referer", "$baseUrl/")

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return if (page == 1) {
            client.newCall(popularMangaRequest(page))
                .asObservableSuccess()
                .map { popularMangaParse(it) }
        } else {
            Observable.just(parseDirectory(page))
        }
    }

    override fun popularMangaRequest(page: Int): Request =
        POST(
            "$baseUrl/daftar-komik-bahasa-indonesia/",
            headers,
            FormBody.Builder().add("ritem", "hot").build(),
        )

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        directory = document.select(popularMangaSelector())
        return parseDirectory(1)
    }

    private fun parseDirectory(page: Int): MangasPage {
        val manga = mutableListOf<SManga>()
        val end = ((page * 24) - 1).let { if (it <= directory.lastIndex) it else directory.lastIndex }

        for (i in (((page - 1) * 24)..end)) {
            manga.add(popularMangaFromElement(directory[i]))
        }
        return MangasPage(manga, end < directory.lastIndex)
    }

    override fun popularMangaSelector() = "#data .npx .an a"

    override fun popularMangaFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        title = element.ownText()
        thumbnail_url = element.selectFirst("img")?.attr("abs:data-src")
    }

    override fun popularMangaNextPageSelector(): String? = null

    override fun latestUpdatesRequest(page: Int): Request = GET(baseUrl, headers)

    override fun latestUpdatesSelector() = "div.kiri_anime div.utao"

    override fun latestUpdatesFromElement(element: Element): SManga = SManga.create().apply {
        setUrlWithoutDomain(element.select("div.uta div.luf a.series").attr("href"))
        title = element.select("div.uta div.luf a.series").text()
        thumbnail_url = element.select("div.uta div.imgu img").attr("abs:data-src")
    }

    override fun latestUpdatesNextPageSelector(): String? = null

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request =
        GET("$baseUrl/search/$query/", headers)

    override fun searchMangaSelector() = ".listupd .bs"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.select(".bsx a").attr("href"))
        title = element.select(".bigor .tt a").text()
        thumbnail_url = element.select(".bsx img").attr("abs:data-src")
    }

    override fun searchMangaNextPageSelector(): String? = null

    override fun mangaDetailsParse(document: Document): SManga = SManga.create().apply {
        title = document
            .select("h1.titles a, h1.title").text()
            .replace("Bahasa Indonesia", "").trim()

        thumbnail_url = document
            .select("#sidebar-a a[imageanchor] > img, #abc a[imageanchor] > img")
            .attr("abs:src")

        genre = document.select(".inf:contains(Genre) p a, .inf:contains(Type) p").joinToString { it.text() }
        document.select("#wrapper-a #content-a .inf, #abc .inf").forEach { row ->
            when (row.select(".infx").text()) {
                "Author" -> author = row.select("p").text()
                "Sinopsis" -> description = row.select("p").text()
            }
        }
        val altName = document.selectFirst(".inf:contains(Alternative) p")?.ownText().takeIf { it.isNullOrBlank().not() }
        altName?.let {
            description = "$description\n\nAlternative Name: $altName".trim()
        }
    }

    override fun chapterListSelector() = "#content-b > div > a, .fndsosmed-social + div > a"

    override fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.text().let {
            if (it.contains("–")) {
                it.split("–")[1].trim()
            } else {
                it
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun pageListParse(document: Document): List<Page> {
        val interfaceName = randomString()

        val decodeScriptOriginal = document
            .select("script:containsData(dtx = )")
            .joinToString("\n") { it.data() }
        val decodeScript = decodeScriptOriginal.replace(urlsnxRe) {
            it.value + "window.$interfaceName.passPayload(JSON.stringify(urlsnx));"
        }

        val wpRoutineUrl = document
            .selectFirst("script[src*=wp-routine]")!!
            .attr("abs:src")
        val wpRoutineScript = client
            .newCall(GET(wpRoutineUrl, headers))
            .execute().use { it.body.string() }

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch)
        var webView: WebView? = null

        handler.post {
            val webview = WebView(Injekt.get<Application>())
            webView = webview
            webview.settings.javaScriptEnabled = true
            webview.settings.blockNetworkLoads = true
            webview.settings.blockNetworkImage = true
            webview.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            webview.addJavascriptInterface(jsInterface, interfaceName)

            webview.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView, url: String) {
                    view.evaluateJavascript(jQueryScript) {}
                    view.evaluateJavascript(cryptoJSScript) {}
                    view.evaluateJavascript(wpRoutineScript) {}
                    view.evaluateJavascript(decodeScript) {}
                }
            }
            webview.loadDataWithBaseURL(
                document.location(),
                "",
                "text/html",
                "UTF-8",
                null,
            )
        }

        // 5s is ten times over the execution time on a crappy emulator
        latch.await(5, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception("Kehabisan waktu saat men-decrypt tautan gambar") // Timeout while decrypting image links
        }

        return jsInterface.images.mapIndexed { i, url ->
            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    private val urlsnxRe = Regex("""urlsnx=(?!\[];)[^;]+;""")

    private fun randomString(length: Int = 10): String {
        val charPool = ('a'..'z') + ('A'..'Z')
        return List(length) { charPool.random() }.joinToString("")
    }

    internal class JsInterface(private val latch: CountDownLatch) {

        private val json: Json by injectLazy()

        var images: List<String> = listOf()
            private set

        @JavascriptInterface
        fun passPayload(rawData: String) {
            val data = json.parseToJsonElement(rawData).jsonArray
            images = data.map { it.jsonPrimitive.content }
            latch.countDown()
        }
    }

    private val jQueryScript by lazy {
        javaClass
            .getResource("/assets/zepto.min.js")!!
            .readText() // Zepto v1.2.0 (jQuery compatible)
    }

    private val cryptoJSScript by lazy {
        javaClass
            .getResource("/assets/crypto-js.min.js")!!
            .readText() // CryptoJS v4.0.0 (on site: cpr2.js)
    }
}
