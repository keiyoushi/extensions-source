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
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.lib.i18n.Intl
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

open class Comikey(
    final override val lang: String,
    override val name: String = "Comikey",
    override val baseUrl: String = "https://comikey.com",
    private val defaultLanguage: String = "en",
) : ParsedHttpSource(), ConfigurableSource {

    private val gundamUrl: String = "https://gundam.comikey.net"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(3)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private val dateFormat by lazy {
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.ROOT)
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    private val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "pt-BR"),
        classLoader = this::class.java.classLoader!!,
    )

    private val preferences by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/comics/?order=-views&page=$page", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun latestUpdatesRequest(page: Int) = GET("$baseUrl/comics/?page=$page", headers)

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
            if (page > 1) {
                addQueryParameter("page", page.toString())
            }

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

        description = element.select("div.excerpt p").text() +
            "\n\n" +
            element.select("div.desc p").text()
        genre = element.select("ul.category-listing li a").joinToString { it.text() }
        thumbnail_url = element.selectFirst("div.image picture img")?.absUrl("src")
    }

    override fun searchMangaNextPageSelector() = "ul.pagination li.next-page:not(.disabled)"

    override fun mangaDetailsParse(document: Document): SManga {
        val data = json.decodeFromString<ComikeyComic>(
            document.selectFirst("script#comic")!!.data(),
        )

        return SManga.create().apply {
            url = data.link
            title = data.name
            author = data.author.joinToString { it.name }
            artist = data.artist.joinToString { it.name }
            description = "\"${data.excerpt}\"\n\n${data.description}"
            thumbnail_url = "$baseUrl${data.fullCover}"
            status = when (data.updateStatus) {
                // HACK: Comikey Brasil
                0 -> when {
                    data.updateText.startsWith("toda", false) -> SManga.ONGOING
                    listOf("em pausa", "hiato").any { data.updateText.startsWith(it, false) } -> SManga.ON_HIATUS
                    else -> SManga.UNKNOWN
                }
                1 -> SManga.COMPLETED
                3 -> SManga.ON_HIATUS
                in (4..14) -> SManga.ONGOING // daily, weekly, bi-weekly, monthly, every day of the week
                else -> SManga.UNKNOWN
            }
            genre = buildList(data.tags.size + 1) {
                addAll(data.tags.map { it.name })

                when (data.format) {
                    0 -> add("Comic")
                    1 -> add("Manga")
                    2 -> add("Webtoon")
                    else -> {}
                }
            }.joinToString()
        }
    }

    private fun makeEpisodeSlug(episode: ComikeyEpisode, defaultChapterPrefix: String): String {
        val e4pid = episode.id.split("-", limit = 2).last()
        val chapterPrefix = if (defaultChapterPrefix == "chapter" && lang != defaultLanguage) {
            when (lang) {
                "es" -> "capitulo-espanol"
                "pt-br" -> "capitulo-portugues"
                "fr" -> "chapitre-francais"
                "id" -> "bab-bahasa"
                else -> "chapter"
            }
        } else {
            defaultChapterPrefix
        }

        return "$e4pid/$chapterPrefix-${episode.number.toString().replace(".", "-")}"
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaData = json.decodeFromString<ComikeyComic>(
            document.selectFirst("script#comic")!!.data(),
        )
        val defaultChapterPrefix = if (mangaData.format == 2) "episode" else "chapter"

        val mangaSlug = response.request.url.pathSegments[1]
        val mangaId = response.request.url.pathSegments[2]
        val data = json.decodeFromString<ComikeyChapterListResponse>(
            client.newCall(
                GET("$gundamUrl/comic.public/$mangaId/episodes?language=${lang.lowercase()}", headers),
            )
                .execute()
                .body
                .string(),
        )

        return data.episodes
            .filter { !it.availability.purchaseEnabled || !hidePaidChapters }
            .map {
                SChapter.create().apply {
                    url = "/read/$mangaSlug/${makeEpisodeSlug(it, defaultChapterPrefix)}/"
                    name = buildString {
                        append(it.title)

                        if (it.subtitle != null) {
                            append(": ")
                            append(it.subtitle)
                        }
                    }
                    chapter_number = it.number
                    date_upload = try {
                        dateFormat.parse(it.releasedAt)!!.time
                    } catch (e: Exception) {
                        0L
                    }
                }
            }
            .reversed()
    }

    override fun chapterListSelector() = throw UnsupportedOperationException()

    override fun chapterFromElement(element: Element) = throw UnsupportedOperationException()

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
            throw Exception(intl["error_timed_out_decrypting_image_links"])
        }

        if (jsInterface.error.isNotEmpty()) {
            val message = if (jsInterface.error.startsWith("[")) {
                val key = jsInterface.error.substringAfter("[").substringBefore("]")

                intl[key]
            } else {
                jsInterface.error
            }

            throw Exception(message)
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

    override fun getFilterList() = getComikeyFilters(intl)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_PAID_CHAPTERS
            title = intl["pref_hide_paid_chapters"]
            summary = intl["pref_hide_paid_chapters_summary"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(PREF_HIDE_PAID_CHAPTERS, newValue as Boolean).commit()
            }
        }.also(screen::addPreference)
    }

    private val hidePaidChapters by lazy {
        preferences.getBoolean(PREF_HIDE_PAID_CHAPTERS, false)
    }

    private val webviewScript by lazy {
        javaClass.getResource("/assets/webview-script.js")?.readText()
            ?: throw Exception(intl["error_webview_script_not_found"])
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
        internal const val PREF_HIDE_PAID_CHAPTERS = "hide_paid_chapters"
    }
}
