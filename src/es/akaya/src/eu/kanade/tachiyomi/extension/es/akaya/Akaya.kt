package eu.kanade.tachiyomi.extension.es.akaya

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.io.IOException
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class Akaya : ParsedHttpSource() {

    override val name: String = "AKAYA"

    override val baseUrl: String = "https://akaya.io"

    override val lang: String = "es"

    override val supportsLatest: Boolean = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 1)
        .addInterceptor { chain ->
            val request = chain.request()
            if (!request.url.toString().startsWith("$baseUrl/serie")) return@addInterceptor chain.proceed(request)
            val response = chain.proceed(request)
            if (response.request.url.toString().removeSuffix("/") == baseUrl) {
                throw IOException("Esta serie no se encuentra disponible")
            }
            return@addInterceptor response
        }
        .addInterceptor { chain ->
            val request = chain.request()
            if (!request.url.toString().startsWith("$baseUrl/search")) return@addInterceptor chain.proceed(request)
            val query = request.url.fragment!!
            if (csrfToken.isEmpty()) getCsrftoken()
            val response = chain.proceed(addFormBody(request, query))
            if (response.code == 419) {
                response.close()
                getCsrftoken()
                return@addInterceptor chain.proceed(addFormBody(request, query))
            }
            return@addInterceptor response
        }
        .build()

    private fun addFormBody(request: Request, query: String): Request {
        val body = FormBody.Builder()
            .add("_token", csrfToken)
            .add("search", query)
            .build()

        return request.newBuilder()
            .url(request.url.toString().substringBefore("#"))
            .post(body)
            .build()
    }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request =
        GET("$baseUrl/collection/bd90cb43-9bf2-4759-b8cc-c9e66a526bc6?page=$page", headers)

    override fun popularMangaSelector() = searchMangaSelector()

    override fun popularMangaNextPageSelector() = searchMangaNextPageSelector()

    override fun popularMangaFromElement(element: Element) = searchMangaFromElement(element)

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$baseUrl/collection/0031a504-706c-4666-9782-a4ae30cad973?page=$page", headers)

    override fun latestUpdatesSelector() = popularMangaSelector()

    override fun latestUpdatesNextPageSelector() = popularMangaNextPageSelector()

    override fun latestUpdatesFromElement(element: Element) = popularMangaFromElement(element)

    private var csrfToken: String = ""

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.isEmpty()) return super.fetchSearchManga(page, query, filters)
        return client.newCall(querySearchMangaRequest(query)).asObservableSuccess().map { response ->
            querySearchMangaParse(response)
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()

        val order = filters.filterIsInstance<OrderFilter>().first().toUriPart()
        val genres = filters.filterIsInstance<GenreFilter>().first().state
            .filter(Genre::state)
            .map(Genre::id)

        url.addPathSegment(order)
        if (genres.isNotEmpty()) url.addPathSegment(genres.joinToString(",", "[", "]"))

        url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    private fun getCsrftoken() {
        val response = client.newCall(GET(baseUrl, headers)).execute()
        val document = response.asJsoup()
        csrfToken = document.selectFirst("meta[name=csrf-token]")!!.attr("content")
    }

    private fun querySearchMangaRequest(query: String): Request = POST("$baseUrl/search#$query", headers)

    private fun querySearchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("main > div.search-title > div.rowDiv div.list-search:has(div.inner-img-search)").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("div.name-serie-search > a")!!.attr("href"))
                thumbnail_url = it.selectFirst("div.inner-img-search")!!.attr("style")
                    .substringAfter("url(").substringBefore(")")
                title = it.select("div.name-serie-search").text()
            }
        }

        return MangasPage(mangas, false)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Los filtros se ignorarán al hacer una búsqueda por texto"),
        Filter.Separator(),
        OrderFilter(),
        GenreFilter(),
    )

    override fun searchMangaSelector() = "div.serie_items > div.library-grid-item"

    override fun searchMangaNextPageSelector() = "div.wrapper-navigation ul.pagination > li > a[rel=next]"

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
        title = element.selectFirst("span > h5 > strong")!!.text()
        thumbnail_url = element.selectFirst("div.inner-img")?.attr("style")
            ?.substringAfter("url(")?.substringBefore(")")
            ?: element.selectFirst("div.img-fluid")?.attr("abs:src")
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        with(document.selectFirst("header.masthead > div.container > div.row")!!) {
            title = selectFirst(".serie-head-title")!!.text()
            author = selectFirst("ul.persons")!!.let { element ->
                element.select("li").joinToString { it.text() }
                    .ifEmpty { element.text() }
            }
            genre = selectFirst("ul.categories")!!.let { element ->
                element.select("li").joinToString { it.text() }
                    .ifEmpty { element.text() }
            }
        }
        thumbnail_url = document.selectFirst("meta[property=og:image]")!!.attr("content")
            .replace("/chapters/", "/content/")
        description = document.selectFirst("section.main div.container div.sidebar > p")!!.text()
    }

    override fun chapterListRequest(manga: SManga): Request =
        GET(baseUrl + manga.url + "?order_direction=desc", headers)

    override fun chapterListSelector() = "div.chapter-desktop div.chapter-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        setUrlWithoutDomain(element.selectFirst("div.text-left > .mt-1 > a")!!.attr("href"))
        name = element.selectFirst("div.text-left > .mt-1 > a")!!.text()
        date_upload = parseDate(element.selectFirst("p.date")!!.text())

        element.selectFirst("i.ak-lock")?.let {
            name = "🔒 $name"
            url = "$url#lock"
        }
    }

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("es"))

    private fun parseDate(date: String): Long {
        return try {
            dateFormat.parse(date)?.time ?: 0L
        } catch (e: ParseException) {
            0L
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.substringAfterLast("#") == "lock") {
            throw Exception("Capítulo bloqueado")
        }

        return super.pageListRequest(chapter)
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select("main.separatorReading div.container img.img-fluid").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(document: Document) = throw UnsupportedOperationException()
}
