package eu.kanade.tachiyomi.extension.fr.perfscan

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Locale

class PerfScan : HttpSource() {
    override val name = "Perf Scan"
    override val baseUrl = "https://perf-scan.xyz"
    private val apiUrl = "https://api.perf-scan.xyz"
    override val lang = "fr"
    override val supportsLatest = true
    override val versionId = 2

    override val client: OkHttpClient = network.cloudflareClient

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
    private val chapterNumberFormat = DecimalFormat("#.##")

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")
        .add("Origin", baseUrl)

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("ranking", "POPULAR")
            .addQueryParameter("rankingType", "YEARLY")
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("take", "24")
            .build()
        return GET(url, headers)
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val result = response.parseAs<PerfScanResponse<List<PerfScanSeries>>>()
        val mangaList = result.data.map { series ->
            SManga.create().apply {
                url = "/series/${series.slug}"
                title = series.title
                thumbnail_url = "$apiUrl/cdn/${series.thumbnail}"
            }
        }
        val hasNextPage = result.data.size == 24
        return MangasPage(mangaList, hasNextPage)
    }

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("page", page.toString())
            .addQueryParameter("take", "24")
            .addQueryParameter("latestUpdate", "true")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/series".toHttpUrl().newBuilder()
            .addQueryParameter("type", "COMIC")
            .addQueryParameter("title", query)
            .addQueryParameter("page", page.toString())
            .addQueryParameter("take", "24")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun getMangaUrl(manga: SManga): String = baseUrl + manga.url

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val slug = manga.url.substringAfterLast("/")
        return GET("$apiUrl/series/$slug", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = response.parseAs<PerfScanResponse<PerfScanSeriesDetails>>()
        val series = result.data
        return SManga.create().apply {
            url = "/series/${series.slug}"
            title = series.title
            author = series.author
            artist = series.artist
            description = series.description
            status = parseStatus(series.statusObject?.name)
            genre = series.seriesGenre.joinToString { it.genre.name }
            thumbnail_url = "$apiUrl/cdn/${series.thumbnail}"
            initialized = true
        }
    }

    private fun parseStatus(status: String?): Int = when (status?.lowercase()) {
        "en cours" -> SManga.ONGOING
        "terminé" -> SManga.COMPLETED
        "en pause" -> SManga.ON_HIATUS
        "annulé" -> SManga.CANCELLED
        else -> SManga.UNKNOWN
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request = mangaDetailsRequest(manga)

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<PerfScanResponse<PerfScanSeriesDetails>>()
        val seriesSlug = result.data.slug
        return result.data.chapters
            .sortedByDescending { it.index }
            .map { chapter ->
                SChapter.create().apply {
                    val chapterIndex = chapterNumberFormat.format(chapter.index)
                    url = "/series/$seriesSlug/chapter/$chapterIndex"
                    name = "Chapitre $chapterIndex" + if (!chapter.title.isNullOrEmpty() && chapter.title != "-") " - ${chapter.title}" else ""
                    date_upload = dateFormat.tryParse(chapter.createdAt)
                    scanlator = chapter.season.name
                }
            }
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request = GET(apiUrl + chapter.url, headers)

    override fun pageListParse(response: Response): List<Page> {
        val result = response.parseAs<PerfScanResponse<PerfScanPageList>>()
        return result.data.content.mapIndexed { index, image ->
            Page(index, imageUrl = "$apiUrl/cdn/${image.value}")
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException("Not used.")
}
