package eu.kanade.tachiyomi.multisrc.lectormonline

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import keiyoushi.utils.parseAs
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Document
import kotlin.concurrent.thread

open class LectorMOnline(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
) : HttpSource() {

    override val supportsLatest = true

    override fun popularMangaRequest(page: Int): Request {
        return GET("$baseUrl/comics?sort=views&page=$page", headers)
    }

    override fun popularMangaParse(response: Response): MangasPage = searchMangaParse(response)

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/comics?page=$page", headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = searchMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = baseUrl.toHttpUrl().newBuilder()
            .addPathSegment("comics")
            .addQueryParameter("q", query)
            .addQueryParameter("page", page.toString())

        filters.forEach { filter ->
            when (filter) {
                is SortByFilter -> {
                    if (filter.selected == "views") {
                        url.addQueryParameter("sort", "views")
                    }
                    if (filter.state!!.ascending) {
                        url.addQueryParameter("isDesc", "false")
                    }
                }
                is GenreFilter -> {
                    val selectedGenre = filter.toUriPart()
                    if (selectedGenre.isNotEmpty()) {
                        return GET("$baseUrl/genres/$selectedGenre?page=$page", headers)
                    }
                }
                else -> { }
            }
        }

        return GET(url.build(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        val document = response.asJsoup()
        if (response.request.url.pathSegments[0] == "genres") {
            return searchMangaGenreParse(document)
        }
        val script = document.select("script:containsData(self.__next_f.push)").joinToString { it.data() }
        val jsonData = COMICS_LIST_REGEX.find(script)?.groupValues?.get(1)?.unescape()
            ?: throw Exception("No se pudo encontrar la lista de cómics")
        val data = jsonData.parseAs<ComicListDataDto>()
        return MangasPage(data.comics.map { it.toSManga() }, data.hasNextPage())
    }

    private fun searchMangaGenreParse(document: Document): MangasPage {
        val mangas = document.select("div.grid.relative > a.group.relative").map { element ->
            SManga.create().apply {
                setUrlWithoutDomain(element.attr("href").substringAfter("/comics/").substringBefore("?"))
                title = element.selectFirst("h3")!!.text()
                thumbnail_url = element.selectFirst("img")?.attr("abs:src")
            }
        }
        val hasNextPage = document.selectFirst("div.flex.items-center > a:has(> svg):last-child:not(.pointer-events-none)") != null
        return MangasPage(mangas, hasNextPage)
    }

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comics/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        return GET("$baseUrl/api/app/comic/${manga.url}", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<ComicDto>().toSMangaDetails()
    }

    override fun getChapterUrl(chapter: SChapter): String {
        val mangaSlug = chapter.url.substringBefore("/")
        val chapterNumber = chapter.url.substringAfter("/")
        return "$baseUrl/comics/$mangaSlug/chapters/$chapterNumber"
    }

    override fun chapterListRequest(manga: SManga) = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<ComicDto>().getChapters()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaSlug = chapter.url.substringBefore("/")
        val chapterNumber = chapter.url.substringAfter("/")
        return GET("$baseUrl/api/app/comic/$mangaSlug/chapter/$chapterNumber", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ChapterPagesDataDto>()
        return data.chapter.urlImagesChapter.mapIndexed { index, image ->
            Page(index, imageUrl = image)
        }
    }

    private var genresList: List<Pair<String, String>> = emptyList()
    private var fetchFiltersAttempts = 0
    private var filtersState = FiltersState.NOT_FETCHED

    private fun fetchFilters() {
        if (filtersState != FiltersState.NOT_FETCHED || fetchFiltersAttempts >= 3) return
        filtersState = FiltersState.FETCHING
        fetchFiltersAttempts++
        thread {
            try {
                val response = client.newCall(GET("$baseUrl/api/app/genres", headers)).execute()
                val filters = response.parseAs<GenreListDto>()

                genresList = filters.genres.map { genre -> genre.name.lowercase().replaceFirstChar { it.uppercase() } to genre.name }

                filtersState = FiltersState.FETCHED
            } catch (_: Throwable) {
                filtersState = FiltersState.NOT_FETCHED
            }
        }
    }

    override fun getFilterList(): FilterList {
        fetchFilters()

        val filters = mutableListOf<Filter<*>>(
            Filter.Header("El filtro por género no funciona con los demas filtros"),
            Filter.Separator(),
            SortByFilter(
                "Ordenar por",
                listOf(
                    SortProperty("Más vistos", "views"),
                    SortProperty("Más recientes", "created_at"),
                ),
                1,
            ),
        )

        filters += if (filtersState == FiltersState.FETCHED) {
            listOf(
                Filter.Separator(),
                Filter.Header("Filtrar por género"),
                GenreFilter(genresList),
            )
        } else {
            listOf(
                Filter.Separator(),
                Filter.Header("Presione 'Reiniciar' para intentar cargar los filtros"),
            )
        }

        return FilterList(filters)
    }

    private enum class FiltersState { NOT_FETCHED, FETCHING, FETCHED }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun String.unescape(): String {
        return UNESCAPE_REGEX.replace(this, "$1")
    }

    companion object {
        private val UNESCAPE_REGEX = """\\(.)""".toRegex()
        private val COMICS_LIST_REGEX = """\\"comicsData\\":(\{.*?\}),\\"searchParams""".toRegex()
    }
}
