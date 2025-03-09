package eu.kanade.tachiyomi.extension.pt.yushukemangas

import android.net.Uri
import eu.kanade.tachiyomi.network.GET
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
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.net.URLEncoder

class YushukeMangas : ParsedHttpSource() {

    override val name = "Yushuke Mangas"

    override val baseUrl = "https://new.yushukemangas.com"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private var nextHash: String? = null

    override val versionId = 2

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(1, 2)
        .build()

    private val json: Json by injectLazy()

    // ============================== Popular ===============================

    override fun popularMangaRequest(page: Int) = GET(baseUrl, headers)

    override fun popularMangaSelector() = ".top10-section .top10-item a"

    override fun popularMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst("h3")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun popularMangaNextPageSelector() = null

    // ============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesSelector() = ".manga-list .manga-card"

    override fun latestUpdatesNextPageSelector() = "a.page-link:contains(>)"

    override fun latestUpdatesFromElement(element: Element) = SManga.create().apply {
        val url = element.selectFirst("a.manga-cover")!!.absUrl("href")
        val uri = Uri.parse(url)
        val pathSegments = uri.pathSegments
        val lastSegment = URLEncoder.encode(pathSegments.last(), "UTF-8")
        val encodedUrl = uri.buildUpon()
            .path(pathSegments.dropLast(1).joinToString("/") + "/$lastSegment")
            .toString()

        title = element.selectFirst("a.manga-title")!!.text()
        thumbnail_url = element.selectFirst("a.manga-cover img")?.absUrl("data-src")
        setUrlWithoutDomain(encodedUrl)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select(latestUpdatesSelector()).map { element ->
            latestUpdatesFromElement(element)
        }
        val nextUrl = document.selectFirst(latestUpdatesNextPageSelector())?.attr("href")
        val baseNextUrl = baseUrl + nextUrl
        nextHash = baseNextUrl?.toHttpUrlOrNull()?.queryParameter("pagina")

        return MangasPage(mangas, !nextHash.isNullOrEmpty())
    }

    // ============================== Search ===============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val urlFilterBuilder = filters.fold(baseUrl.toHttpUrl().newBuilder()) { urlBuilder, filter ->
            when (filter) {
                is RadioFilter -> {
                    val selected = filter.selected()
                    if (selected == all) return@fold urlBuilder
                    urlBuilder.addQueryParameter(filter.query, selected)
                }
                is GenreFilter -> {
                    filter.state
                        .filter(GenreCheckBox::state)
                        .fold(urlBuilder) { builder, genre ->
                            builder.addQueryParameter(filter.query, genre.id)
                        }
                }
                else -> urlBuilder
            }
        }

        val url = when {
            query.isBlank() -> urlFilterBuilder
            else -> baseUrl.toHttpUrl().newBuilder().addQueryParameter("search", query)
        }

        return GET(url.build(), headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.substringAfter(PREFIX_SEARCH)
            return client.newCall(GET("$baseUrl/manga/$slug", headers))
                .asObservableSuccess()
                .map {
                    val manga = mangaDetailsParse(it.asJsoup())
                    MangasPage(listOf(manga), false)
                }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaSelector() = ".search-result-item"

    override fun searchMangaParse(response: Response): MangasPage {
        return if (response.request.url.queryParameter("search").isNullOrBlank()) {
            latestUpdatesParse(response)
        } else {
            super.searchMangaParse(response)
        }
    }

    override fun searchMangaFromElement(element: Element) = SManga.create().apply {
        title = element.selectFirst(".search-result-title")!!.text()
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
        setUrlWithoutDomain(
            element.attr("onclick").let {
                SEARCH_URL_REGEX.find(it)?.groups?.get(1)?.value!!
            },
        )
    }

    override fun searchMangaNextPageSelector() = null

    // ============================== Manga Details =========================

    override fun mangaDetailsParse(document: Document) = SManga.create().apply {
        val details = document.selectFirst(".manga-banner .container")!!
        title = details.selectFirst("h1")!!.text()
        thumbnail_url = details.selectFirst("img")?.absUrl("src")
        genre = details.select(".genre-tag").joinToString { it.text() }
        description = details.selectFirst(".sinopse p")?.text()
        details.selectFirst(".manga-meta > div")?.ownText()?.let {
            status = when (it.lowercase()) {
                "em andamento" -> SManga.ONGOING
                "completo" -> SManga.COMPLETED
                "cancelado" -> SManga.CANCELLED
                "hiato" -> SManga.ON_HIATUS
                else -> SManga.UNKNOWN
            }
        }
        setUrlWithoutDomain(document.location())
    }

    private fun SManga.fetchMangaId(): String {
        val document = client.newCall(mangaDetailsRequest(this)).execute().asJsoup()
        return document.select("script")
            .map(Element::data)
            .firstOrNull(MANGA_ID_REGEX::containsMatchIn)
            ?.let { MANGA_ID_REGEX.find(it)?.groups?.get(1)?.value }
            ?: throw Exception("Manga ID não encontrado")
    }

    // ============================== Chapters ===============================

    override fun chapterListSelector() = "a.chapter-item"

    override fun chapterFromElement(element: Element) = SChapter.create().apply {
        val capituloTexto = element.select(".capitulo-numero")
            .textNodes()
            .joinToString(" ") { it.text().trim() }
            .split(" ")
            .take(2)
            .joinToString(" ")

        name = capituloTexto
        setUrlWithoutDomain(element.absUrl("href"))
    }

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val mangaId = manga.fetchMangaId()
        val chapters = mutableListOf<SChapter>()
        var page = 1
        do {
            val dto = fetchChapterListPage(mangaId, page++).parseAs<ChaptersDto>()
            val document = Jsoup.parseBodyFragment(dto.chapters, baseUrl)
            chapters += document.select(chapterListSelector()).map(::chapterFromElement)
        } while (dto.hasNext())
        return Observable.just(chapters)
    }

    private fun fetchChapterListPage(mangaId: String, page: Int): Response {
        val url = "$baseUrl/ajax/lzmvke.php?order=DESC".toHttpUrl().newBuilder()
            .addQueryParameter("manga_id", mangaId)
            .addQueryParameter("page", page.toString())
            .build()

        return client
            .newCall(GET(url, headers))
            .execute()
    }

    // ============================== Pages ===============================

    override fun pageListParse(document: Document): List<Page> {
        return document.select("div.select-nav + * picture")
            .mapIndexedNotNull { index, pictureElement ->
                val imgElement = pictureElement.selectFirst("img")
                val imageUrl = imgElement?.attr("src")?.takeIf { it.isNotBlank() } ?: return@mapIndexedNotNull null
                Page(index, imageUrl = "$baseUrl$imageUrl")
            }
    }

    override fun imageUrlParse(document: Document) = ""

    // ============================== Filters =============================

    override fun getFilterList(): FilterList {
        return FilterList(
            RadioFilter("Status", "status", statusList),
            RadioFilter("Tipo", "tipo", typeList),
            GenreFilter("Gêneros", "tags[]", genresList),
        )
    }

    class RadioFilter(
        displayName: String,
        val query: String,
        private val vals: Array<String>,
        state: Int = 0,
    ) : Filter.Select<String>(displayName, vals, state) {
        fun selected() = vals[state]
    }

    protected class GenreFilter(
        title: String,
        val query: String,
        genres: List<String>,
    ) : Filter.Group<GenreCheckBox>(title, genres.map { GenreCheckBox(it) })

    class GenreCheckBox(name: String, val id: String = name) : Filter.CheckBox(name)

    private val all = "Todos"

    private val statusList = arrayOf(
        all,
        "Em andamento",
        "Completo",
        "Cancelado",
        "Hiato",
    )

    private val typeList = arrayOf(
        all,
        "Mangá",
        "Manhwa",
        "Manhua",
        "Comics",
    )

    private var genresList: List<String> = listOf(
        "Ação", "Artes Marciais", "Aventura",
        "Comédia",
        "Drama",
        "Escolar",
        "Esporte",
        "Fantasia",
        "Harém", "Histórico",
        "Isekai",
        "Josei",
        "Mistério",
        "Reencarnação", "Regressão", "Romance",
        "Sci-fi", "Seinen", "Shoujo", "Shounen", "Slice of Life", "Sobrenatural", "Super Poderes",
        "Terror",
        "Vingança",
    )

    // ============================== Utilities ===========================

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromStream(body.byteStream())
    }

    @Serializable
    class ChaptersDto(val chapters: String, private val remaining: Int) {
        fun hasNext() = remaining > 0
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
        val SEARCH_URL_REGEX = "'([^']+)".toRegex()
        val MANGA_ID_REGEX = """obra_id:\s+(\d+)""".toRegex()
    }
}
