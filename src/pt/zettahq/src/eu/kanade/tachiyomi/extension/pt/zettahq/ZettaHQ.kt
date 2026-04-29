package eu.kanade.tachiyomi.extension.pt.zettahq

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import rx.Observable
import java.text.Normalizer

class ZettaHQ : HttpSource() {

    override val name = "ZettaHQ"

    override val baseUrl = "https://zettahq.com"

    override val lang = "pt-BR"

    override val supportsLatest = false

    override val client = network.cloudflareClient

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int) = GET("$baseUrl/page/$page", headers)

    override fun fetchPopularManga(page: Int): Observable<MangasPage> {
        if (genreList.isEmpty()) getFilters()
        return super.fetchPopularManga(page)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        val mangas = document.select("div.post-item article").map(::mangaFromElement)
        val hasNextPage = document.selectFirst(".next.page-numbers") != null
        return MangasPage(mangas, hasNextPage)
    }

    private fun mangaFromElement(element: Element) = SManga.create().apply {
        element.selectFirst("h3 a")!!.let { anchor ->
            title = anchor.text()
            setUrlWithoutDomain(anchor.absUrl("href"))
        }
        thumbnail_url = element.selectFirst("img")?.absUrl("src")
    }

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int) = throw UnsupportedOperationException()
    override fun latestUpdatesParse(response: Response): MangasPage = throw UnsupportedOperationException()

    // ============================== Search ==============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$baseUrl/".toHttpUrl().newBuilder()

        var isCategoryEnable = false
        var isGenreEnable = false
        var isAuthorEnable = false

        filters
            .filterNot { it is Filter.Separator }
            .sortedByDescending { (it as Sort).priority }
            .forEach { filter ->
                when (filter) {
                    is GenreList -> {
                        val genresSelected = filter.state
                            .filter { it.state }
                            .joinToString("+") { it.id }
                            .takeIf(String::isNotEmpty) ?: return@forEach

                        if (isCategoryEnable) {
                            url.addQueryParameter("tag", genresSelected)
                            return@forEach
                        }

                        url.addPathSegment("tag")
                            .addPathSegment(genresSelected)

                        isGenreEnable = isGenreEnable.not()
                    }

                    is SelectFilter -> {
                        val selected = filter.selected()
                        if (selected.isBlank()) return@forEach

                        if (isCategoryEnable || isGenreEnable || isAuthorEnable) {
                            url.addQueryParameter(filter.query, selected)
                            return@forEach
                        }

                        url.addPathSegment(filter.query)
                            .addPathSegment(selected)

                        when {
                            filter.query.equals("autor", true) -> {
                                isAuthorEnable = isAuthorEnable.not()
                            }

                            filter.query.equals("category", true) -> {
                                isCategoryEnable = isCategoryEnable.not()
                            }

                            else -> {}
                        }
                    }

                    else -> {}
                }
            }

        url.addPathSegment("page")
            .addPathSegment(page.toString())
            .addQueryParameter("s", query)

        return GET(url.build(), headers)
    }

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) {
            val slug = query.substringAfter(PREFIX_SEARCH)
            return fetchMangaDetails(SManga.create().apply { url = "/$slug" })
                .map { manga -> MangasPage(listOf(manga), false) }
        }
        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================== Details ==============================

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val document = response.asJsoup()
        title = document.selectFirst("h1")!!.text()
        thumbnail_url = document.selectFirst(".content-container article img:first-child")?.absUrl("src")
        genre = document.select(".tags > a.tag").joinToString { it.text() }
        author = document.selectFirst("strong:contains(Autor) + a")?.text()
        status = SManga.COMPLETED
        setUrlWithoutDomain(document.location())
    }

    // ============================== Chapters ==============================

    override fun fetchChapterList(manga: SManga): Observable<List<SChapter>> {
        val chapters = listOf(
            SChapter.create().apply {
                name = "Capítulo Único"
                url = manga.url
            },
        )
        return Observable.just(chapters)
    }

    override fun chapterListParse(response: Response): List<SChapter> = throw UnsupportedOperationException()

    // =============================== Pages ===============================

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select(".content-container article img").mapIndexed { index, element ->
            Page(index, imageUrl = element.absUrl("src"))
        }
    }

    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    // =============================== Filters ===============================

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>()
        if (genreList.isNotEmpty()) {
            filters += listOf(
                SelectFilter(title = "Categorias", vals = categoryList, query = "category", priority = 3),
                Filter.Separator(),
                SelectFilter(title = "Personagens", vals = characterList, query = "personagem"),
                Filter.Separator(),
                SelectFilter(title = "Autor", vals = authorList, query = "autor", priority = 1),
                Filter.Separator(),
                SelectFilter(title = "Paródia", vals = parodyList, query = "parodia"),
                Filter.Separator(),
                GenreList(title = "Gêneros", genres = genreList, priority = 2),
            )
        } else {
            filters += listOf(Filter.Header("Aperte 'Redefinir' para tentar mostrar os filtros"))
        }
        return FilterList(filters)
    }

    private var categoryList = emptyArray<Pair<String, String>>()
    private var authorList = emptyArray<Pair<String, String>>()
    private var characterList = emptyArray<Pair<String, String>>()
    private var parodyList = emptyArray<Pair<String, String>>()
    private var genreList = emptyList<Genre>()

    private fun getFilters() {
        val document = client.newCall(GET("$baseUrl/busca-avancada/", headers))
            .execute()
            .asJsoup()

        categoryList = parseOptions(document, "ofcategory")
        authorList = parseOptions(document, "ofautor")
        characterList = parseOptions(document, "ofpersonagem")
        parodyList = parseOptions(document, "ofparodia")
        genreList = parseGenres(document)
    }

    private fun parseGenres(document: Document): List<Genre> = document.select(".cat-item > label")
        .map { label ->
            Genre(
                name = label.text(),
                id = label.text().normalize(),
            )
        }

    private fun parseOptions(document: Document, attr: String): Array<Pair<String, String>> {
        val options = mutableListOf("Todos" to "")

        options += document.select("select[name*=$attr] option").map { option ->
            option.text() to option.text().normalize()
        }

        return options.toTypedArray()
    }

    private fun String.normalize() = this
        .lowercase().trim()
        .replace(SPACE_REGEX, "-")
        .removeAccents()

    private fun String.removeAccents(): String {
        val normalized = Normalizer.normalize(this, Normalizer.Form.NFD)
        return normalized.replace(ACCENT_REGEX, "")
    }

    companion object {
        const val PREFIX_SEARCH = "id:"
        val SPACE_REGEX = Regex("""\s+""")
        private val ACCENT_REGEX = Regex("""[\p{InCombiningDiacriticalMarks}]""")
    }
}
