package eu.kanade.tachiyomi.extension.all.mangafire

import android.annotation.SuppressLint
import android.app.Application
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.await
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import okhttp3.internal.charset
import okio.Buffer
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

class MangaFire(
    override val lang: String,
    private val langCode: String = lang,
) : ConfigurableSource, HttpSource() {
    override val name = "MangaFire"

    override val baseUrl = "https://mangafire.to"

    override val supportsLatest = true
    private val preferences by getPreferencesLazy()

    override val client = network.cloudflareClient.newBuilder()
        .addInterceptor(ImageInterceptor)
        .apply {
            val naiveTrustManager = @SuppressLint("CustomX509TrustManager")
            object : X509TrustManager {
                override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
                override fun checkClientTrusted(certs: Array<X509Certificate>, authType: String) = Unit
                override fun checkServerTrusted(certs: Array<X509Certificate>, authType: String) = Unit
            }

            val insecureSocketFactory = SSLContext.getInstance("SSL").apply {
                val trustAllCerts = arrayOf<TrustManager>(naiveTrustManager)
                init(null, trustAllCerts, SecureRandom())
            }.socketFactory

            sslSocketFactory(insecureSocketFactory, naiveTrustManager)
            hostnameVerifier { _, _ -> true }
        }
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int): Request {
        return searchMangaRequest(
            page,
            "",
            FilterList(SortFilter(defaultValue = "most_viewed")),
        )
    }

    override fun popularMangaParse(response: Response) = searchMangaParse(response)

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return searchMangaRequest(
            page,
            "",
            FilterList(SortFilter(defaultValue = "recently_updated")),
        )
    }

    override fun latestUpdatesParse(response: Response) = searchMangaParse(response)

    // =============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder().apply {
            addPathSegment("filter")

            if (query.isNotBlank()) {
                addQueryParameter("keyword", query.trim())
            }

            val filterList = filters.ifEmpty { getFilterList() }
            filterList.filterIsInstance<UriFilter>().forEach {
                it.addToUri(this)
            }

            addQueryParameter("language[]", langCode)
            addQueryParameter("page", page.toString())

            if (query.isNotBlank()) {
                val vrf = VrfGenerator.generate(query.trim())
                addQueryParameter("vrf", vrf)
            }
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        var entries = document.select(searchMangaSelector()).map(::searchMangaFromElement)
        if (preferences.getBoolean(SHOW_VOLUME_PREF, false)) {
            entries = entries.flatMapTo(ArrayList(entries.size * 2)) { manga ->
                val volume = SManga.create().apply {
                    url = manga.url + VOLUME_URL_SUFFIX
                    title = VOLUME_TITLE_PREFIX + manga.title
                    thumbnail_url = manga.thumbnail_url
                }
                listOf(manga, volume)
            }
        }
        val hasNextPage = document.selectFirst(searchMangaNextPageSelector()) != null
        return MangasPage(entries, hasNextPage)
    }

    private fun searchMangaNextPageSelector() = ".page-item.active + .page-item .page-link"

    private fun searchMangaSelector() = ".original.card-lg .unit .inner"

    private fun searchMangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst(".info > a")!!.let {
            setUrlWithoutDomain(it.attr("href"))
            title = it.ownText()
        }
        thumbnail_url = element.selectFirst("img")?.attr("abs:src")
    }

    // =============================== Filters ==============================

    override fun getFilterList() = FilterList(
        TypeFilter(),
        GenreFilter(),
        GenreModeFilter(),
        StatusFilter(),
        YearFilter(),
        MinChapterFilter(),
        SortFilter(),
    )

    // =========================== Manga Details ============================

    override fun getMangaUrl(manga: SManga) = baseUrl + manga.url.removeSuffix(VOLUME_URL_SUFFIX)

    override fun mangaDetailsParse(response: Response): SManga {
        return mangaDetailsParse(response.asJsoup()).apply {
            if (response.request.url.fragment == VOLUME_URL_FRAGMENT) {
                title = VOLUME_TITLE_PREFIX + title
            }
        }
    }

    private fun mangaDetailsParse(document: Document) = SManga.create().apply {
        with(document.selectFirst(".main-inner:not(.manga-bottom)")!!) {
            title = selectFirst("h1")!!.text()
            thumbnail_url = selectFirst(".poster img")?.attr("src")
            status = selectFirst(".info > p").parseStatus()
            description = buildString {
                document.selectFirst("#synopsis .modal-content")?.textNodes()?.let {
                    append(it.joinToString("\n\n"))
                }

                selectFirst("h6")?.let {
                    append("\n\nAlternative title: ${it.text()}")
                }
            }.trim()

            selectFirst(".meta")?.let {
                author = it.selectFirst("span:contains(Author:) + span")?.text()
                val type = it.selectFirst("span:contains(Type:) + span")?.text()
                val genres = it.selectFirst("span:contains(Genres:) + span")?.text()
                genre = listOfNotNull(type, genres).joinToString()
            }
        }
    }

    private fun Element?.parseStatus(): Int = when (this?.text()?.lowercase()) {
        "releasing" -> SManga.ONGOING
        "completed" -> SManga.COMPLETED
        "on_hiatus" -> SManga.ON_HIATUS
        "discontinued" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================

    override fun getChapterUrl(chapter: SChapter): String {
        return baseUrl + chapter.url.substringBeforeLast("#")
    }

    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = manga.url.removeSuffix(VOLUME_URL_SUFFIX).substringAfterLast(".")
        val type = if (manga.url.endsWith(VOLUME_URL_SUFFIX)) "volume" else "chapter"

        return GET("$baseUrl/ajax/manga/$mangaId/$type/$langCode", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val isVolume = response.request.url.pathSegments.contains("volume")

        val mangaList = response.parseAs<ResponseDto<String>>().result
            .toBodyFragment()
            .select(if (isVolume) ".vol-list > .item" else "li")

        val abbrPrefix = if (isVolume) "Vol" else "Chap"
        val fullPrefix = if (isVolume) "Volume" else "Chapter"

        return mangaList.map { m ->
            val link = m.selectFirst("a")!!

            val number = m.attr("data-number")
            val dateStr = m.select("span").getOrNull(1)?.text() ?: ""

            SChapter.create().apply {
                setUrlWithoutDomain(link.attr("href"))
                chapter_number = number.toFloatOrNull() ?: -1f
                name = run {
                    val name = m.selectFirst("span")!!.text()
                    val prefix = "$abbrPrefix $number: "
                    if (!name.startsWith(prefix)) return@run name
                    val realName = name.removePrefix(prefix)
                    if (realName.contains(number)) realName else "$fullPrefix $number: $realName"
                }
                date_upload = dateFormat.tryParse(dateStr)
            }
        }
    }

    // =============================== Pages ================================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val ajaxUrl = runBlocking { getVrfFromWebview(document) }

        return client.newCall(GET(ajaxUrl, headers)).execute()
            .parseAs<ResponseDto<PageListDto>>().result
            .pages.mapIndexed { index, image ->
                val url = image.url
                val offset = image.offset
                val imageUrl = if (offset > 0) "$url#${ImageInterceptor.SCRAMBLED}_$offset" else url

                Page(index, imageUrl = imageUrl)
            }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun getVrfFromWebview(document: Document): String = withContext(Dispatchers.Main.immediate) {
        withTimeoutOrNull(20.seconds) {
            suspendCancellableCoroutine { continuation ->
                val emptyWebViewResponse = runCatching {
                    WebResourceResponse("text/html", "utf-8", Buffer().inputStream())
                }.getOrElse {
                    continuation.resumeWithException(it)
                    return@suspendCancellableCoroutine
                }

                val context = Injekt.get<Application>()
                var webview: WebView? = WebView(context)

                fun cleanup() = runBlocking(Dispatchers.Main.immediate) {
                    webview?.stopLoading()
                    webview?.destroy()
                    webview = null
                }

                webview?.apply {
                    with(settings) {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        databaseEnabled = true
                        blockNetworkImage = true
                    }

                    webViewClient = object : WebViewClient() {
                        private val ajaxCalls = setOf("ajax/read/chapter", "ajax/read/volume")

                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest,
                        ): WebResourceResponse? {
                            val url = request.url

                            // allow script from their cdn
                            if (
                                url.host.orEmpty().contains("mfcdn.cc") &&
                                url.pathSegments.lastOrNull().orEmpty().contains("js")
                            ) {
                                Log.d(name, "allowed: $url")

                                runCatching { fetchWebResource(request) }
                                    .onSuccess { return it }
                                    .onFailure {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(it)
                                            cleanup()
                                        }
                                    }
                            }

                            // allow jquery script
                            if (
                                url.host.orEmpty().contains("cloudflare.com") &&
                                url.encodedPath.orEmpty().contains("jquery")
                            ) {
                                Log.d(name, "allowed: $url")

                                runCatching { fetchWebResource(request) }
                                    .onSuccess { return it }
                                    .onFailure {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(it)
                                            cleanup()
                                        }
                                    }
                            }

                            // allow ajax/read calls and intercept ajax/read/chapter or ajax/read/volume
                            if (
                                url.host == "mangafire.to" &&
                                url.encodedPath.orEmpty().contains("ajax/read")
                            ) {
                                if (ajaxCalls.any { url.encodedPath!!.contains(it) }) {
                                    Log.d(name, "found: $url")

                                    if (url.getQueryParameter("vrf") != null) {
                                        if (continuation.isActive) {
                                            continuation.resume(url.toString())
                                            cleanup()
                                        }
                                    } else {
                                        if (continuation.isActive) {
                                            continuation.resumeWithException(
                                                Exception("Unable to find vrf token"),
                                            )
                                            cleanup()
                                        }
                                    }
                                } else {
                                    // need to allow other call to ajax/read
                                    Log.d(name, "allowed: $url")
                                    runCatching { fetchWebResource(request) }
                                        .onSuccess { return it }
                                        .onFailure {
                                            if (continuation.isActive) {
                                                continuation.resumeWithException(it)
                                                cleanup()
                                            }
                                        }
                                }
                            }

                            Log.d(name, "denied: $url")
                            return emptyWebViewResponse
                        }
                    }

                    loadDataWithBaseURL(
                        document.location(),
                        document.outerHtml(),
                        "text/html",
                        "utf-8",
                        "",
                    )
                }

                continuation.invokeOnCancellation {
                    cleanup()
                }
            }
        } ?: throw Exception("Timeout getting vrf token")
    }

    private fun fetchWebResource(request: WebResourceRequest): WebResourceResponse = runBlocking(Dispatchers.IO) {
        val okhttpRequest = Request.Builder().apply {
            url(request.url.toString())
            headers(headers)

            val skipHeaders = setOf("referer", "user-agent", "sec-ch-ua", "sec-ch-ua-mobile", "sec-ch-ua-platform", "x-requested-with")
            for ((name, value) in request.requestHeaders) {
                if (skipHeaders.contains(name.lowercase())) continue
                addHeader(name, value)
            }
        }.build()

        client.newCall(okhttpRequest).await().use { response ->
            val mediaType = response.body.contentType()

            WebResourceResponse(
                mediaType?.let { "${it.type}/${it.subtype}" },
                mediaType?.charset()?.name(),
                Buffer().readFrom(
                    response.body.byteStream(),
                ).inputStream(),
            )
        }
    }

    @Serializable
    class PageListDto(private val images: List<List<JsonPrimitive>>) {
        val pages
            get() = images.map {
                Image(it[0].content, it[2].int)
            }
    }

    class Image(val url: String, val offset: Int)

    override fun imageUrlParse(response: Response): String {
        throw UnsupportedOperationException()
    }

    // ============================ Preferences =============================

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_VOLUME_PREF
            title = "Show volume entries in search result"
            setDefaultValue(false)
        }.let(screen::addPreference)
    }

    // ============================= Utilities ==============================

    @Serializable
    class ResponseDto<T>(
        val result: T,
    )

    private fun String.toBodyFragment(): Document {
        return Jsoup.parseBodyFragment(this, baseUrl)
    }

    companion object {
        private val dateFormat = SimpleDateFormat("MMM dd, yyyy", Locale.US)
        private const val SHOW_VOLUME_PREF = "show_volume"

        private const val VOLUME_URL_FRAGMENT = "vol"
        private const val VOLUME_URL_SUFFIX = "#$VOLUME_URL_FRAGMENT"
        private const val VOLUME_TITLE_PREFIX = "[VOL] "
    }
}
