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
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
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

class HattoriManga : HttpSource() {
    override val name: String = "Hattori Manga"

    override val baseUrl: String = "https://hattorimanga.net"

    override val lang: String = "tr"

    override val supportsLatest: Boolean = true

    override val versionId: Int = 2

    private val json: Json by injectLazy()

    private var csrfToken: String = ""

    private var genresList: List<Genre> = emptyList()

    override val client: OkHttpClient = network.cloudflareClient.newBuilder()
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
        .rateLimit(4)
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

    override fun chapterListParse(response: Response) =
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
            page = dto.nextPage()
        } while (dto.hasNextPage())

        return Observable.just(chapters)
    }

    private fun fetchChapterPageableList(slug: String, page: Int, manga: SManga): HMChapterDto =
        client.newCall(GET("$baseUrl/load-more-chapters/$slug?page=$page", headers))
            .execute()
            .parseAs<HMChapterDto>()

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/latest-chapters")

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        return SManga.create().apply {
            title = document.selectFirst("h3")!!.text()
            thumbnail_url = document.selectFirst(".set-bg")?.absUrl("data-setbg")
            description = document.selectFirst(".anime-details-text p")?.text()
            author = document.selectFirst(".anime-details-widget li:has(span:contains(Yazar))")?.ownText()
            artist = document.selectFirst(".anime-details-widget li:has(span:contains(Çizer))")?.ownText()
            genre = document.selectFirst(".anime-details-widget li:has(span:contains(Etiketler))")
                ?.ownText()
                ?.split(",")
                ?.map { it.trim() }
                ?.joinToString()
            setUrlWithoutDomain(document.location())
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.asJsoup().select(".image-wrapper img").mapIndexed { index, element ->
            Page(index, imageUrl = "$baseUrl${element.attr("data-src")}")
        }.takeIf { it.isNotEmpty() } ?: throw Exception("Oturum açmanız, WebView'ı açmanız ve oturum açmanız gerekir")
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        return response.use {
            val mangas = it.parseAs<HMLatestUpdateDto>().chapters.map {
                SManga.create().apply {
                    val manga = it.manga
                    title = manga.title
                    thumbnail_url = "$baseUrl/storage/${manga.thumbnail}"
                    url = "/manga/${manga.slug}"
                }
            }.distinctBy { manga -> manga.title }
            MangasPage(mangas, false)
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()

        if (genresList.isEmpty()) {
            genresList = parseGenres(document)
        }

        val mangas = document
            .select(".product-card.grow-box")
            .map(::mangaFromElement)

        return MangasPage(
            mangas = mangas,
            hasNextPage = document.selectFirst(".pagination .page-item:last-child:not(.disabled)") != null,
        )
    }

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/manga?page=$page", headers)

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

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

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(SEARCH_PREFIX)) {
            val slug = query.removePrefix(SEARCH_PREFIX)
            return client.newCall(GET("$baseUrl/manga/$slug", headers))
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
                    url = "/manga/${it.slug}"
                }
            }
            MangasPage(mangas, false)
        }
    }

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()

        filters += if (genresList.isNotEmpty()) {
            GenreList("Türler", genresList)
        } else {
            Filter.Header("Türleri göstermeyi denemek için 'Sıfırla' düğmesine basın")
        }

        return FilterList(filters)
    }

    override fun imageUrlParse(response: Response) = ""

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h5")!!.text()
        thumbnail_url = element.selectFirst(".img-con")?.absUrl("data-setbg")
        genre = element.select(".product-card-con ul li").joinToString { it.text() }
        val script = element.attr("onclick")
        setUrlWithoutDomain(REGEX_MANGA_URL.find(script)!!.groups["url"]!!.value)
    }

    private fun parseGenres(document: Document): List<Genre> {
        return document.select(".tags-blog a")
            .map { element -> Genre(element.text()) }
    }

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
        const val SEARCH_PREFIX = "slug:"
        val REGEX_MANGA_URL = """='(?<url>[^']+)""".toRegex()
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.US)
    }
}
