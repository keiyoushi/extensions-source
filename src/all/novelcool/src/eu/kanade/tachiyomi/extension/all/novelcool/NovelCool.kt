package eu.kanade.tachiyomi.extension.all.novelcool

import android.content.SharedPreferences
import androidx.preference.PreferenceScreen
import androidx.preference.SwitchPreferenceCompat
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.parseAs
import keiyoushi.utils.toJsonRequestBody
import keiyoushi.utils.tryParse
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.select.Elements
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

open class NovelCool(
    final override val baseUrl: String,
    final override val lang: String,
    private val siteLang: String = lang,
) : HttpSource(),
    ConfigurableSource {

    override val name = "NovelCool"

    override val supportsLatest = true

    private val apiUrl = "https://api.novelcool.com"

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1)
        .build()

    private val pageClient by lazy {
        client.newBuilder()
            .addInterceptor(::jsRedirect)
            .build()
    }

    private val preference by getPreferencesLazy()

    override fun fetchPopularManga(page: Int): Observable<MangasPage> = when (preference.useAppApi) {
        true -> client.newCall(commonApiRequest("$apiUrl/elite/hot/", page))
            .asObservableSuccess()
            .map(::commonApiResponseParse)

        else -> super.fetchPopularManga(page)
    }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/category/new_list.html", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        return parseMangasPage(response.asJsoup())
    }

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> = when (preference.useAppApi) {
        true -> client.newCall(commonApiRequest("$apiUrl/elite/latest/", page))
            .asObservableSuccess()
            .map(::commonApiResponseParse)

        else -> super.fetchLatestUpdates(page)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/category/latest.html", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        runCatching { fetchGenres() }
        return parseMangasPage(response.asJsoup())
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = when (preference.useAppApi) {
        true -> client.newCall(commonApiRequest("$apiUrl/book/search/", page, query))
            .asObservableSuccess()
            .map(::commonApiResponseParse)

        else -> super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/search".toHttpUrl().newBuilder().apply {
            addQueryParameter("name", query.trim())

            filters.forEach { filter ->
                when (filter) {
                    is AuthorFilter -> {
                        addQueryParameter("author", filter.state.trim())
                    }

                    is GenreFilter -> {
                        addQueryParameter("category_id", filter.included.joinToString(",", ","))
                        addQueryParameter("out_category_id", filter.excluded.joinToString(",", ","))
                    }

                    is StatusFilter -> {
                        addQueryParameter("completed_series", filter.getValue())
                    }

                    is RatingFilter -> {
                        addQueryParameter("rate_star", filter.getValue())
                    }

                    else -> { }
                }
            }

            addQueryParameter("page", page.toString())
        }.build()

        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        runCatching { fetchGenres(document) }
        return parseMangasPage(document)
    }

    private fun parseMangasPage(document: Document): MangasPage {
        val mangas = document.select(".book-list .book-item:not(:has(.book-type-novel))").map { element ->
            SManga.create().apply {
                title = element.select(".book-pic").attr("title")
                setUrlWithoutDomain(element.select("a").attr("href"))
                thumbnail_url = element.select("img").imgAttr()
            }
        }
        val hasNextPage = document.selectFirst("div.page-nav a div.next") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList(): FilterList {
        if (preference.useAppApi) {
            return FilterList(Filter.Header("Not supported when using App API"))
        }

        val filters: MutableList<Filter<*>> = mutableListOf(
            AuthorFilter("Author"),
            StatusFilter("Status", getStatusList()),
            RatingFilter("Rating", getRatingList()),
        )

        filters += if (genresList.isNotEmpty()) {
            listOf(
                GenreFilter("Genres", genresList),
            )
        } else {
            listOf(
                Filter.Separator(),
                Filter.Header("Press 'Reset' to attempt to show the genres"),
            )
        }

        return FilterList(filters)
    }

    private var fetchGenresAttempts = 0
    private var fetchGenresFailed = false
    private var genresList: List<Pair<String, String>> = emptyList()

    private fun fetchGenres(document: Document? = null) {
        if (fetchGenresAttempts < 3 && (genresList.isEmpty() || fetchGenresFailed) && !preference.useAppApi) {
            val genres = runCatching {
                if (document == null) {
                    client.newCall(genresRequest()).execute()
                        .use { parseGenres(it.asJsoup()) }
                } else {
                    parseGenres(document)
                }
            }

            fetchGenresFailed = genres.isFailure
            genresList = genres.getOrNull().orEmpty()
            fetchGenresAttempts++
        }
    }

    private fun genresRequest(): Request = GET("$baseUrl/search/", headers)

    private fun parseGenres(document: Document): List<Pair<String, String>> = document.selectFirst(".category-list")
        ?.select(".category-id-item")
        .orEmpty()
        .map { div ->
            Pair(
                div.attr("title"),
                div.attr("cate_id"),
            )
        }

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h1.bookinfo-title")!!.text()
            description = document.selectFirst("div.bk-summary-txt")?.text()
            genre = document.select(".bookinfo-category-list a").joinToString { it.text() }
            author = document.selectFirst(".bookinfo-author > a")?.attr("title")
            thumbnail_url = document.selectFirst(".bookinfo-pic-img")?.attr("abs:src")
            status = document.select(".bookinfo-category-list a").first()?.text().parseStatus()
        }
    }

    private fun String?.parseStatus(): Int {
        this ?: return SManga.UNKNOWN
        return when {
            this.lowercase() in completedStatusList -> SManga.COMPLETED
            this.lowercase() in ongoingStatusList -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select(".chapter-item-list a").map { element ->
            SChapter.create().apply {
                setUrlWithoutDomain(element.attr("href"))
                name = element.attr("title")
                date_upload = element.select(".chapter-item-time").text().parseDate()
            }
        }
    }

    private fun String.parseDate(): Long = DATE_FORMATTER.tryParse(this)

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> = pageClient.newCall(pageListRequest(chapter))
        .asObservableSuccess()
        .map(::pageListParse)

    override fun pageListRequest(chapter: SChapter): Request = super.pageListRequest(chapter).newBuilder()
        .addHeader("Referer", baseUrl)
        .build()

    override fun pageListParse(response: Response): List<Page> {
        var doc = response.asJsoup()
        val serverUrl = doc.selectFirst("section.section div.post-content-body > a")?.attr("href")

        if (serverUrl != null) {
            val serverHeaders = headers.newBuilder()
                .set("Referer", doc.baseUri())
                .build()
            doc = pageClient.newCall(GET(serverUrl, serverHeaders)).execute().asJsoup()
        }

        val script = doc.select("script:containsData(all_imgs_url)").html()

        val images = imgRegex.find(script)?.groupValues?.get(1)
            ?.let { "[$it]".parseAs<List<String>>() }
            ?: return singlePageParse(doc)

        return images.mapIndexed { idx, img ->
            Page(idx, imageUrl = img)
        }
    }

    private fun singlePageParse(document: Document): List<Page> = document.selectFirst(".mangaread-pagenav > .sl-page")?.select("option")
        ?.mapIndexed { idx, page ->
            Page(idx, url = page.attr("value"))
        } ?: emptyList()

    override fun imageUrlParse(response: Response): String {
        val document = response.asJsoup()
        return document.select(".mangaread-manga-pic").attr("src")
    }

    private fun Elements.imgAttr(): String = when {
        hasAttr("lazy_url") -> attr("abs:lazy_url")
        else -> attr("abs:src")
    }

    override fun setupPreferenceScreen(screen: PreferenceScreen) {
        SwitchPreferenceCompat(screen.context).apply {
            key = PREF_API_SEARCH
            title = "Use App API for browse"
            summary = "Results may be more reliable"
            setDefaultValue(true)
        }.also(screen::addPreference)
    }

    private val SharedPreferences.useAppApi: Boolean
        get() = getBoolean(PREF_API_SEARCH, true)

    private fun jsRedirect(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val headers = request.headers.newBuilder()
            .removeAll("Accept-Encoding")
            .build()
        val response = chain.proceed(request.newBuilder().headers(headers).build())

        val document = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())
        val jsRedirect = document.selectFirst("script:containsData(window.location.href)")?.html()
            ?.substringAfter("\"")
            ?.substringBefore("\"")
            ?: return response

        val requestUrl = response.request.url

        val url = "${requestUrl.scheme}://${requestUrl.host}$jsRedirect".toHttpUrlOrNull()
            ?: return response

        response.close()

        val newHeaders = headersBuilder()
            .add("Referer", requestUrl.toString())
            .build()

        return chain.proceed(
            request.newBuilder()
                .url(url)
                .headers(newHeaders)
                .build(),
        )
    }

    private fun commonApiRequest(url: String, page: Int, query: String? = null): Request {
        val payload = NovelCoolBrowsePayload(
            appId = APP_ID,
            lang = siteLang,
            query = query,
            type = "manga",
            page = page.toString(),
            size = SIZE.toString(),
            secret = APP_SECRET,
        )

        return POST(url, headers, payload.toJsonRequestBody())
    }

    private fun commonApiResponseParse(response: Response): MangasPage {
        runCatching { fetchGenres() }

        val browse = response.parseAs<NovelCoolBrowseResponse>()
        val mangas = browse.list?.map {
            it.toSManga().apply {
                setUrlWithoutDomain(it.url)
            }
        }.orEmpty()

        return MangasPage(mangas, browse.list?.size == SIZE)
    }

    companion object {
        private const val APP_ID = "202201290625004"
        private const val APP_SECRET = "c73a8590641781f203660afca1d37ada"
        private const val SIZE = 20

        private val DATE_FORMATTER = SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
        private val imgRegex = Regex("""all_imgs_url\s*:\s*\[\s*([^]]*)\s*,\s*]""")

        private const val PREF_API_SEARCH = "pref_use_search_api"

        // copied from Madara
        private val completedStatusList: Array<String> = arrayOf(
            "completed",
            "completo",
            "completado",
            "concluído",
            "concluido",
            "finalizado",
            "terminé",
            "hoàn thành",
        )

        private val ongoingStatusList: Array<String> = arrayOf(
            "ongoing", "Продолжается", "updating", "em lançamento", "em lançamento", "em andamento",
            "em andamento", "en cours", "ativo", "lançando", "Đang Tiến Hành", "devam ediyor",
            "devam ediyor", "in corso", "in arrivo", "en curso", "en curso", "emision",
            "curso", "en marcha", "Publicandose", "en emision",
        )
    }
}
