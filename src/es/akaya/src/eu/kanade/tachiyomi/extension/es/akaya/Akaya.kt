package eu.kanade.tachiyomi.extension.es.akaya

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.firstInstanceOrNull
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale

class Akaya : HttpSource() {

    override val name = "AKAYA"

    override val baseUrl = "https://akaya.io"

    override val lang = "es"

    override val supportsLatest = true

    @Volatile
    private var csrfToken: String = ""

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 1)
        .addInterceptor { chain ->
            val request = chain.request()
            if (!request.url.toString().startsWith("$baseUrl/serie")) return@addInterceptor chain.proceed(request)
            val response = chain.proceed(request)
            if (response.request.url.toString().removeSuffix("/") == baseUrl) {
                response.close()
                throw IOException("Esta serie no se encuentra disponible")
            }
            response
        }
        .addInterceptor { chain ->
            val request = chain.request()
            if (!request.url.toString().startsWith("$baseUrl/search")) return@addInterceptor chain.proceed(request)
            val query = request.url.fragment ?: return@addInterceptor chain.proceed(request)
            if (csrfToken.isEmpty()) getCsrftoken()
            var response = chain.proceed(addFormBody(request, query))
            if (response.code == 419) {
                response.close()
                getCsrftoken()
                response = chain.proceed(addFormBody(request, query))
            }
            response
        }
        .build()

    private fun getCsrftoken() {
        val response = client.newCall(GET(baseUrl, headers)).execute()
        csrfToken = response.asJsoup().selectFirst("meta[name=csrf-token]")?.attr("content") ?: ""
    }

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

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/collection/bd90cb43-9bf2-4759-b8cc-c9e66a526bc6?page=$page", headers)

    override fun popularMangaParse(response: Response) = parseMangaList(response)

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/collection/0031a504-706c-4666-9782-a4ae30cad973?page=$page", headers)

    override fun latestUpdatesParse(response: Response) = parseMangaList(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            return POST("$baseUrl/search#$query", headers)
        }

        val url = baseUrl.toHttpUrl().newBuilder()
        val order = filters.firstInstanceOrNull<OrderFilter>()?.toUriPart() ?: "genres"
        val genres = filters.firstInstanceOrNull<GenreFilter>()?.state
            ?.filter { it.state }
            ?.map { it.id }
            ?: emptyList()

        url.addPathSegment(order)
        if (genres.isNotEmpty()) {
            url.addPathSegment(genres.joinToString(",", "[", "]"))
        }

        url.addQueryParameter("page", page.toString())

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        if (!response.request.url.toString().contains("/search")) {
            return parseMangaList(response)
        }

        val document = response.asJsoup()
        val mangas = document.select("main > div.search-title > div.rowDiv div.list-search:has(div.inner-img-search)").map {
            SManga.create().apply {
                setUrlWithoutDomain(it.selectFirst("div.name-serie-search > a")!!.attr("href"))
                thumbnail_url = it.selectFirst("div.inner-img-search")?.attr("style")
                    ?.substringAfter("url(")?.substringBefore(")")
                title = it.select("div.name-serie-search")?.text() ?: ""
            }
        }

        return MangasPage(mangas, false)
    }

    private fun parseMangaList(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.serie_items > div.library-grid-item").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.selectFirst("a")!!.attr("href"))
                title = element.selectFirst("span > h5 > strong")?.text() ?: ""
                thumbnail_url = element.selectFirst("div.inner-img")?.attr("style")
                    ?.substringAfter("url(")?.substringBefore(")")
                    ?: element.selectFirst("div.img-fluid")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("div.wrapper-navigation ul.pagination > li > a[rel=next]") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Los filtros se ignorarán al hacer una búsqueda por texto"),
        Filter.Separator(),
        OrderFilter(),
        GenreFilter(),
    )

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            document.selectFirst("header.masthead > div.container > div.row")?.let { header ->
                title = header.selectFirst(".serie-head-title")?.text() ?: ""
                author = header.selectFirst("ul.persons")?.let { element ->
                    element.select("li").joinToString { it.text() }.ifEmpty { element.text() }
                }
                genre = header.selectFirst("ul.categories")?.let { element ->
                    element.select("li").joinToString { it.text() }.ifEmpty { element.text() }
                }
            }
            thumbnail_url = document.selectFirst("meta[property=og:image]")?.attr("content")
                ?.replace("/chapters/", "/content/")
            description = document.selectFirst("section.main div.container div.sidebar > p")?.text()
        }
    }

    override fun chapterListRequest(manga: SManga): Request = GET(baseUrl + manga.url + "?order_direction=desc", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        return document.select("div.chapter-desktop div.chapter-item").map { element ->
            SChapter.create().apply {
                val link = element.selectFirst("div.text-left > .mt-1 > a")!!
                setUrlWithoutDomain(link.attr("href"))
                name = link.text()
                date_upload = dateFormat.tryParse(element.selectFirst("p.date")?.text())

                if (element.selectFirst("i.ak-lock") != null) {
                    name = "🔒 $name"
                    url = "$url#lock"
                }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        if (chapter.url.substringAfterLast("#") == "lock") {
            throw Exception("Capítulo bloqueado")
        }
        return super.pageListRequest(chapter)
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        val scriptContent = document.selectFirst("script:containsData(var chapterData =)")?.data()

        if (scriptContent != null) {
            try {
                val jsonString = scriptContent
                    .substringAfter("var chapterData =")
                    .substringBefore("\n")
                    .trim()
                    .removeSuffix(";")

                val chapterData = jsonString.parseAs<ChapterDataDto>()
                if (chapterData.sortedImages.isNotEmpty()) {
                    return chapterData.sortedImages.mapIndexed { i, img ->
                        Page(i, imageUrl = "https://api.akayamedia.com/chapters/${img.image}")
                    }
                }
            } catch (e: Exception) {
                // Fallback to DOM parsing below
            }
        }

        return document.select("main div.container img.chapter-img, main.separatorReading div.container img.img-fluid").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale("es"))
    }
}
