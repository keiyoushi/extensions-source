package eu.kanade.tachiyomi.extension.es.ikigaimangas

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.model.SMangaUpdate
import keiyoushi.annotation.Source
import keiyoushi.network.addCookie
import keiyoushi.network.get
import keiyoushi.network.post
import keiyoushi.network.rateLimit
import keiyoushi.source.KeiSource
import keiyoushi.utils.asJsoup
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.runWebView
import keiyoushi.utils.tryParseZonedDateTime
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.nodes.Element
import java.util.Locale
import kotlin.time.Duration.Companion.seconds

@Source
abstract class IkigaiMangas :
    KeiSource(),
    ConfigurableSource {

    private val baseUrlHost by lazy { baseUrl.toHttpUrl().host }

    private var shouldFetchDomain = true
    private fun fetchDomainUrl() {
        if (!shouldFetchDomain) return
        shouldFetchDomain = false
        if (!preferences.fetchDomainPref()) {
            return
        }
        try {
            val initClient = network.client
            val initHeaders = Headers.Builder()
                .set("Referer", "$baseUrl/")
                .build()
            val response = initClient.newCall(
                okhttp3.Request.Builder().url("https://ikigaimangas.com").headers(initHeaders).build(),
            ).execute()
            val document = response.asJsoup()
            val scriptUrl = document.selectFirst("button[on:click]:containsOwn(Ir al sitio)")?.attr("on:click")
                ?: return
            val scriptResponse = initClient.newCall(
                okhttp3.Request.Builder().url("https://ikigaimangas.com/build/$scriptUrl").headers(initHeaders).build(),
            ).execute()
            val script = scriptResponse.body.string()
            val domain = script.substringAfter("i(\"").substringBefore("\"")
            val finalResponse = initClient.newCall(
                okhttp3.Request.Builder().url(domain).headers(initHeaders).build(),
            ).execute()
            val host = finalResponse.request.url.host
            val newDomain = "https://$host"
            preferences.edit().putString(BASE_URL_PREF, newDomain).apply()
        } catch (_: Exception) {
        }
    }

    private val imageCdnUrl: String = "https://image3.ikigaimangas.cloud"

    override fun OkHttpClient.Builder.configureClient() = apply {
        fetchDomainUrl()
        addCookie { listOf("is-adult-enabled" to preferences.showNsfwPref.toString()) }
        addInterceptor(::imageHeadersInterceptor)
        rateLimit(1, 2.seconds) { it.host == baseUrlHost }
    }

    private fun imageHeadersInterceptor(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.host.contains("ikigaimangas.cloud")) return chain.proceed(request)

        val newRequest = request.newBuilder()
            .header("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .header("Sec-Fetch-Dest", "image")
            .header("Sec-Fetch-Mode", "no-cors")
            .header("Sec-Fetch-Site", "cross-site")
            .build()
        return chain.proceed(newRequest)
    }

    private val preferences = getPreferences()

    private val dateFormat = java.time.format.DateTimeFormatter.ofPattern("EEE MMM dd yyyy HH:mm:ss 'GMT'X", Locale.ENGLISH)

    override suspend fun getPopularManga(page: Int): MangasPage {
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
        if (query.isNotEmpty()) {
            val series = getQuerySeriesList()
            return qwikDataParse(query, series, page)
        }

        val searchUrl = "$baseUrl/series/".toHttpUrl().newBuilder()
        searchUrl.addQueryParameter("tipos[]", "comic")

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

    private suspend fun getQuerySeriesList(): List<QwikSeriesDto> {
        fetchDomainUrl()
        val qfunc = getQfuncFromWebView(baseUrl) ?: throw Exception("Ocurrio un error al obtener la lista de series")
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("qfunc", qfunc)
            .build()
        val payload = """{"_entry":"1","_objs":["\u0002_#s_$qfunc",["0"]]}"""
        val body = payload.toRequestBody()
        val qrlHeaders = headersBuilder()
            .set("X-QRL", qfunc)
            .set("Content-Type", "application/qwik-json")
            .build()
        val response = client.post(url, qrlHeaders, body)
        return response.parseAs<QwikData>().parseAsList<QwikSeriesDto>()
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
        val detailsManga = if (fetchDetails) {
            val document = client.get("$baseUrl/series/${manga.url}/", headers).asJsoup()
            document.selectFirst("article.card")!!.let { element ->
                SManga.create().apply {
                    title = element.selectFirst(".card-body .card-title")!!.text()
                    thumbnail_url = element.selectFirst("img")?.attr("abs:src")
                    description = element.selectFirst(".card-body > p")?.text()
                    status = parseStatus(element.selectFirst("figure > ul a[href*=?estados]")?.text())
                    genre = element.select(".card-body > ul > li > a[href*=?generos]").joinToString { it.text() }
                }
            }
        } else {
            manga
        }

        val chapterList = if (fetchChapters) {
            val allChapters = mutableListOf<SChapter>()
            var page = 1
            do {
                val chapterUrl = "$baseUrl/series/${manga.url}/".toHttpUrl().newBuilder()
                    .addQueryParameter("pagina", page.toString())
                    .build()
                val document = client.get(chapterUrl, headers).asJsoup()
                val parsed = document.select("section.card > ul.grid a.card").map(::chapterFromElement)
                if (parsed.isEmpty()) break
                allChapters.addAll(parsed)
                page++
            } while (document.selectFirst("nav[aria-label=pagination] > a:last-child:not([class*=btn-disabled])") != null)
            allChapters
        } else {
            chapters
        }

        return SMangaUpdate(detailsManga, chapterList)
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
        date_upload = dateFormat.tryParseZonedDateTime(dateString)
    }

    override suspend fun getPageList(chapter: SChapter): List<Page> {
        var document = client.get(baseUrl + chapter.url, headers).asJsoup()

        document.selectFirst("button > span:contains(permitir nsfw)")?.let {
            preferences.showNsfwPref = true
            document = client.get(baseUrl + chapter.url, headers).asJsoup()
        }

        val images = document.select("img[alt^=Página][src*=/series/]")
        val chapterUrl = baseUrl + chapter.url
        return images.mapIndexed { i, element ->
            Page(i, url = chapterUrl, imageUrl = element.attr("abs:src"))
        }
    }

    override fun imageRequest(page: Page) = okhttp3.Request.Builder()
        .url(page.imageUrl!!)
        .headers(
            headersBuilder()
                .set("Referer", page.url.ifEmpty { "$baseUrl/" })
                .build(),
        )
        .build()

    override fun getFilterList(data: kotlinx.serialization.json.JsonElement?) = FilterList(
        eu.kanade.tachiyomi.source.model.Filter.Header("Nota: Los filtros son ignorados si se realiza una búsqueda por texto."),
        eu.kanade.tachiyomi.source.model.Filter.Separator(),
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

    private suspend fun getQfuncFromWebView(url: String): String? = runWebView<String?>(timeout = 20.seconds) {
        javaScriptEnabled = true
        domStorageEnabled = true
        blockImages = true

        jsBridge("qfuncBridge") { message ->
            resolve(message)
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
                                    window.qfuncBridge.post(qfunc);
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

    companion object {
        private const val SHOW_NSFW_PREF = "pref_show_nsfw"
        private const val BASE_URL_PREF = "overrideBaseUrl"
        private const val FETCH_DOMAIN_PREF = "fetchDomain"
        private const val PAGE_SIZE = 20
    }
}
