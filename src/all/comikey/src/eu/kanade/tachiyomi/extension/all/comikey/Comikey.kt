package eu.kanade.tachiyomi.extension.all.comikey

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class Comikey(override val lang: String) : ParsedHttpSource() {

    override val name = "Comikey"

    override val baseUrl = "https://comikey.com"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comics/?order=-views&page=$page", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comics/", headers)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (query.startsWith(PREFIX_SLUG_SEARCH)) {
            val slug = query.removePrefix(PREFIX_SLUG_SEARCH)
            val url = "/comics/$slug/"

            fetchMangaDetails(SManga.create().apply { this.url = url })
                .map { MangasPage(listOf(it), false) }
        } else {
            super.fetchSearchManga(page, query, filters)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/comics/".toHttpUrl().newBuilder().apply {
            if (query.length >= 2) {
                addQueryParameter("q", query)
            }

            filters.ifEmpty { getFilterList() }
                .filterIsInstance<UriFilter>()
                .forEach { it.addToUri(this) }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaSelector() = "div.series-listing[data-view=list] > ul > li"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("div.series-data span.title a")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.text()
        }

        element
            .selectFirst("div.series-data span.subtitle")
            ?.text()
            ?.removePrefix("by ")
            ?.split(" | ", limit = 2)
            ?.let {
                author = it.getOrNull(0)
                artist = it.getOrNull(1)
            }

        description = element.select("div.excerpt p").text() +
            "\n\n" +
            element.select("div.desc p").text()
        genre = element.select("ul.category-listing li a").joinToString { it.text() }
        thumbnail_url = element.selectFirst("div.image picture img")?.absUrl("src")
    }

    override fun searchMangaNextPageSelector() = "ul.pagination li.next-page:not(.disabled)"

    override fun mangaDetailsParse(document: Document): SManga {
        val comicData = json.decodeFromString<ComikeyComic>(
            document.selectFirst("script#comic")!!.data(),
        )
        val updateDateDiv = document.selectFirst("div.updates div.update-date")

        return SManga.create().apply {
            url = comicData.link
            title = comicData.name
            author = comicData.author.joinToString { it.name }
            artist = comicData.artist.joinToString { it.name }
            description = "\"${comicData.excerpt}\"\n\n${comicData.description}"
            genre = comicData.tags.joinToString { it.name }
            thumbnail_url = "$baseUrl${comicData.fullCover}"
            status = when {
                updateDateDiv?.selectFirst("span.update-text") != null -> SManga.ONGOING
                updateDateDiv?.selectFirst("strong")?.text() == "Completed" -> SManga.COMPLETED
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val comicData = json.decodeFromString<ComikeyComic>(
            document.selectFirst("script#comic")!!.data(),
        )
        val e4pid = comicData.e4pid
        val chapterPrefix = if (comicData.format == 2) "episode" else "chapter"
        val chapterNamePrefix = chapterPrefix.replaceFirstChar { it.titlecase() }
        val chapterData = json.decodeFromString<ComikeyChapterListResponse>(
            client.newCall(
                GET(
                    "https://relay-us.epub.rocks/consumer/COMIKEY/series/$e4pid/content?clientid=dylMNK5a32of",
                    headers,
                ),
            )
                .execute()
                .body
                .string(),
        )
        val currentTime = System.currentTimeMillis()

        return chapterData.data.episodes
            .filter { it.language == lang && (it.saleAt == null || it.saleAt * 1000L <= currentTime) }
            .map {
                val shortId = it.id.split("-", limit = 2).last()

                SChapter.create().apply {
                    url = "/read/${comicData.uslug}/$shortId/$chapterPrefix-${it.number.replace(".", "-")}/#${comicData.id}"
                    name = buildString {
                        append(chapterNamePrefix)
                        append(" ")
                        append(it.number)

                        if (it.name.isNotEmpty()) {
                            append(": ")
                            append(it.name[0].name)
                        }
                    }
                    date_upload = it.publishedAt * 1000L
                    chapter_number = it.number.toFloat()
                }
            }.reversed()
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url.substringBefore("#")}"

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return Observable.fromCallable {
            pageListParse(chapter)
        }
    }

    override fun pageListParse(document: Document) = throw UnsupportedOperationException()

    @SuppressLint("SetJavaScriptEnabled")
    private fun pageListParse(chapter: SChapter): List<Page> {
        val interfaceName = randomString()

        val handler = Handler(Looper.getMainLooper())
        val latch = CountDownLatch(1)
        val jsInterface = JsInterface(latch, json)
        var webView: WebView? = null

        handler.post {
            val innerWv = WebView(Injekt.get<Application>())

            webView = innerWv
            innerWv.settings.domStorageEnabled = true
            innerWv.settings.javaScriptEnabled = true
            innerWv.settings.blockNetworkImage = true
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            innerWv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view?.evaluateJavascript(webviewScript.replace("__interface__", interfaceName)) {}
                }
            }

            innerWv.loadUrl("$baseUrl${chapter.url}")
        }

        latch.await(30, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception("Timed out decrypting image links")
        }

        if (jsInterface.error.isNotEmpty()) {
            throw Exception(jsInterface.error)
        }

        val manifestUrl = jsInterface.manifestUrl.toHttpUrl()

        return jsInterface.images.mapIndexed { i, it ->
            val url = manifestUrl.newBuilder().apply {
                removePathSegment(manifestUrl.pathSegments.size - 1)
                addPathSegments(it)
                addQueryParameter("act", jsInterface.act)
            }.build()

            Page(i, imageUrl = url.toString())
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Please use at least 2 characters when searching by title."),
        Filter.Separator(),
        TypeFilter(),
        SortFilter(),
    )

    private val webviewScript by lazy {
        javaClass.getResource("/assets/webview-script.js")?.readText()
            ?: throw Exception("WebView script not found.")
    }

    private fun randomString() = buildString(15) {
        val charPool = ('a'..'z') + ('A'..'Z')

        for (i in 0 until 15) {
            append(charPool.random())
        }
    }

    private class JsInterface(private val latch: CountDownLatch, private val json: Json) {
        var images: List<String> = emptyList()
            private set

        var manifestUrl: String = ""
            private set

        var act: String = ""
            private set

        var error: String = ""
            private set

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passError(msg: String) {
            error = msg
            latch.countDown()
        }

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passPayload(manifestUrl: String, act: String, rawData: String) {
            this.manifestUrl = manifestUrl
            this.act = act
            images = json.parseToJsonElement(rawData).jsonObject["readingOrder"]!!.jsonArray.map {
                it.jsonObject["href"]!!.jsonPrimitive.content
            }

            latch.countDown()
        }
    }

    companion object {
        internal const val PREFIX_SLUG_SEARCH = "slug:"
    }
}
