package eu.kanade.tachiyomi.extension.es.ikigaimangas

import android.content.SharedPreferences
import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.annotation.Source
import keiyoushi.network.rateLimit
import keiyoushi.utils.applicationContext
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.time.Duration.Companion.seconds

@Source
abstract class IkigaiMangas :
    HttpSource(),
    ConfigurableSource {

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    private var shouldFetchDomain = true
    private fun fetchDomainUrl() {
        if (!shouldFetchDomain) return
        shouldFetchDomain = false
        if (!preferences.fetchDomainPref()) return
        try {
            val initClient = network.client
            val headers = super.headersBuilder().build()
            val document = initClient.newCall(GET("https://ikigaimangas.com", headers)).execute().asJsoup()
            val scriptUrl = document.selectFirst("button[on:click]:containsOwn(Ir al sitio)")?.attr("on:click")
                ?: return
            val script = initClient.newCall(GET("https://ikigaimangas.com/build/$scriptUrl", headers)).execute().body.string()
            val domain = script.substringAfter("i(\"").substringBefore("\"")
            val host = initClient.newCall(GET(domain, headers)).execute().request.url.host
            val newDomain = "https://$host"
            preferences.edit().putString(BASE_URL_PREF, newDomain).apply()
        } catch (_: Exception) {}
    }

    private val imageCdnUrl: String = "https://image2.ikigaimangas.cloud"

    override val supportsLatest: Boolean = true

    override val client by lazy {
        fetchDomainUrl()
        network.client.newBuilder()
            .addNetworkInterceptor(::nsfwCookieInterceptor)
            .rateLimit(1, 2.seconds) { it.host == baseUrlHost }
            .build()
    }

    private fun nsfwCookieInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        return request.header(ENABLE_NSFW_HEADER)?.let { value ->
            val newRequest = request.newBuilder()
                .removeHeader(ENABLE_NSFW_HEADER)
                .setCookie("is-adult-enabled", value)
                .build()
            chain.proceed(newRequest)
        } ?: chain.proceed(request)
    }

    private fun Request.Builder.setCookie(name: String, value: String): Request.Builder {
        val existingHeader = this.build().header("Cookie") ?: ""

        val cookies = existingHeader
            .split(";")
            .mapNotNull {
                val parts = it.trim().split("=", limit = 2)
                if (parts.size == 2) parts[0].trim() to parts[1].trim() else null
            }.toMap().toMutableMap()

        cookies[name] = value

        val mergedHeader = cookies.entries.joinToString("; ") { "${it.key}=${it.value}" }

        return this.header("Cookie", mergedHeader)
    }

    private val preferences = getPreferences()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    private val dateFormat = SimpleDateFormat("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.ENGLISH)

    override fun popularMangaRequest(page: Int): Request {
        val headers = headersBuilder()
            .enableNsfw(preferences.showNsfwPref)
            .build()

        return GET("$baseUrl/clasificacion/", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("div.grid > div.card").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                title = element.selectFirst(".card-body .card-title")!!.text()
                val seriesUrl = element.selectFirst(".card-actions > a.btn[href]")!!.attr("href")
                url = seriesUrl.substringAfterLast("/series/").substringBefore("/")
            }
        }
        return MangasPage(mangaList, false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val headers = headersBuilder()
            .enableNsfw(preferences.showNsfwPref)
            .build()

        return GET("$baseUrl/?pagina=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("section[aria-labelledby=new-chapters-heading] > ul.grid:last-of-type a.card").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                title = element.selectFirst(".card-body .card-title")!!.text()
                url = element.attr("href").substringAfterLast("/series/").substringBefore("/")
            }
        }
        val hasNextPage = document.selectFirst("nav[aria-label=pagination] > a:last-child:not([class*=btn-disabled])") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private var seriesCache: List<QwikSeriesDto>? = null

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        if (query.isNotEmpty()) {
            if (seriesCache != null) {
                return Observable.just(qwikDataParse(query, seriesCache!!, page))
            }
            val series = getQuerySeriesList()
            return Observable.just(qwikDataParse(query, series, page))
        }

        return client.newCall(searchMangaRequest(page, query, filters))
            .asObservableSuccess()
            .map { response -> searchMangaParse(response) }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/series/".toHttpUrl().newBuilder()

        url.addQueryParameter("tipos[]", "comic")

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.state.forEach { genre ->
                        if (genre.state) {
                            url.addQueryParameter("generos[]", genre.id.toString())
                        }
                    }
                }
                is StatusFilter -> {
                    filter.state.forEach { status ->
                        if (status.state) {
                            url.addQueryParameter("estados[]", status.id.toString())
                        }
                    }
                }
                is SortByFilter -> {
                    url.addQueryParameter("ordenar", filter.selected)
                    url.addQueryParameter("direccion", if (filter.state?.ascending == true) "asc" else "desc")
                }
                else -> {}
            }
        }

        url.addQueryParameter("pagina", page.toString())

        val headers = headersBuilder()
            .enableNsfw(preferences.showNsfwPref)
            .build()

        return GET(url.build(), headers)
    }

    private fun getQuerySeriesList(): List<QwikSeriesDto> {
        fetchDomainUrl()
        val qfunc = getQfuncFromWebView(baseUrl, headers) ?: throw Exception("Ocurrio un error al obtener la lista de series")
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("qfunc", qfunc)
            .build()
        val payload = """{"_entry":"1","_objs":["\u0002_#s_$qfunc",["0"]]}"""
        val body = payload.toRequestBody()
        val headers = headersBuilder()
            .set("X-QRL", qfunc)
            .set("Content-Type", "application/qwik-json")
            .build()
        val response = client.newCall(POST(url.toString(), headers, body)).execute()
        return response.parseAs<QwikData>().parseAsList<QwikSeriesDto>().also { seriesCache = it }
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangaList = document.select("section[aria-labelledby=archive-heading] > ul.grid a.card").map { element ->
            SManga.create().apply {
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                title = element.selectFirst("h3")!!.text()
                url = element.attr("href").substringAfterLast("/series/").substringBefore("/")
            }
        }
        val hasNextPage = document.selectFirst("nav[aria-label=pagination] > a:last-child:not([class*=btn-disabled])") != null
        return MangasPage(mangaList, hasNextPage)
    }

    private fun qwikDataParse(query: String, seriesList: List<QwikSeriesDto>, page: Int): MangasPage {
        val nsfwEnabled = preferences.showNsfwPref

        val filteredSeries = seriesList
            .filter { it.type == "comic" }
            .filter { nsfwEnabled || !it.isMature }
            .filter { it.name.contains(query, ignoreCase = true) }

        val pagedSeries = filteredSeries
            .drop((page - 1) * PAGE_SIZE)
            .take(PAGE_SIZE)
            .map { it.toSManga(imageCdnUrl) }

        return MangasPage(pagedSeries, filteredSeries.size > page * PAGE_SIZE)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/series/${manga.url}/"

    override fun mangaDetailsRequest(manga: SManga): Request = GET("$baseUrl/series/${manga.url}/", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return document.selectFirst("article.card")!!.let { element ->
            SManga.create().apply {
                title = element.selectFirst(".card-body .card-title")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                description = element.selectFirst(".card-body > p")?.text()
                status = parseStatus(element.selectFirst("figure > ul a[href*=?estados]")?.text())
                genre = element.select(".card-body > ul > li > a[href*=?generos]").joinToString { it.text().trim() }
            }
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "cancelada" -> SManga.CANCELLED
        "completa" -> SManga.COMPLETED
        "en curso" -> SManga.ONGOING
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> = Observable.fromCallable {
        val chapterList = mutableListOf<SChapter>()
        var page = 1
        do {
            val request = chapterListRequest(manga.url, page)
            val document = client.newCall(request).execute().asJsoup()
            val chapters = document.select("section.card > ul.grid a.card").map(::chapterFromElement)
            if (chapters.isEmpty()) break
            chapterList.addAll(chapters)
            page++
        } while (document.selectFirst("nav[aria-label=pagination] > a:last-child:not([class*=btn-disabled])") != null)
        return@fromCallable chapterList.toList()
    }

    private fun chapterListRequest(slug: String, page: Int): Request {
        val url = "$baseUrl/series/$slug/".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .build()
        return GET(url, headers)
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        name = element.selectFirst(".card-body .card-title")!!.text()
        val dateString = element.selectFirst("time")?.attr("datetime")?.substringBeforeLast("(")?.trim()
        date_upload = dateFormat.tryParse(dateString)
    }

    override fun pageListRequest(chapter: SChapter): Request = GET(baseUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val request = response.request
        var document = response.asJsoup()
        document.selectFirst("button > span:contains(permitir nsfw)")?.let {
            val newRequest = request.newBuilder()
                .enableNsfw(true)
                .build()
            document = client.newCall(newRequest).execute().asJsoup()
        }
        return document.select("section div.img > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()
    override fun chapterListRequest(manga: SManga) = throw UnsupportedOperationException()
    override fun chapterListParse(response: Response) = throw UnsupportedOperationException()

    override fun getFilterList() = FilterList(
        Filter.Header("Nota: Los filtros son ignorados si se realiza una búsqueda por texto."),
        Filter.Separator(),
        SortByFilter("Ordenar por", getSortProperties()),
        StatusFilter("Estados", getStatusFilters()),
        GenreFilter("Géneros", getGenreFilters()),
    )

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = SHOW_NSFW_PREF
            title = "Mostrar contenido NSFW"
            setDefaultValue(false)
        }.also { screen.addPreference(it) }

        SwitchPreferenceCompat(screen.context).apply {
            key = FETCH_DOMAIN_PREF
            title = "Buscar dominio automáticamente"
            summary = "Intenta buscar el dominio automáticamente al abrir la fuente."
            setDefaultValue(true)
        }.also { screen.addPreference(it) }
    }

    private fun SharedPreferences.fetchDomainPref() = getBoolean(FETCH_DOMAIN_PREF, true)

    private var SharedPreferences.showNsfwPref: Boolean
        get() = getBoolean(SHOW_NSFW_PREF, false)
        set(value) {
            edit().putBoolean(SHOW_NSFW_PREF, value).apply()
        }

    private fun getQfuncFromWebView(url: String, headers: Headers): String? {
        val latch = CountDownLatch(1)
        val handler = Handler(Looper.getMainLooper())
        val pool = ('a'..'z') + ('A'..'Z')
        val interfaceName = (1..(10..20).random())
            .map { pool.random() }
            .joinToString("")
        var result: String? = null
        var webView: WebView? = null
        handler.post {
            webView = WebView(applicationContext).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.blockNetworkImage = true
                settings.userAgentString = headers["User-Agent"]
                addJavascriptInterface(
                    object {
                        @Suppress("unused")
                        @JavascriptInterface
                        fun onQfunc(value: String) {
                            result = value
                            latch.countDown()
                        }
                    },
                    interfaceName,
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, loadedUrl: String) {
                        super.onPageFinished(view, loadedUrl)
                        injectFetchInterceptor(view, interfaceName)
                        clickTargetButton(view)
                    }
                }
                loadUrl(url)
            }
        }
        latch.await(20, TimeUnit.SECONDS)
        handler.post {
            webView?.destroy()
        }
        return result
    }

    private fun injectFetchInterceptor(
        webView: WebView,
        interfaceName: String,
    ) {
        val script = """
        (function () {
            const originalFetch = window.fetch;
            window.fetch = async function(resource, options) {
                let url = "";
                if (typeof resource === "string") {
                    url = resource;
                } else if (resource && resource.url) {
                    url = resource.url;
                }
                if (url.includes("qfunc")) {
                    const match = url.match(/[?&]qfunc=([^&]+)/);
                    if (match) {
                        const qfunc = decodeURIComponent(match[1]);
                        window.$interfaceName.onQfunc(qfunc);
                    }
                }
                return originalFetch.apply(this, arguments);
            };
        })();
        """.trimIndent()

        webView.evaluateJavascript(script, null)
    }

    private fun clickTargetButton(webView: WebView) {
        val script = """
        (function () {
            let tries = 0;
            const interval = setInterval(() => {
                const btn = [...document.querySelectorAll('button')]
                    .find(button =>
                        [...button.querySelectorAll('span')]
                            .some(span =>
                                span.textContent?.trim().includes('Buscar...')
                            )
                    );
                if (btn) {
                    clearInterval(interval);
                    btn.click();
                    return;
                }
                tries++;
                if (tries >= 20) {
                    clearInterval(interval);
                }
            }, 500);
        })();
        """.trimIndent()
        webView.evaluateJavascript(script, null)
    }

    private fun Headers.Builder.enableNsfw(flag: Boolean) = this.set(ENABLE_NSFW_HEADER, flag.toString())
    private fun Request.Builder.enableNsfw(flag: Boolean) = this.header(ENABLE_NSFW_HEADER, flag.toString())

    companion object {
        private const val SHOW_NSFW_PREF = "pref_show_nsfw"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val FETCH_DOMAIN_PREF = "fetchDomain"
        private const val PAGE_SIZE = 20
        private const val ENABLE_NSFW_HEADER = "X-Add-Nsfw-Cookie"
    }
}
