package eu.kanade.tachiyomi.extension.es.ikigaimangas

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.awaitSuccess
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.tryParseDate
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonElement
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class IkigaiMangas :
    KeiSource(),
    ConfigurableSource {

    override fun OkHttpClient.Builder.configureClient(): OkHttpClient.Builder = addNetworkInterceptor(::nsfwCookieInterceptor)
        .rateLimit(1, 2.seconds) { it.host == baseUrl.toHttpUrl().host }

    private val domainMutex = Mutex()
    private var domainFetched = false

    private suspend fun fetchDomainUrl() {
        if (domainFetched || !preferences.fetchDomainPref()) return
        domainMutex.withLock {
            if (domainFetched || !preferences.fetchDomainPref()) return

            try {
                val initClient = network.client
                val headers = super.headersBuilder().build()
                val document = initClient.get("https://ikigaimangas.com", headers).asJsoup()
                val scriptUrl = document.selectFirst("button[on:click]:containsOwn(Ir al sitio)")?.attr("on:click")
                    ?: return
                initClient.get("https://ikigaimangas.com/build/$scriptUrl", headers).use { response ->
                    val script = response.body.string()
                    val domain = script.substringAfter("i(\"").substringBefore("\"")
                    val host = initClient.newCall(GET(domain, headers)).execute().request.url.host
                    val newDomain = "https://$host"
                    preferences.edit().putString(BASE_URL_PREF, newDomain).apply()
                    domainFetched = true
                }
            } catch (_: Exception) {}
        }
    }

    private val imageCdnUrl: String = "https://image2.ikigaimangas.cloud"

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

    override fun Headers.Builder.configureHeaders() = apply {
        set("Sec-Fetch-Dest", "document")
        set("Sec-Fetch-Mode", "navigate")
        set("Sec-Fetch-Site", "cross-site")
    }

    private val dateFormat = DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'Z", Locale.ENGLISH)

    override suspend fun getPopularManga(page: Int): MangasPage {
        fetchDomainUrl()

        val headers = headersBuilder()
            .enableNsfw(preferences.showNsfwPref)
            .build()

        val document = client.get("$baseUrl/clasificacion/", headers).asJsoup()

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

    override suspend fun getLatestUpdates(page: Int): MangasPage {
        fetchDomainUrl()

        val headers = headersBuilder()
            .enableNsfw(preferences.showNsfwPref)
            .build()

        val document = client.get("$baseUrl/?pagina=$page", headers).asJsoup()

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

    override suspend fun getSearchMangaList(page: Int, query: String, filters: FilterList): MangasPage {
        fetchDomainUrl()

        if (query.isNotEmpty()) {
            return qwikDataParse(query, getQuerySeriesList(), page)
        }

        val headers = headersBuilder()
            .enableNsfw(preferences.showNsfwPref)
            .build()

        val searchUrl = "$baseUrl/series/".toHttpUrl().newBuilder()
            .addQueryParameter("tipos[]", "comic")

        filters.forEach { filter ->
            when (filter) {
                is GenreFilter -> {
                    filter.state.forEach { genre ->
                        if (genre.state) {
                            searchUrl.addQueryParameter("generos[]", genre.id.toString())
                        }
                    }
                }
                is StatusFilter -> {
                    filter.state.forEach { status ->
                        if (status.state) {
                            searchUrl.addQueryParameter("estados[]", status.id.toString())
                        }
                    }
                }
                is SortByFilter -> {
                    searchUrl.addQueryParameter("ordenar", filter.selected)
                    searchUrl.addQueryParameter("direccion", if (filter.state?.ascending == true) "asc" else "desc")
                }
                else -> {}
            }
        }

        searchUrl.addQueryParameter("pagina", page.toString())

        val document = client.get(searchUrl.build(), headers).asJsoup()

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

    private var seriesCache: Deferred<List<QwikSeriesDto>>? = null

    private suspend fun getQuerySeriesList(): List<QwikSeriesDto> {
        val deferred = seriesCache ?: coroutineScope {
            async {
                val qfunc = getQfuncFromWebView(baseUrl, headers)
                val url = baseUrl.toHttpUrl().newBuilder()
                    .addQueryParameter("qfunc", qfunc)
                    .build()
                val payload = """{"_entry":"1","_objs":["\u0002_#s_$qfunc",["0"]]}"""
                val body = payload.toRequestBody()
                val headers = headersBuilder()
                    .set("X-QRL", qfunc)
                    .set("Content-Type", "application/qwik-json")
                    .build()
                client.post(url, headers, body).use { response ->
                    response.parseAs<QwikData>().parseAsList<QwikSeriesDto>()
                }
            }.also { seriesCache = it }
        }
        return deferred.await()
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
    override fun getChapterUrl(chapter: SChapter) = baseUrl + chapter.url

    override suspend fun fetchMangaUpdate(
        manga: SManga,
        chapters: List<SChapter>,
        fetchDetails: Boolean,
        fetchChapters: Boolean,
    ): SMangaUpdate {
        fetchDomainUrl()

        val document = client.get("$baseUrl/series/${manga.url}/").asJsoup()
        val mainContent = document.selectFirst("main")!!
        val updatedManga = SManga.create().apply {
            title = mainContent.selectFirst(".card-body .card-title")!!.text()
            thumbnail_url = mainContent.selectFirst("img")?.attr("abs:src")
            description = mainContent.selectFirst(".card-body > p")?.text()
            status = parseStatus(mainContent.selectFirst("figure > ul a[href*=?estados]")?.text())
            genre = mainContent.select(".card-body > ul > li > a[href*=?generos]").joinToString { it.text().trim() }
        }

        if (!fetchChapters) {
            return SMangaUpdate(
                manga = updatedManga,
                chapters = chapters,
            )
        }

        val firstPageChapters = document
            .select("section.card > ul.grid a.card")
            .map(::chapterFromElement)

        val lastPage = document.select("nav[aria-label=pagination] > a[q:key^=page-]").lastOrNull()
            ?.attr("q:key")?.let {
                it.substringAfter("-").toIntOrNull()
            } ?: 1

        val remainingChapters = coroutineScope {
            (2..lastPage).map { page ->
                async {
                    val url = "$baseUrl/series/${manga.url}/"
                        .toHttpUrl()
                        .newBuilder()
                        .addQueryParameter("pagina", page.toString())
                        .build()

                    client.get(url).asJsoup()
                        .select("section.card > ul.grid a.card")
                        .map(::chapterFromElement)
                }
            }.awaitAll()
        }.flatten()

        val chapterList = firstPageChapters + remainingChapters

        return SMangaUpdate(
            manga = updatedManga,
            chapters = chapterList,
        )
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "cancelada" -> SManga.CANCELLED
        "completa" -> SManga.COMPLETED
        "en curso" -> SManga.ONGOING
        "hiatus" -> SManga.ON_HIATUS
        else -> SManga.UNKNOWN
    }

    private fun chapterFromElement(element: Element): SChapter = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("abs:href"))
        name = element.selectFirst(".card-body .card-title")!!.text()
        val dateString = element.selectFirst("time")?.attr("datetime")?.substringBeforeLast("(")?.trim()
        date_upload = dateFormat.tryParseDate(dateString)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        fetchDomainUrl()

        val headers = headersBuilder()
            .enableNsfw(preferences.showNsfwPref)
            .build()

        var document = client.get(baseUrl + chapter.url, headers).asJsoup()
        document.selectFirst("button > span:contains(permitir nsfw)")?.let {
            val newRequest = GET(baseUrl + chapter.url, headers)
                .newBuilder()
                .enableNsfw(true)
                .build()
            document = client.newCall(newRequest).awaitSuccess().asJsoup()
        }
        return document.select("section div > img").mapIndexed { i, element ->
            Page(i, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageRequest(page: Page): Request = super.imageRequest(page).newBuilder()
        .header("Sec-Fetch-Dest", "image")
        .header("Sec-Fetch-Mode", "no-cors")
        .header("Sec-Fetch-Site", "cross-site")
        .build()

    override fun getFilterList(data: JsonElement?) = FilterList(
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

    private suspend fun getQfuncFromWebView(
        url: String,
        headers: Headers,
    ): String = runWebView(timeout = 20.seconds) {
        val pool = ('a'..'z') + ('A'..'Z')
        val interfaceName = (1..(10..20).random())
            .map { pool.random() }
            .joinToString("")

        javaScriptEnabled = true
        domStorageEnabled = true
        blockImages = true
        userAgent = headers["User-Agent"].orEmpty()

        jsBridge(interfaceName) { qfunc ->
            resolve(qfunc)
        }

        onPageFinished {
            evaluateJs(
                """
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
                                    window.$interfaceName.post(qfunc);
                                }
                            }
                            return originalFetch.apply(this, arguments);
                        };
                    })();
                """.trimIndent(),
            )

            evaluateJs(
                """
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
                """.trimIndent(),
            )
        }

        loadUrl(url)
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
