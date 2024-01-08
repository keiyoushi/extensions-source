package eu.kanade.tachiyomi.extension.all.novelcool

import android.app.Application
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
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.select.Elements
import rx.Observable
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

open class NovelCool(
    final override val baseUrl: String,
    final override val lang: String,
    private val siteLang: String = lang,
) : ParsedHttpSource(), ConfigurableSource {

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

    private val json: Json by injectLazy()

    private val preference by lazy {
        Injekt.get<Application>().getSharedPreferences("source_$id", 0x0000)
    }

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        return when (preference.useAppApi) {
            true -> client.newCall(commonApiRequest("$apiUrl/elite/hot/", page))
                .asObservableSuccess()
                .map(::commonApiResponseParse)
            else -> super.fetchPopularManga(page)
        }
    }

    override fun popularMangaRequest(page: Int): Request {
        // popular on the site only have novels
        return GET("$baseUrl/category/new_list.html", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        runCatching { fetchGenres() }

        return super.popularMangaParse(response)
    }

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return when (preference.useAppApi) {
            true -> client.newCall(commonApiRequest("$apiUrl/elite/latest/", page))
                .asObservableSuccess()
                .map(::commonApiResponseParse)
            else -> super.fetchLatestUpdates(page)
        }
    }

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/category/latest.html", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        runCatching { fetchGenres() }

        return super.latestUpdatesParse(response)
    }

    override fun latestUpdatesFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesSelector() = searchMangaSelector()

    override fun latestUpdatesNextPageSelector() = searchMangaNextPageSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        return when (preference.useAppApi) {
            true -> client.newCall(commonApiRequest("$apiUrl/book/search/", page, query))
                .asObservableSuccess()
                .map(::commonApiResponseParse)
            else -> super.fetchSearchManga(page, query, filters)
        }
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
        val document = Jsoup.parse(response.peekBody(Long.MAX_VALUE).string())
        runCatching { fetchGenres(document) }

        return super.searchMangaParse(response)
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.select(".book-pic").attr("title")
        setUrlWithoutDomain(element.select("a").attr("href"))
        thumbnail_url = element.select("img").imgAttr()
    }

    override fun searchMangaSelector() = ".book-list .book-item:not(:has(.book-type-novel))"

    override fun searchMangaNextPageSelector() = "div.page-nav a div.next"

    private class AuthorFilter(title: String) : Filter.Text(title)

    private class GenreFilter(title: String, genres: List<Pair<String, String>>) :
        Filter.Group<Genre>(title, genres.map { Genre(it.first, it.second) }) {
        val included: List<String>
            get() = state.filter { it.isIncluded() }.map { it.id }

        val excluded: List<String>
            get() = state.filter { it.isExcluded() }.map { it.id }
    }
    class Genre(name: String, val id: String) : Filter.TriState(name)

    private fun getStatusList() = listOf(
        Pair("All", ""),
        Pair("Completed", "YES"),
        Pair("Ongoing", "NO"),
    )

    private class StatusFilter(title: String, private val status: List<Pair<String, String>>) :
        Filter.Select<String>(title, status.map { it.first }.toTypedArray()) {
        fun getValue() = status[state].second
    }

    private fun getRatingList() = listOf(
        Pair("All", ""),
        Pair("5 Star", "5"),
        Pair("4 Star", "4"),
        Pair("3 Star", "3"),
        Pair("2 Star", "2"),
    )
    private class RatingFilter(title: String, private val ratings: List<Pair<String, String>>) :
        Filter.Select<String>(title, ratings.map { it.first }.toTypedArray()) {
        fun getValue() = ratings[state].second
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

    private fun genresRequest(): Request {
        return GET("$baseUrl/search/", headers)
    }

    private fun parseGenres(document: Document): List<Pair<String, String>> {
        return document.selectFirst(".category-list")
            ?.select(".category-id-item")
            .orEmpty()
            .map { div ->
                Pair(
                    div.attr("title"),
                    div.attr("cate_id"),
                )
            }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h1.bookinfo-title")!!.text()
        description = document.selectFirst("div.bk-summary-txt")?.text()
        genre = document.select(".bookinfo-category-list a").joinToString { it.text() }
        author = document.selectFirst(".bookinfo-author > a")?.attr("title")
        thumbnail_url = document.selectFirst(".bookinfo-pic-img")?.attr("abs:src")
        status = document.select(".bookinfo-category-list a").first()?.text().parseStatus()
    }

    private fun String?.parseStatus(): Int {
        this ?: return SManga.UNKNOWN
        return when {
            this.lowercase() in completedStatusList -> SManga.COMPLETED
            this.lowercase() in ongoingStatusList -> SManga.ONGOING
            else -> SManga.UNKNOWN
        }
    }

    override fun chapterListSelector() = ".chapter-item-list a"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.attr("href"))
        name = element.attr("title")
        date_upload = element.select(".chapter-item-time").text().parseDate()
    }

    private fun String.parseDate(): Long {
        return runCatching { DATE_FORMATTER.parse(this)?.time }
            .getOrNull() ?: 0L
    }

    override fun fetchPageList(chapter: SChapter): Observable<List<Page>> {
        return pageClient.newCall(pageListRequest(chapter))
            .asObservableSuccess()
            .map(::pageListParse)
    }

    override fun pageListRequest(chapter: SChapter): Request {
        return super.pageListRequest(chapter).newBuilder()
            .addHeader("Referer", baseUrl)
            .build()
    }

    override fun pageListParse(document: Document): List<Page> {
        var doc = document
        val serverUrl = doc.selectFirst("section.section div.post-content-body > a")?.attr("href")

        if (serverUrl != null) {
            val serverHeaders = headers.newBuilder()
                .set("Referer", doc.baseUri())
                .build()
            doc = pageClient.newCall(GET(serverUrl, serverHeaders)).execute().asJsoup()
        }

        val script = doc.select("script:containsData(all_imgs_url)").html()

        val images = imgRegex.find(script)?.groupValues?.get(1)
            ?.let { json.decodeFromString<List<String>>("[$it]") }
            ?: return singlePageParse(doc)

        return images.mapIndexed { idx, img ->
            Page(idx, "", img)
        }
    }

    private fun singlePageParse(document: Document): List<Page> {
        return document.selectFirst(".mangaread-pagenav > .sl-page")?.select("option")
            ?.mapIndexed { idx, page ->
                Page(idx, page.attr("value"))
            } ?: emptyList()
    }

    override fun imageUrlParse(document: Document): String {
        return document.select(".mangaread-manga-pic").attr("src")
    }

    private fun Elements.imgAttr(): String {
        return when {
            hasAttr("lazy_url") -> attr("abs:lazy_url")
            else -> attr("abs:src")
        }
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
            appId = appId,
            lang = siteLang,
            query = query,
            type = "manga",
            page = page.toString(),
            size = size.toString(),
            secret = appSecret,
        )

        val body = json.encodeToString(payload)
            .toRequestBody(JSON_MEDIA_TYPE)

        val apiHeaders = headersBuilder()
            .add("Content-Length", body.contentLength().toString())
            .add("Content-Type", body.contentType().toString())
            .build()

        return POST(url, apiHeaders, body)
    }

    private fun commonApiResponseParse(response: Response): MangasPage {
        runCatching { fetchGenres() }

        val browse = json.decodeFromString<NovelCoolBrowseResponse>(response.body.string())

        val hasNextPage = browse.list?.size == size

        return browse.list?.map {
            SManga.create().apply {
                setUrlWithoutDomain(it.url)
                title = it.name
                thumbnail_url = it.cover
            }
        }.let { MangasPage(it ?: emptyList(), hasNextPage) }
    }

    companion object {
        private const val appId = "202201290625004"
        private const val appSecret = "c73a8590641781f203660afca1d37ada"
        private const val size = 20
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaTypeOrNull()
        private val DATE_FORMATTER by lazy {
            SimpleDateFormat("MMM dd, yyyy", Locale.ENGLISH)
        }
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
