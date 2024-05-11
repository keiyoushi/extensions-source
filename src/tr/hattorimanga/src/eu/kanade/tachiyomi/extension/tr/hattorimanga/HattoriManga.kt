package eu.kanade.tachiyomi.extension.tr.hattorimanga

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.ParsedHttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale

class HattoriManga : ParsedHttpSource() {
    override val name: String = "Hattori Manga"

    override val baseUrl: String = "https://hattorimanga.com"

    override val lang: String = "tr"

    override val supportsLatest: Boolean = true

    override val versionId: Int = 2

    private val json: Json by injectLazy()

    private var csrfToken: String = ""

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
        .rateLimit(4)
        .addInterceptor { chain ->
            val request = chain.request()
            if (!request.url.toString().contains("manga/search")) {
                return@addInterceptor chain.proceed(request)
            }

            val req = request.newBuilder()
                .addHeader("X-Requested-With", "XMLHttpRequest")
                .build()

            if (csrfToken.isEmpty()) {
                getCsrftoken()
            }

            val query = request.url.fragment!!
            val response = chain.proceed(addFormBody(req, query))

            with(response) {
                return@addInterceptor when {
                    isPageExpired() -> {
                        close()
                        getCsrftoken()
                        chain.proceed(addFormBody(req, query))
                    }
                    else -> this
                }
            }
        }
        .build()

    private fun addFormBody(request: Request, query: String): Request {
        val body = FormBody.Builder()
            .add("_token", csrfToken)
            .add("query", query)
            .build()

        return request.newBuilder()
            .url(request.url.toString().substringBefore("#"))
            .post(body)
            .build()
    }

    private fun getCsrftoken() {
        val response = client.newCall(GET(baseUrl, headers)).execute()
        val document = response.asJsoup()
        csrfToken = document.selectFirst("meta[name=csrf-token]")!!.attr("content")
    }

    override fun chapterFromElement(element: Element) =
        throw UnsupportedOperationException()

    override fun chapterListSelector() =
        throw UnsupportedOperationException()

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val slug = manga.url.substringAfterLast('/')
        val chapters = mutableListOf<SChapter>()
        var page = 1

        do {
            val dto = fetchChapterPageableList(slug, page, manga)
            chapters += dto.chapters.map {
                SChapter.create().apply {
                    name = it.title
                    date_upload = it.date.toDate()
                    url = "${manga.url}/${it.chapterSlug}"
                }
            }
            page = dto.currentPage + 1
        } while (dto.hasNextPage())

        return Observable.just(chapters)
    }

    private fun fetchChapterPageableList(slug: String, page: Int, manga: SManga): HMChapterDto =
        client.newCall(GET("$baseUrl/load-more-chapters/$slug?page=$page", headers))
            .execute()
            .parseAs<HMChapterDto>()

    override fun imageUrlParse(document: Document) = ""

    override fun latestUpdatesFromElement(element: Element) =
        throw UnsupportedOperationException()

    override fun latestUpdatesNextPageSelector() =
        throw UnsupportedOperationException()

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-chapters")

    override fun latestUpdatesSelector() =
        throw UnsupportedOperationException()

    override fun fetchLatestUpdates(page: Int): Observable<MangasPage> {
        return client.newCall(latestUpdatesRequest(page)).asObservableSuccess()
            .map {
                val mangas = it.parseAs<HMLatestUpdateDto>().chapters.map {
                    SManga.create().apply {
                        val manga = it.manga
                        title = manga.title
                        thumbnail_url = "$baseUrl/storage/${manga.thumbnail}"
                        setUrlWithoutDomain("$baseUrl/manga/${manga.slug}")
                    }
                }.distinctBy { it.title }

                MangasPage(mangas, false)
            }
    }

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        title = document.selectFirst("h3")!!.text()
        thumbnail_url = document.selectFirst(".set-bg")?.absUrl("data-setbg")
        author = document.selectFirst(".anime-details-widget li span:contains(Yazar) + span")?.text()
        description = document.selectFirst(".anime-details-text p")?.text()
        setUrlWithoutDomain(document.location())
    }

    override fun pageListParse(document: Document): List<Page> {
        return document.select(".image-wrapper img").mapIndexed { index, element ->
            Page(index, imageUrl = "$baseUrl${element.attr("data-src")}")
        }.takeIf { it.isNotEmpty() } ?: throw Exception("Oturum açmanız, WebView'ı açmanız ve oturum açmanız gerekir")
    }

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h5")!!.text()
        thumbnail_url = element.selectFirst(".img-con")?.absUrl("data-setbg")
        genre = element.select(".product-card-con ul li").joinToString { it.text() }
        val script = element.attr("onclick")
        setUrlWithoutDomain(REGEX_MANGA_URL.find(script)!!.groups.get("url")!!.value)
    }

    override fun popularMangaNextPageSelector() = ".pagination .page-item:last-child:not(.disabled)"

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga?page=$page", headers)

    override fun popularMangaSelector() = ".product-card.grow-box"

    override fun searchMangaFromElement(element: Element) = popularMangaFromElement(element)

    override fun searchMangaNextPageSelector() = popularMangaNextPageSelector()

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val request = POST("$baseUrl/manga/search#$query", headers)
        if (query.isNotBlank()) {
            return request
        }

        val url = "$baseUrl/manga-index".toHttpUrl().newBuilder()
        val selection = filters.filterIsInstance<GenreList>()
            .flatMap { it.state }
            .filter { it.state }

        return when {
            selection.isNotEmpty() -> {
                selection.forEach { genre ->
                    url.addQueryParameter("genres[]", genre.id)
                }
                url.addQueryParameter("page", "$page")
                GET(url.build(), headers)
            }
            else -> request
        }
    }

    override fun searchMangaSelector() = popularMangaSelector()

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) { // URL intent handler
            val slug = query.removePrefix(PREFIX_SEARCH)
            return client.newCall(GET("$baseUrl/$slug"))
                .asObservableSuccess()
                .map {
                    MangasPage(listOf(mangaDetailsParse(it)), false)
                }
        }

        val request = searchMangaRequest(page, query, filters)

        if (request.url.toString().contains("manga-index")) {
            return super.fetchSearchManga(page, query, filters)
        }

        return client.newCall(request).asObservableSuccess().map { response ->
            val mangas = response.parseAs<List<SearchManga>>().map {
                SManga.create().apply {
                    title = it.title
                    description = it.description
                    author = it.author
                    artist = it.artist
                    thumbnail_url = "$baseUrl/storage/${it.thumbnail}"
                    setUrlWithoutDomain("$baseUrl/manga/${it.slug}")
                }
            }
            MangasPage(mangas, false)
        }
    }

    override fun getFilterList(): FilterList {
        CoroutineScope(Dispatchers.IO).launch { fetchGenres() }

        val filters = mutableListOf<Filter<*>>()

        if (genresList.isNotEmpty()) {
            filters += GenreList("Türler", genresList)
        } else {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Türleri göstermeyi denemek için 'Sıfırla' düğmesine basın"),
            )
        }

        return FilterList(filters)
    }

    private var genresList: List<Genre> = emptyList()

    private var fetchCategoriesAttempts: Int = 0

    private fun fetchGenres() {
        if (fetchCategoriesAttempts < 3 && genresList.isEmpty()) {
            try {
                genresList = client.newCall(genresRequest()).execute()
                    .use { parseCategories(it.asJsoup()) }
            } catch (_: Exception) {
            } finally {
                fetchCategoriesAttempts++
            }
        }
    }

    private fun parseCategories(document: Document): List<Genre> {
        return document.select(".tags-blog a")
            .map { element ->
                val tag = element.text()
                Genre(tag, tag)
            }
    }

    private fun genresRequest(): Request = GET("$baseUrl/manga", headers)

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromString(body.string())
    }

    private fun Response.isPageExpired() = code == 419

    private fun String.toDate(): Long =
        try { dateFormat.parse(trim())!!.time } catch (_: Exception) { 0L }

    class GenreList(title: String, genres: List<Genre>) : Filter.Group<GenreCheckBox>(title, genres.map { GenreCheckBox(it.name, it.id) })

    class GenreCheckBox(name: String, val id: String = name) : Filter.CheckBox(name)

    class Genre(val name: String, val id: String = name)

    companion object {
        val REGEX_MANGA_URL = """='(?<url>[^']+)""".toRegex()
        val PREFIX_SEARCH = "slug:"
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
    }
}
