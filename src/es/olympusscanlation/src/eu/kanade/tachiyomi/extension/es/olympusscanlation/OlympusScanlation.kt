package eu.kanade.tachiyomi.extension.es.olympusscanlation

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class OlympusScanlation : HttpSource() {

    override val versionId = 2

    override val baseUrl: String = "https://olympusvisor.com"
    private val apiBaseUrl: String = "https://dashboard.olympusvisor.com"

    override val lang: String = "es"
    override val name: String = "Olympus Scanlation"

    override val supportsLatest: Boolean = true

    override val client = super.client.newBuilder()
        .rateLimitHost(baseUrl.toHttpUrl(), 1, 2)
        .rateLimitHost(apiBaseUrl.toHttpUrl(), 2, 1)
        .build()

    private val json: Json by injectLazy()
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override fun popularMangaRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/api/series?page=1&direction=asc&type=comic".toHttpUrl().newBuilder()
            .build()
        return GET(apiUrl, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        runCatching { fetchFilters() }
        val result = json.decodeFromString<PayloadSeriesDto>(response.body.string())
        val resultMangaList = json.decodeFromString<List<MangaDto>>(result.data.recommended_series)
        val mangaList = resultMangaList.filter { it.type == "comic" }.map {
            SManga.create().apply {
                url = "/series/comic-${it.slug}"
                title = it.name
                thumbnail_url = it.cover
            }
        }
        return MangasPage(mangaList, hasNextPage = false)
    }

    override fun latestUpdatesRequest(page: Int): Request {
        val apiUrl = "$apiBaseUrl/api/sf/new-chapters?page=$page".toHttpUrl().newBuilder()
            .build()
        return GET(apiUrl, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage {
        runCatching { fetchFilters() }
        val result = json.decodeFromString<NewChaptersDto>(response.body.string())
        val mangaList = result.data.filter { it.type == "comic" }.map {
            SManga.create().apply {
                url = "/series/comic-${it.slug}"
                title = it.name
                thumbnail_url = it.cover
            }
        }
        val hasNextPage = result.current_page < result.last_page
        return MangasPage(mangaList, hasNextPage)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.isNotEmpty()) {
            val apiUrl = "$apiBaseUrl/api/search".toHttpUrl().newBuilder()
                .addQueryParameter("name", query)
                .build()
            return GET(apiUrl, headers)
        }

        val url = "$apiBaseUrl/api/series".toHttpUrl().newBuilder()
        filters.forEach { filter ->
            when (filter) {
                is SortFilter -> {
                    if (filter.state?.ascending == true) {
                        url.addQueryParameter("direction", "desc")
                    } else {
                        url.addQueryParameter("direction", "asc")
                    }
                }
                is GenreFilter -> {
                    if (filter.toUriPart() != 9999) {
                        url.addQueryParameter("genres", filter.toUriPart().toString())
                    }
                }
                is StatusFilter -> {
                    if (filter.toUriPart() != 9999) {
                        url.addQueryParameter("status", filter.toUriPart().toString())
                    }
                }
                else -> {}
            }
        }
        url.addQueryParameter("type", "comic")
        url.addQueryParameter("page", page.toString())
        return GET(url.build().toString(), headers)
    }

    override fun searchMangaParse(response: Response): MangasPage {
        runCatching { fetchFilters() }
        if (response.request.url.toString().startsWith("$apiBaseUrl/api/search")) {
            val result = json.decodeFromString<PayloadMangaDto>(response.body.string())
            val mangaList = result.data.filter { it.type == "comic" }.map {
                SManga.create().apply {
                    url = "/series/comic-${it.slug}"
                    title = it.name
                    thumbnail_url = it.cover
                }
            }
            return MangasPage(mangaList, hasNextPage = false)
        }

        val result = json.decodeFromString<PayloadSeriesDto>(response.body.string())
        val mangaList = result.data.series.data.map {
            SManga.create().apply {
                url = "/series/comic-${it.slug}"
                title = it.name
                thumbnail_url = it.cover
            }
        }
        val hasNextPage = result.data.series.current_page < result.data.series.last_page
        return MangasPage(mangaList, hasNextPage)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val slug = response.request.url
            .toString()
            .substringAfter("/series/comic-")
            .substringBefore("/chapters")
        val apiUrl = "$apiBaseUrl/api/series/$slug?type=comic"
        val newRequest = GET(url = apiUrl, headers = headers)
        val newResponse = client.newCall(newRequest).execute()
        val result = json.decodeFromString<MangaDetailDto>(newResponse.body.string())
        return SManga.create().apply {
            url = "/series/comic-$slug"
            title = result.data.name
            thumbnail_url = result.data.cover
            description = result.data.summary
            status = parseStatus(result.data.status?.id)
            genre = result.data.genres?.joinToString { it.name.trim() }
        }
    }

    override fun getChapterUrl(chapter: SChapter): String = baseUrl + chapter.url

    override fun chapterListRequest(manga: SManga): Request {
        return paginatedChapterListRequest(
            manga.url
                .substringAfter("/series/comic-")
                .substringBefore("/chapters"),
            1,
        )
    }

    private fun paginatedChapterListRequest(mangaUrl: String, page: Int): Request {
        return GET(
            url = "$apiBaseUrl/api/series/$mangaUrl/chapters?page=$page&direction=desc&type=comic",
            headers = headers,
        )
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val slug = response.request.url
            .toString()
            .substringAfter("/series/")
            .substringBefore("/chapters")
        val data = json.decodeFromString<PayloadChapterDto>(response.body.string())
        var resultSize = data.data.size
        var page = 2
        while (data.meta.total > resultSize) {
            val newRequest = paginatedChapterListRequest(slug, page)
            val newResponse = client.newCall(newRequest).execute()
            val newData = json.decodeFromString<PayloadChapterDto>(newResponse.body.string())
            data.data += newData.data
            resultSize += newData.data.size
            page += 1
        }
        return data.data.map { chap -> chapterFromObject(chap, slug) }
    }

    private fun chapterFromObject(chapter: ChapterDto, slug: String) = SChapter.create().apply {
        url = "/capitulo/${chapter.id}/comic-$slug"
        name = "Capitulo ${chapter.name}"
        date_upload = runCatching { dateFormat.parse(chapter.date)?.time }
            .getOrNull() ?: 0L
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val id = chapter.url
            .substringAfter("/capitulo/")
            .substringBefore("/chapters")
            .substringBefore("/comic")
        val slug = chapter.url
            .substringAfter("comic-")
            .substringBefore("/chapters")
            .substringBefore("/comic")
        return GET("$apiBaseUrl/api/series/$slug/chapters/$id?type=comic")
    }

    override fun pageListParse(response: Response): List<Page> {
        return json.decodeFromString<PayloadPagesDto>(response.body.string()).chapter.pages.mapIndexed { i, img ->
            Page(i, "", img)
        }
    }

    override fun imageUrlParse(response: Response): String = throw Exception("Not used")

    private fun parseStatus(statusId: Int?) = when (statusId) {
        1 -> SManga.ONGOING
        3 -> SManga.ON_HIATUS
        4 -> SManga.COMPLETED
        5 -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    private class SortFilter : Filter.Sort(
        "Ordenar",
        arrayOf("Alfabético"),
        Selection(0, false),
    )

    private class GenreFilter(genres: List<Pair<String, Int>>) : UriPartFilter(
        "Género",
        arrayOf(
            Pair("Todos", 9999),
            *genres.toTypedArray(),
        ),
    )

    private class StatusFilter(statuses: List<Pair<String, Int>>) : UriPartFilter(
        "Estado",
        arrayOf(
            Pair("Todos", 9999),
            *statuses.toTypedArray(),
        ),
    )

    override fun getFilterList(): FilterList {
        val filters = mutableListOf<Filter<*>>(
            Filter.Header("Los filtros no funcionan en la búsqueda por texto"),
            Filter.Separator(),
            SortFilter(),
        )

        if (genresList.isNotEmpty() || statusesList.isNotEmpty()) {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Filtrar por género"),
                GenreFilter(genresList),
            )

            filters += listOf(
                Filter.Separator(),
                Filter.Header("Filtrar por estado"),
                StatusFilter(statusesList),
            )
        } else {
            filters += listOf(
                Filter.Separator(),
                Filter.Header("Presione 'Reiniciar' para intentar cargar los filtros"),
            )
        }

        return FilterList(filters)
    }

    private var genresList: List<Pair<String, Int>> = emptyList()
    private var statusesList: List<Pair<String, Int>> = emptyList()
    private var fetchFiltersAttemps = 0
    private var fetchFiltersFailed = false

    private fun fetchFilters() {
        if (fetchFiltersAttemps <= 3 && ((genresList.isEmpty() && statusesList.isEmpty()) || fetchFiltersFailed)) {
            val filters = runCatching {
                val response = client.newCall(GET("$apiBaseUrl/api/genres-statuses", headers)).execute()
                json.decodeFromString<GenresStatusesDto>(response.body.string())
            }

            fetchFiltersFailed = filters.isFailure
            genresList = filters.getOrNull()?.genres?.map { it.name.trim() to it.id } ?: emptyList()
            statusesList = filters.getOrNull()?.statuses?.map { it.name.trim() to it.id } ?: emptyList()
            fetchFiltersAttemps++
        }
    }

    open class UriPartFilter(displayName: String, val vals: Array<Pair<String, Int>>) :
        Filter.Select<String>(displayName, vals.map { it.first }.toTypedArray()) {
        fun toUriPart() = vals[state].second
    }
}
