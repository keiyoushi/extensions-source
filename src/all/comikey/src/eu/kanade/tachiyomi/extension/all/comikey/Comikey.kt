package eu.kanade.tachiyomi.extension.all.comikey

import android.annotation.SuppressLint
import android.app.Application
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
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
import keiyoushi.utils.getPreferencesLazy
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
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

    private val preferences by getPreferencesLazy()

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
                    data.updateText.startsWith("toda", true) -> SManga.ONGOING
                    listOf("em pausa", "hiato").any { data.updateText.startsWith(it, true) } -> SManga.ON_HIATUS
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

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val mangaSlug = response.request.url.pathSegments[1]
        val mangaData = json.decodeFromString<ComikeyComic>(
            document.selectFirst("script#comic")!!.data(),
        )
        val defaultChapterPrefix = if (mangaData.format == 2) "episode" else "chapter"

        val chapterUrl = gundamUrl.toHttpUrl().newBuilder().apply {
            val mangaId = response.request.url.pathSegments[2]
            val gundamToken = document.selectFirst("script:containsData(GUNDAM.token)")
                ?.data()
                ?.substringAfter("= \"")
                ?.substringBefore("\";")

            if (gundamToken != null) {
                addPathSegment("comic")
            } else {
                addPathSegment("comic.public")
            }

            addPathSegment(mangaId)
            addPathSegment("episodes")
            addQueryParameter("language", lang.lowercase())
            gundamToken?.let { addQueryParameter("token", gundamToken) }
        }.build()
        val data = json.decodeFromString<ComikeyEpisodeListResponse>(
            client.newCall(GET(chapterUrl, headers))
                .execute()
                .body
                .string(),
        )
        val currentTime = System.currentTimeMillis()

        return data.episodes
            .filter { it.readable || !hideLockedChapters }
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
            .filter { it.date_upload <= currentTime }
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
        val jsInterface = JsInterface(latch, json, intl)
        var webView: WebView? = null

        handler.post {
            val innerWv = WebView(Injekt.get<Application>())

            webView = innerWv
            innerWv.settings.domStorageEnabled = true
            innerWv.settings.javaScriptEnabled = true
            innerWv.settings.blockNetworkImage = true
            innerWv.settings.userAgentString = headers["User-Agent"]
            innerWv.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            innerWv.addJavascriptInterface(jsInterface, interfaceName)

            // Somewhat useful if you need to debug WebView issues. Don't delete.
            //
            /*innerWv.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                    if (consoleMessage == null) { return false }
                    val logContent = "wv: ${consoleMessage.message()} (${consoleMessage.sourceId()}, line ${consoleMessage.lineNumber()})"
                    when (consoleMessage.messageLevel()) {
                        ConsoleMessage.MessageLevel.DEBUG -> Log.d("comikey", logContent)
                        ConsoleMessage.MessageLevel.ERROR -> Log.e("comikey", logContent)
                        ConsoleMessage.MessageLevel.LOG -> Log.i("comikey", logContent)
                        ConsoleMessage.MessageLevel.TIP -> Log.i("comikey", logContent)
                        ConsoleMessage.MessageLevel.WARNING -> Log.w("comikey", logContent)
                        else -> Log.d("comikey", logContent)
                    }

                    return true
                }
            }*/

            innerWv.webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    super.onPageStarted(view, url, favicon)
                    view?.evaluateJavascript(webviewScript.replace("__interface__", interfaceName)) {}
                }

                // If you're logged in, the manifest URL sent to the client is not a direct link;
                // it only redirects to the real one when you call it.
                //
                // In order to avoid a later call and remove an avenue for sniffing out users,
                // we intercept said request so we can grab the real manifest URL.
                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?,
                ): WebResourceResponse? {
                    val url = request?.url
                        ?: return super.shouldInterceptRequest(view, request)

                    if (url.host != "relay-us.epub.rocks" || url.path?.endsWith("/manifest") != true) {
                        return super.shouldInterceptRequest(view, request)
                    }

                    val requestHeaders = headers.newBuilder().apply {
                        request.requestHeaders.entries.forEach {
                            set(it.key, it.value)
                        }

                        removeAll("X-Requested-With")
                    }.build()
                    val response = client.newCall(GET(url.toString(), requestHeaders)).execute()

                    jsInterface.manifestUrl = response.request.url

                    return WebResourceResponse(
                        response.headers["Content-Type"] ?: "application/divina+json+vnd.e4p.drm",
                        null,
                        response.code,
                        response.message,
                        response.headers.toMap(),
                        response.body.byteStream(),
                    )
                }
            }

            innerWv.loadUrl(
                "$baseUrl${chapter.url}",
                buildMap {
                    putAll(headers.toMap())
                    put("X-Requested-With", randomString())
                },
            )
        }

        latch.await(30, TimeUnit.SECONDS)
        handler.post { webView?.destroy() }

        if (latch.count == 1L) {
            throw Exception(intl["error_timed_out_decrypting_image_links"])
        }

        if (jsInterface.error.isNotEmpty()) {
            throw Exception(jsInterface.error)
        }

        val manifestUrl = jsInterface.manifestUrl!!
        val manifest = jsInterface.manifest!!
        val webtoon = manifest.metadata.readingProgression == "ttb"

        return manifest.readingOrder.mapIndexed { i, it ->
            val url = manifestUrl.newBuilder().apply {
                removePathSegment(manifestUrl.pathSize - 1)

                if (it.alternate.isNotEmpty() && it.height == 2048 && it.type == "image/jpeg") {
                    addPathSegments(
                        it.alternate.first {
                            val dimension = if (webtoon) it.width else it.height

                            dimension <= 1536 && it.type == "image/webp"
                        }.href,
                    )
                } else {
                    addPathSegments(it.href)
                }

                addQueryParameter("act", jsInterface.act)
            }.toString()

            Page(i, imageUrl = url)
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()

    override fun getFilterList() = getComikeyFilters(intl)

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_HIDE_LOCKED_CHAPTERS
            title = intl["pref_hide_locked_chapters"]
            summary = intl["pref_hide_locked_chapters_summary"]
            setDefaultValue(false)

            setOnPreferenceChangeListener { _, newValue ->
                preferences.edit().putBoolean(PREF_HIDE_LOCKED_CHAPTERS, newValue as Boolean).commit()
            }
        }.also(screen::addPreference)
    }

    private val hideLockedChapters by lazy {
        preferences.getBoolean(PREF_HIDE_LOCKED_CHAPTERS, false)
    }

    private val webviewScript by lazy {
        javaClass.getResource("/assets/webview-script.js")?.readText()
            ?: throw Exception(intl["error_webview_script_not_found"])
    }

    private fun randomString(): String {
        val length = (10..20).random()

        return buildString(length) {
            val charPool = ('a'..'z') + ('A'..'Z')

            for (i in 0 until length) {
                append(charPool.random())
            }
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

        return "$e4pid/$chapterPrefix-${episode.number.toString().removeSuffix(".0").replace(".", "-")}"
    }

    private class JsInterface(
        private val latch: CountDownLatch,
        private val json: Json,
        private val intl: Intl,
    ) {
        var manifest: ComikeyEpisodeManifest? = null
            private set

        var manifestUrl: HttpUrl? = null

        var act: String = ""
            private set

        var error: String = ""
            private set

        @JavascriptInterface
        @Suppress("UNUSED")
        fun gettext(key: String): String {
            return intl[key]
        }

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passError(msg: String) {
            error = msg
            latch.countDown()
        }

        @JavascriptInterface
        @Suppress("UNUSED")
        fun passPayload(manifestUrl: String, act: String, rawData: String) {
            if (this.manifestUrl == null) {
                this.manifestUrl = manifestUrl.toHttpUrl()
            }

            this.act = act
            manifest = json.decodeFromString<ComikeyEpisodeManifest>(rawData)

            latch.countDown()
        }
    }

    companion object {
        internal const val PREFIX_SLUG_SEARCH = "slug:"
        internal const val PREF_HIDE_LOCKED_CHAPTERS = "hide_locked_chapters"
    }
}
