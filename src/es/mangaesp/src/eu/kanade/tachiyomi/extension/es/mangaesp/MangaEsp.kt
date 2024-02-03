package eu.kanade.tachiyomi.extension.es.mangaesp

import android.util.Log
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
import java.text.SimpleDateFormat
import java.util.Locale

class MangaEsp : HttpSource() {

    override val name = "MangaEsp"

    override val baseUrl = "https://mangaesp.co"

    private val apiBaseUrl = "https://apis.mangaesp.co"

    private val imgCdnUrl = "https://cdn.statically.io/img"

    override val lang = "es"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)

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

        val mangas = (topDaily + topWeekly + topMonthly).distinctBy { it.slug }.map { series ->
            SManga.create().apply {
                title = series.name
                thumbnail_url = series.thumbnail?.let { "$imgCdnUrl/${it.removeProtocol()}" }
                url = "/ver/${series.slug}"
            }
        }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiBaseUrl/api/lastUpdates", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val responseData = json.decodeFromString<LastUpdatesDto>(response.body.string())

        val mangas = responseData.response.map { series ->
            SManga.create().apply {
                title = series.name
                thumbnail_url = series.thumbnail?.let { "$imgCdnUrl/${it.removeProtocol()}" }
                url = "/ver/${series.slug}"
            }
        }

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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/comics", headers)

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    private fun searchMangaParse(response: Response, page: Int, query: String, filters: FilterList): MangasPage {
        val responseData = json.decodeFromString<ComicsDto>(response.body.string())
        comicsList = responseData.response.toMutableList()
        return parseComicsList(page, query, filters)
    }

    private var filteredList = mutableListOf<SeriesDto>()

    private fun parseComicsList(page: Int, query: String, filterList: FilterList): MangasPage {
        if (page == 1) {
            filteredList.clear()
            if (query.isNotBlank()) {
                if (query.length < 2) throw Exception("La búsqueda debe tener al menos 2 caracteres")
                filteredList.addAll(comicsList.filter { it.name.contains(query, ignoreCase = true) })
            } else {
                filteredList.addAll(comicsList)
            }
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

        val hasNextPage = filteredList.size > page * MANGAS_PER_PAGE

        Log.d("Bapeey", "filteredList.size: ${filteredList.size}")
        Log.d("Bapeey", "page: $page")
        Log.d("Bapeey", "comics.size: ${comicsList.size}")

        return MangasPage(
            filteredList.subList((page - 1) * MANGAS_PER_PAGE, filteredList.size)
                .map { it.toSimpleSManga(imgCdnUrl) },
            hasNextPage,
        )
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val responseBody = response.body.string()
        val mangaDetailsJson = MANGA_DETAILS_REGEX.find(responseBody)?.groupValues?.get(1)
            ?: throw Exception("Could not find manga details in response")
        val unescapedJson = mangaDetailsJson.replace("\\", "")

        val series = json.decodeFromString<SeriesDto>(unescapedJson)
        return SManga.create().apply {
            title = series.name
            thumbnail_url = series.thumbnail?.let { "$imgCdnUrl/${it.removeProtocol()}" }
            description = series.synopsis
            genre = series.genders.joinToString { it.gender.name }
            author = series.authors.joinToString { it.author.name }
            artist = series.artists.joinToString { it.artist.name }
        }
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseBody = response.body.string()
        val mangaDetailsJson = MANGA_DETAILS_REGEX.find(responseBody)?.groupValues?.get(1)
            ?: throw Exception("Could not find manga details in response")
        val unescapedJson = mangaDetailsJson.replace("\\", "")
        val series = json.decodeFromString<SeriesDto>(unescapedJson)
        return series.chapters.map { chapter ->
            SChapter.create().apply {
                name = if (chapter.name.isNullOrBlank()) {
                    "Capítulo ${chapter.number.toString().removeSuffix(".0")}"
                } else {
                    "Capítulo ${chapter.number.toString().removeSuffix(".0")} - ${chapter.name}"
                }
                date_upload = runCatching { dateFormat.parse(chapter.date)?.time }.getOrNull() ?: 0L
                url = "/ver/${series.slug}/${chapter.slug}"
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("main.contenedor.read img").mapIndexed { i, img ->
            Page(i, "", img.imgAttr())
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

    private fun String.removeProtocol(): String = replaceFirst(Regex("https?://"), "")

    private fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    companion object {
        private val MANGA_DETAILS_REGEX = """self.__next_f.push\(.*data\\":(\{.*lastChapters.*\}).*numFollow""".toRegex()
        private const val MANGAS_PER_PAGE = 15
    }
}
