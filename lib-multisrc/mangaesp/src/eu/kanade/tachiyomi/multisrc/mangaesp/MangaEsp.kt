package eu.kanade.tachiyomi.multisrc.mangaesp

import eu.kanade.tachiyomi.lib.i18n.Intl
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

abstract class MangaEsp(
    override val name: String,
    override val baseUrl: String,
    override val lang: String,
    protected val apiBaseUrl: String = baseUrl.replace("://", "://apis."),
) : HttpSource() {

    override val supportsLatest = true

    protected val json: Json by injectLazy()

    protected val intl = Intl(
        language = lang,
        baseLanguage = "en",
        availableLanguages = setOf("en", "es"),
        classLoader = this::class.java.classLoader!!,
    )

    protected open val apiPath = "/api"

    protected open val seriesPath = "/ver"

    protected open val useApiSearch = false

    override val client: OkHttpClient = network.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 2)
        .build()

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request = GET("$apiBaseUrl$apiPath/topSerie", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val responseData = json.decodeFromString<TopSeriesDto>(response.body.string())

        val topDaily = responseData.response.topDaily.flatten().map { it.data }
        val topWeekly = responseData.response.topWeekly.flatten().map { it.data }
        val topMonthly = responseData.response.topMonthly.flatten().map { it.data }

        val mangas = (topDaily + topWeekly + topMonthly).distinctBy { it.slug }
            .additionalParse()
            .map { it.toSManga(seriesPath) }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$apiBaseUrl$apiPath/lastUpdates", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val responseData = json.decodeFromString<LastUpdatesDto>(response.body.string())

        val mangas = responseData.response
            .additionalParse()
            .map { it.toSManga(seriesPath) }

        return MangasPage(mangas, false)
    }

    protected var comicsList = mutableListOf<SeriesDto>()

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

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return if (useApiSearch) {
            GET("$apiBaseUrl$apiPath/comics", headers)
        } else {
            GET("$baseUrl/comics", headers)
        }
    }

    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException()

    protected open fun searchMangaParse(response: Response, page: Int, query: String, filters: FilterList): MangasPage {
        comicsList = if (useApiSearch) {
            json.decodeFromString<List<SeriesDto>>(response.body.string()).toMutableList()
        } else {
            val script = response.asJsoup().select("script:containsData(self.__next_f.push)").joinToString { it.data() }
            val jsonString = MANGA_LIST_REGEX.find(script)?.groupValues?.get(1)
                ?: throw Exception(intl["comics_list_error"])
            val unescapedJson = jsonString.unescape()
            json.decodeFromString<List<SeriesDto>>(unescapedJson).toMutableList()
        }.additionalParse().toMutableList()
        return parseComicsList(page, query, filters)
    }

    protected open fun List<SeriesDto>.additionalParse(): List<SeriesDto> {
        return this
    }

    private var filteredList = mutableListOf<SeriesDto>()

    protected open fun parseComicsList(page: Int, query: String, filterList: FilterList): MangasPage {
        if (page == 1) {
            filteredList.clear()

            if (query.isNotBlank()) {
                if (query.length < 2) throw Exception(intl["search_length_error"])
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
                if (statusFilter.toUriPart() != 0) {
                    filteredList = filteredList.filter { it.status == statusFilter.toUriPart() }.toMutableList()
                }
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
                .map { it.toSManga(seriesPath) },
            hasNextPage,
        )
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val responseBody = response.body.string()
        val mangaDetailsJson = MANGA_DETAILS_REGEX.find(responseBody)?.groupValues?.get(1)
            ?: throw Exception(intl["comic_data_error"])
        val unescapedJson = mangaDetailsJson.unescape()

        return json.decodeFromString<SeriesDto>(unescapedJson).toSMangaDetails()
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val responseBody = response.body.string()
        val mangaDetailsJson = MANGA_DETAILS_REGEX.find(responseBody)?.groupValues?.get(1)
            ?: throw Exception(intl["comic_data_error"])
        val unescapedJson = mangaDetailsJson.unescape()
        val series = json.decodeFromString<SeriesDto>(unescapedJson)
        return series.chapters.map { it.toSChapter(seriesPath, series.slug) }
    }

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("main.contenedor.read img").mapIndexed { i, img ->
            Page(i, imageUrl = img.imgAttr())
        }
    }

    override fun getFilterList() = FilterList(
        SortByFilter(intl["sort_by_filter_title"], getSortProperties()),
        StatusFilter(intl["status_filter_title"], getStatusList()),
    )

    protected open fun getSortProperties(): List<SortProperty> = listOf(
        SortProperty(intl["sort_by_filter_name"], "name"),
        SortProperty(intl["sort_by_filter_views"], "views"),
        SortProperty(intl["sort_by_filter_updated"], "updated_at"),
        SortProperty(intl["sort_by_filter_added"], "created_at"),
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

    private class StatusFilter(title: String, statusList: Array<Pair<String, Int>>) : UriPartFilter(
        title,
        statusList,
    )

    protected open fun getStatusList() = arrayOf(
        Pair(intl["status_filter_all"], 0),
        Pair(intl["status_filter_ongoing"], 1),
        Pair(intl["status_filter_hiatus"], 2),
        Pair(intl["status_filter_dropped"], 3),
        Pair(intl["status_filter_completed"], 4),
    )

    private open class UriPartFilter(displayName: String, private val vals: Array<Pair<String, Int>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }

    private inline fun <reified R> List<*>.firstInstanceOrNull(): R? =
        filterIsInstance<R>().firstOrNull()

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    protected open fun Element.imgAttr(): String = when {
        hasAttr("data-lazy-src") -> attr("abs:data-lazy-src")
        hasAttr("data-src") -> attr("abs:data-src")
        hasAttr("data-cfsrc") -> attr("abs:data-cfsrc")
        else -> attr("abs:src")
    }

    fun String.unescape(): String {
        return UNESCAPE_REGEX.replace(this, "$1")
    }

    companion object {
        private val UNESCAPE_REGEX = """\\(.)""".toRegex()
        val MANGA_LIST_REGEX = """self\.__next_f\.push\(.*data\\":(\[.*trending.*])\}""".toRegex()
        val MANGA_DETAILS_REGEX = """self\.__next_f\.push\(.*data\\":(\{.*lastChapters.*\}).*\\"numFollow""".toRegex()
        const val MANGAS_PER_PAGE = 15
    }
}
