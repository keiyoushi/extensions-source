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
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import kotlin.math.min

class KoinoboriScan : HttpSource() {

    override val versionId = 3

    override val name = "Koinobori Scan"

    override val lang = "es"

    override val baseUrl = "https://visorkoi.com"

    private val apiBaseUrl = "https://api.visorkoi.com"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale("es")).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2, 1)
        .build()

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int): Request =
        GET("$apiBaseUrl/api/topSeries", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<TopSeriesDto>(response.body.string())
        val mangas = (result.mensualRes + result.weekRes + result.dayRes)
            .distinctBy { it.slug }
            .map { it.toSManga() }

        return MangasPage(mangas, false)
    }

    override fun latestUpdatesRequest(page: Int): Request =
        GET("$apiBaseUrl/api/lastupdates", headers)

    override fun latestUpdatesParse(response: Response): MangasPage {
        val mangas = json.decodeFromString<List<SeriesDto>>(response.body.string())
            .map { it.toSManga() }

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
        GET("$apiBaseUrl/api/allComics", headers)

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
        ).map { it.toSManga() }

        val hasNextPage = filteredSeries.size > page * SERIES_PER_PAGE

        return MangasPage(mangas, hasNextPage)
    }

    override fun getFilterList() = FilterList(
        Filter.Header("Presione 'Filtrar' para mostrar toda la biblioteca"),
    )

    override fun getMangaUrl(manga: SManga) = "$baseUrl/comic/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request =
        GET("$baseUrl/comic/${manga.url}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val document = response.asJsoup()
        val scriptsData = document.select("script").joinToString("\n") { it.data() }
        val jsonData = MANGA_DETAILS_REGEX.find(scriptsData)?.groupValues?.get(1)
            ?: throw Exception("No se pudo obtener la información de la serie")
        return json.decodeFromString<SeriesDto>(jsonData.unescape()).toSMangaDetails()
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/comic/${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val document = response.asJsoup()
        val scriptsData = document.select("script").joinToString("\n") { it.data() }
        val jsonData = MANGA_DETAILS_REGEX.find(scriptsData)?.groupValues?.get(1)
            ?: throw Exception("No se pudo obtener la información de la serie")
        val result = json.decodeFromString<ChaptersPayloadDto>(jsonData.unescape())
        val seriesSlug = result.seriesSlug
        return result.seasons.flatMap { season ->
            season.chapters.map { chapter ->
                SChapter.create().apply {
                    url = "$seriesSlug/${chapter.slug}"
                    name = chapter.name
                    if (!chapter.title.isNullOrBlank()) {
                        name += ": ${chapter.title}"
                    }
                    date_upload = try {
                        dateFormat.parse(chapter.date)?.time ?: 0
                    } catch (e: Exception) {
                        0
                    }
                }
            }
        }
    }

    override fun pageListRequest(chapter: SChapter): Request =
        GET("$baseUrl/comic/${chapter.url}", headers)

    override fun pageListParse(response: Response): List<Page> {
        val document = response.asJsoup()
        return document.select("body > div.w-full > div > img").mapIndexed { i, img ->
            Page(i, imageUrl = img.attr("abs:src"))
        }
    }

    override fun searchMangaParse(response: Response) = throw UnsupportedOperationException()
    override fun imageUrlParse(response: Response) = throw UnsupportedOperationException()

    private fun String.unescape(): String {
        return UNESCAPE_REGEX.replace(this, "$1")
    }

    companion object {
        const val SERIES_PER_PAGE = 24
        val UNESCAPE_REGEX = """\\(.)""".toRegex()
        val MANGA_DETAILS_REGEX = """self\.__next_f\.push\(.*info\\":(\{.*Chapter.*\}).*\\"userIsFollowed""".toRegex()
    }
}
