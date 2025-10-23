package eu.kanade.tachiyomi.extension.en.alternativescans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import rx.Observable
import java.text.SimpleDateFormat
import java.util.Locale

class AlternativeScans : HttpSource() {

    override val name = "Alternative Scans"
    override val baseUrl = "https://alternativescans.icu"
    private val apiUrl = "https://api.alternativescans.icu"
    override val lang = "en"
    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    override fun headersBuilder() = super.headersBuilder()
        .set("Referer", "$baseUrl/")

    // Popular
    override fun popularMangaRequest(page: Int): Request {
        return GET("$apiUrl/api/admin/getLatestUpdate", apiHeaders)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<ApiResponse>(response.body.string())
        val mangas = result.latestReleases
            .distinctBy { it.manga }
            .map {
                SManga.create().apply {
                    title = it.title
                    thumbnail_url = it.seriesThumbnail
                    url = "/manga/${it.nick}?id=${it.manga}"
                }
            }
        return MangasPage(mangas, false)
    }

    // Latest
    override fun latestUpdatesRequest(page: Int): Request = popularMangaRequest(page)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // Search
    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> = Observable.error(UnsupportedOperationException("Search not yet implemented."))
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = throw UnsupportedOperationException("Search not yet implemented.")
    override fun searchMangaParse(response: Response): MangasPage = throw UnsupportedOperationException("Search not yet implemented.")

    // Details
    override fun mangaDetailsRequest(manga: SManga): Request {
        val nick = manga.url.substringAfter("/manga/").substringBefore("?")
        val seriesId = manga.url.substringAfter("?id=")
        return GET("$apiUrl/api/admin/getSeriesDetails/$seriesId/$nick", apiHeaders)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = json.decodeFromString<MangaDetailsResponse>(response.body.string())
        return SManga.create().apply {
            title = result.seriesDetails.title
            description = result.seriesDetails.desc
            author = result.seriesDetails.author
            artist = result.seriesDetails.artist
            genre = result.seriesDetails.genre
            status = parseStatus(result.seriesDetails.manga_status)
            thumbnail_url = result.seriesDetails.thumbnail
        }
    }

    private fun parseStatus(status: String?): Int {
        return when (status?.lowercase()) {
            "releasing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // Chapters
    override fun chapterListRequest(manga: SManga): Request {
        val nick = manga.url.substringAfter("/manga/").substringBefore("?")
        val seriesId = manga.url.substringAfter("?id=")
        return GET("$apiUrl/api/admin/getSeriesDetails/$seriesId/$nick", apiHeaders)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.decodeFromString<MangaDetailsResponse>(response.body.string())
        val nick = response.request.url.pathSegments.last()
        val seriesId = response.request.url.pathSegments[response.request.url.pathSegments.size - 2]
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        return result.releases.map {
            SChapter.create().apply {
                name = "Chapter ${it.chapterNo}" + (it.chapterName?.let { ": $it" } ?: "")
                url = "/reader.html?id=$seriesId&series=$nick&chapter=${it.chapterNo}"
                date_upload = try { dateFormat.parse(it.uploadDate)?.time ?: 0L } catch (e: Exception) { 0L }
                chapter_number = it.chapterNo.let { chapterNum ->
                    val numbers = Regex("(\\d+\\.?\\d*)").findAll(chapterNum).map { it.value }.toList()
                    numbers.lastOrNull()?.toFloatOrNull() ?: -1f
                }
            }
        }.sortedByDescending { it.chapter_number }
    }

    // Pages
    override fun pageListRequest(chapter: SChapter): Request {
        val url = chapter.url
        val seriesId = url.substringAfter("id=").substringBefore("&")
        val seriesName = url.substringAfter("series=").substringBefore("&")
        val chapterNo = url.substringAfter("chapter=")
        return GET("$apiUrl/api/admin/getSeries/$seriesId/$seriesName/$chapterNo", apiHeaders)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<PageListResponse>(response.body.string())
        return result.resources.mapIndexed { index, imageUrl ->
            Page(index, "", imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")

    // DTOs
    @Serializable
    private data class ApiResponse(val latestReleases: List<LatestRelease>)

    @Serializable
    private data class LatestRelease(
        val title: String,
        val seriesThumbnail: String,
        val manga: Int,
        val nick: String,
    )

    @Serializable
    data class MangaDetailsResponse(
        val seriesDetails: Series,
        val releases: List<ApiChapter>,
    )

    @Serializable
    data class Series(
        val title: String,
        val desc: String,
        val author: String? = null,
        val artist: String? = null,
        val genre: String? = null,
        val manga_status: String? = null,
        val thumbnail: String,
    )

    @Serializable
    data class ApiChapter(
        val chapterNo: String,
        val chapterName: String? = null,
        val uploadDate: String,
    )

    @Serializable
    data class PageListResponse(
        val resources: List<String>,
    )
}
