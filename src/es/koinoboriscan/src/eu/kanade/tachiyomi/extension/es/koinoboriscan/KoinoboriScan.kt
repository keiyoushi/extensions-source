package eu.kanade.tachiyomi.extension.es.koinoboriscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.asObservableSuccess
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.math.min

class KoinoboriScan : HttpSource() {

    // Site change theme from Madara to custom
    override val versionId = 2

    override val name = "Koinobori Scan"

    override val lang = "es"

    override val baseUrl = "https://visorkoi.com"

    private val apiBaseUrl = "https://api.visorkoi.com"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ", Locale("es"))

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2, 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request =
        GET("$apiBaseUrl/topSeries", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = json.decodeFromString<List<SeriesDto>>(response.body.string())
            .map { it.toSManga(apiBaseUrl) }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$apiBaseUrl/lastupdates", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = json.decodeFromString<List<SeriesDto>>(response.body.string())
            .map { it.toSManga(apiBaseUrl) }

        return MangasPage(mangas, false)
    }

    private val seriesList = mutableListOf<SeriesDto>()

    override fun fetchSearchManga(
        page: Int,
        query: String,
        filters: FilterList,
    ): Observable<MangasPage> {
        return if (seriesList.isEmpty()) {
            client.newCall(searchMangaRequest(page, query, filters))
                .asObservableSuccess()
                .map { searchMangaParse(it, page, query) }
        } else {
            Observable.just(parseSeriesList(page, query))
        }
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        GET("$apiBaseUrl/all", headers)

    private fun searchMangaParse(response: Response, page: Int, query: String): MangasPage {
        val result = json.decodeFromString<List<SeriesDto>>(response.body.string())
        seriesList.addAll(result)
        return parseSeriesList(page, query)
    }

    private fun parseSeriesList(page: Int, query: String): MangasPage {
        val filteredSeries = seriesList.filter {
            it.title.contains(query, ignoreCase = true)
        }

        val mangas = filteredSeries.subList(
            (page - 1) * SERIES_PER_PAGE,
            min(page * SERIES_PER_PAGE, filteredSeries.size),
        ).map { it.toSManga(apiBaseUrl) }

        val hasNextPage = filteredSeries.size > page * SERIES_PER_PAGE

        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Presione 'Filtrar' para mostrar toda la biblioteca"),
    )

    override fun getMangaUrl(manga: SManga) = "$baseUrl/?tipo=serie&identificador=${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET("$apiBaseUrl/api/project/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        return json.decodeFromString<SeriesDto>(response.body.string()).toSMangaDetails(apiBaseUrl)
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/?tipo=capitulo&identificador=${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request =
        GET("$apiBaseUrl/api/project/${manga.url}", headers)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.decodeFromString<ChaptersPayloadDto>(response.body.string())
        return result.seasons.flatMap { season ->
            season.chapters.map { chapter ->
                SChapter.create().apply {
                    url = chapter.id.toString()
                    name = "Capítulo ${chapter.name}: ${chapter.title}"
                    date_upload = try {
                        dateFormat.parse(chapter.date)?.time ?: 0
                    } catch (e: Exception) {
                        0
                    }
                }
            }
        }.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$apiBaseUrl/api/chapter/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<PagesPayloadDto>(response.body.string())
        val key = result.key
        val chapterId = result.chapter.id
        return result.chapter.images.mapIndexed { i, img ->
            Page(i, imageUrl = "$apiBaseUrl/api/images/chapter/$chapterId/$img?token=$key")
        }
    }

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    companion object {
        const val SERIES_PER_PAGE = 24
    }
}
