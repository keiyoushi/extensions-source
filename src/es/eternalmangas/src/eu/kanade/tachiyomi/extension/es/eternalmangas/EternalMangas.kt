package eu.kanade.tachiyomi.extension.es.eternalmangas

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
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
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.jsoup.nodes.Element
import rx.Observable
import uy.kohesive.injekt.injectLazy
import kotlin.math.min

class EternalMangas : HttpSource() {

    override val name = "EternalMangas"

    override val baseUrl = "https://eternalmangas.com"

    private val apiBaseUrl = "https://apis.eternalmangas.com"

    override val lang = "es"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$apiBaseUrl/api/topSerie", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val responseData = json.decodeFromString<TopSeriesDto>(response.body.string())

        val topDaily = responseData.response.topDaily.flatten().map { it.data }
        val topWeekly = responseData.response.topWeekly.flatten().map { it.data }
        val topMonthly = responseData.response.topMonthly.flatten().map { it.data }

        val mangas = (topDaily + topWeekly + topMonthly).distinctBy { it.slug }.map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiBaseUrl/api/lastUpdates", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val responseData = json.decodeFromString<LastUpdatesDto>(response.body.string())

        val mangas = responseData.response.map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    private var comicsList = mutableListOf<SeriesDto>()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (comicsList.isEmpty()) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it, page, query, filters) }
        } else {
            Observable.just(parseComicsList(page, query, filters))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/comics", headers)

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    private fun searchMangaParse(response: Response, page: Int, query: String, filters: FilterList): MangasPage {
        val document = response.asJsoup()
        val script = document.select("script:containsData(self.__next_f.push)").joinToString { it.data() }
        val jsonString = MANGA_LIST_REGEX.find(script)?.groupValues?.get(1)
            ?: throw Exception("No se pudo encontrar la lista de comics")
        val unescapedJson = jsonString.unescape()
        comicsList = json.decodeFromString<List<SeriesDto>>(unescapedJson).toMutableList()
        return parseComicsList(page, query, filters)
    }

    private var filteredList = mutableListOf<SeriesDto>()

    private fun parseComicsList(page: Int, query: String, filterList: FilterList): MangasPage {
        if (page == 1) {
            filteredList.clear()

            if (query.isNotBlank()) {
                if (query.length < 2) throw Exception("La búsqueda debe tener al menos 2 caracteres")
                filteredList.addAll(
                    comicsList.filter {
                        it.name.contains(query, ignoreCase = true) || it.alternativeName?.contains(query, ignoreCase = true) == true
                    },
                )
            } else {
                filteredList.addAll(comicsList)
            }

            val statusFilter = filterList.firstInstanceOrNull<StatusFilter>()

            if (statusFilter != null) {
                filteredList = filteredList.filter { it.status == statusFilter.toUriPart() }.toMutableList()
            }

            val sortByFilter = filterList.firstInstanceOrNull<SortByFilter>()

            if (sortByFilter != null) {
                if (sortByFilter.state?.ascending == true) {
                    when (sortByFilter.selected) {
                        "name" -> filteredList.sortBy { it.name }
                        "views" -> filteredList.sortBy { it.trending?.views }
                        "updated_at" -> filteredList.sortBy { it.lastChapterDate }
                        "created_at" -> filteredList.sortBy { it.createdAt }
                    }
                } else {
                    when (sortByFilter.selected) {
                        "name" -> filteredList.sortByDescending { it.name }
                        "views" -> filteredList.sortByDescending { it.trending?.views }
                        "updated_at" -> filteredList.sortByDescending { it.lastChapterDate }
                        "created_at" -> filteredList.sortByDescending { it.createdAt }
                    }
                }
            }
        }

        val hasNextPage = filteredList.size > page * MANGAS_PER_PAGE

        return MangasPage(
            filteredList.subList((page - 1) * MANGAS_PER_PAGE, min(page * MANGAS_PER_PAGE, filteredList.size))
                .map { it.toSManga() },
            hasNextPage,
        )
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val responseBody = response.body.string()
        val mangaDetailsJson = MANGA_DETAILS_REGEX.find(responseBody)?.groupValues?.get(1)
            ?: throw Exception("No se pudo encontrar los detalles del manga")
        val unescapedJson = mangaDetailsJson.unescape()

        return json.decodeFromString<SeriesDto>(unescapedJson).toSMangaDetails()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseBody = response.body.string()
        val mangaDetailsJson = MANGA_DETAILS_REGEX.find(responseBody)?.groupValues?.get(1)
            ?: throw Exception("No se pudo encontrar la lista de capítulos")
        val unescapedJson = mangaDetailsJson.unescape()
        val series = json.decodeFromString<SeriesDto>(unescapedJson)
        return series.chapters.map { it.toSChapter(series.slug) }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("main.contenedor.read img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    override fun getFilterList() = FilterList(
        SortByFilter("Ordenar por", getSortProperties()),
        StatusFilter(),
    )

    private fun getSortProperties(): List<SortProperty> = listOf(
        SortProperty("Nombre", "name"),
        SortProperty("Visitas", "views"),
        SortProperty("Actualización", "updated_at"),
        SortProperty("Agregado", "created_at"),
    )

    data class SortProperty(val name: String, val value: String) {
        override fun toString(): String = name
    }

    class SortByFilter(title: String, private val sortProperties: List<SortProperty>) : Filter.Sort(
        title,
        sortProperties.map { it.name }.toTypedArray(),
        Selection(2, ascending = false),
    ) {
        val selected: String
            get() = sortProperties[state!!.index].value
    }

    private class StatusFilter : UriPartFilter(
        "Estado",
        arrayOf(
            Pair("En emisión", 1),
            Pair("En pausa", 2),
            Pair("Abandonado", 3),
            Pair("Finalizado", 4),
        ),
    )

    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, Int>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private inline fun <reified R> List<*>.firstInstanceOrNull(): R? =
        filterIsInstance<R>().firstOrNull()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    private fun String.unescape(): String {
        return UNESCAPE_REGEX.replace(this, "$1")
    }

    companion object {
        private val UNESCAPE_REGEX = """\\(.)""".toRegex()
        private val MANGA_LIST_REGEX = """self\.__next_f\.push\(.*data\\":(\[.*trending.*])\}""".toRegex()
        private val MANGA_DETAILS_REGEX = """self\.__next_f\.push\(.*data\\":(\{.*lastChapters.*\}).*\\"numFollow""".toRegex()
        private const val MANGAS_PER_PAGE = 15
    }
}
