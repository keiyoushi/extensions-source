package eu.kanade.tachiyomi.extension.tr.alucardscans

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import eu.kanade.tachiyomi.util.asJsoup
import kotlinx.serialization.decodeFromString
import okhttp3.Headers
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Alucardscans : HttpSource() {
    override val name = "Alucard Scans"
    override val baseUrl = "https://alucardscans.com"
    override val lang = "tr"
    override val supportsLatest = true

    private val alucardApi = "api/series?sort=views&order=desc&calculateTotalViews=true&page=SAYFA&limit=24"
    private val latestApi = "api/chapters/latest?page=SAYFA&limit=10"

    override fun headersBuilder(): Headers.Builder = super.headersBuilder()
        .add("Referer", "$baseUrl/")

    private fun parseDate(dateStr: String?): Long {
        return runCatching {
            val cleanDate = dateStr!!.replace("\$D", "")
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = java.util.TimeZone.getTimeZone("UTC")
            }.parse(cleanDate)!!.time
        }.getOrDefault(0L)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val html = response.body.string()

        val chaptersRegex = """initialChapters\\":\s*(\[.*?])\s*\}\s*""".toRegex()
        val match = chaptersRegex.find(html) ?: throw Exception("Bölüm listesi bulunamadı")

        var chaptersJson = match.groupValues[1]

        chaptersJson = chaptersJson.replace("""\"""", "\"").replace("""\\/""", "/")

        val chapters = json.decodeFromString<List<AluChapters>>(chaptersJson)

        return chapters.map { chapter ->
            SChapter.create().apply {
                name = "Bölüm ${chapter.number}${if (chapter.title.isNullOrBlank()) "" else " - ${chapter.title}"}"
                url = "/${chapter.slug}"
                date_upload = parseDate(chapter.releaseDate)
            }
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun latestUpdatesParse(response: Response): MangasPage {
        val responseBody = response.body.string()
        val cevap = json.decodeFromString<AlucardResponse>(responseBody)

        val smanga = (
            cevap.groupedChapters?.map { it.series!! }
            )
            ?.distinctBy { it.slug }
            ?.map { series ->
                SManga.create().apply {
                    this.title = series.title!!
                    this.url = "/manga/${series.slug}"
                    this.thumbnail_url = series.coverImage?.let {
                        if (it.startsWith("/")) "$baseUrl$it" else it
                    }
                    this.status = series.status?.parseStatus() ?: SManga.UNKNOWN
                }
            }

        val hasNextPage = cevap.pagination?.let {
            (it.page ?: 1) < (it.pages ?: 1)
        } ?: false

        return MangasPage(smanga!!, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/${latestApi.replace("SAYFA","$page")}", headers)

    override fun mangaDetailsParse(response: Response): SManga {
        val html = response.asJsoup()
        val scriptData = html.select("script").find { it.data().contains("initialSeries") }?.data()
            ?: throw Exception("Script verisi bulunamadı")

        val seriesRegex = """initialSeries\\":\s*(\{.*?\})\s*,\s*\\"initialChapters""".toRegex()

        val match = seriesRegex.find(scriptData) ?: """initialSeries\\":\s*(\{.*?\})\s*,""".toRegex().find(scriptData)

        var seriesJson = match?.groupValues?.get(1)
            ?: throw Exception("JSON Regex ile bulunamadı")

        seriesJson = seriesJson.replace("""\"""", "\"").replace("""\\n""", "\n")

        val item = json.decodeFromString<AluSeries>(seriesJson)

        return SManga.create().apply {
            title = item.title ?: ""
            description = item.description?.trim()
            genre = item.genres?.joinToString(", ")
            status = item.status?.parseStatus() ?: SManga.UNKNOWN
            thumbnail_url = (item.coverImage ?: item.cover)?.let {
                if (it.startsWith("/")) "$baseUrl$it" else it
            }
        }
    }

    override fun pageListParse(response: Response): List<Page> {
        val html = response.asJsoup()
        val images = html.select("div.w-full.flex-col.items-center img")

        return images.mapIndexed { index, element ->
            val imageUrl = element.attr("abs:src").ifEmpty {
                element.attr("abs:data-src")
            }

            Page(index, imageUrl = imageUrl)
        }
    }

    override fun popularMangaParse(response: Response): MangasPage {
        val responseBody = response.body.string()
        val cevap = json.decodeFromString<AlucardResponse>(responseBody)

        val mangas = cevap.series?.map { series ->
            SManga.create().apply {
                this.url = "/manga/${series.slug}"

                this.title = series.title!!
                this.thumbnail_url = series.coverImage?.let {
                    if (it.startsWith("/")) "$baseUrl$it" else it
                }
                this.status = series.status?.parseStatus() ?: SManga.UNKNOWN
            }
        }

        val hasNextPage = cevap.pagination?.let {
            (it.page ?: 1) < (it.pages ?: 1)
        } ?: false

        return MangasPage(mangas!!, hasNextPage)
    }

    private fun String.parseStatus(): Int {
        return when (this.lowercase()) {
            in statusOngoing -> SManga.ONGOING
            in statusCompleted -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private val statusOngoing = listOf("ongoing", "devam ediyor")
    private val statusCompleted = listOf("complete", "tamamlandı", "bitti")

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/${alucardApi.replace("SAYFA","$page")}", headers)

    override fun searchMangaParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        return GET("$baseUrl/api/series?search=$query&page=$page&limit=24", headers)
    }
}
