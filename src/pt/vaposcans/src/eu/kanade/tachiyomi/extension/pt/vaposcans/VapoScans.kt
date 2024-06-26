package eu.kanade.tachiyomi.extension.pt.vaposcans

import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class VapoScans : HttpSource() {
    override val name = "Vapo Scans"

    override val baseUrl = "https://vaposcans.site"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2)
        .build()

    private val json: Json by injectLazy()

    // Keeps the behavior of the web page
    private val emptyPayload = "{}".toRequestBody()

    private var popularMangaCache: List<SManga> = mutableListOf()

    override fun headersBuilder() = super.headersBuilder()
        .set("Origin", baseUrl)
        .set("Referer", "$baseUrl/")

    override fun popularMangaRequest(page: Int) =
        POST("$apiUrl/api/series/", headers, emptyPayload)

    override fun popularMangaParse(response: Response) =
        MangasPage(
            response.parseAs<List<MangaDto>>()
                .map(::sMangaParse)
                .also {
                    popularMangaCache = it
                },
            false,
        )

    override fun latestUpdatesRequest(page: Int) =
        POST("$apiUrl/api/recent-chapters/", headers, emptyPayload)

    override fun latestUpdatesParse(response: Response) =
        MangasPage(
            response.parseAs<List<LatestMangaDto>>()
                .map { sMangaParse(it.mangaDto) },
            false,
        )

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(URL_SEARCH_PREFIX)) {
            val manga = SManga.create().apply {
                url = query.substringAfter(URL_SEARCH_PREFIX)
            }

            return fetchMangaDetails(manga).map {
                MangasPage(listOf(it), false)
            }
        }

        if (popularMangaCache.isNotEmpty()) {
            return Observable.just(findMangaByTitle(query))
        }

        return super.fetchSearchManga(page, query, filters)
    }

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) =
        POST("$apiUrl/api/series/#$query", headers, emptyPayload)

    override fun searchMangaParse(response: Response): MangasPage {
        val mangas = popularMangaParse(response).mangas
        val query = response.request.url.toString().substringAfter("#")
        return findMangaByTitle(query, mangas)
    }

    override fun getMangaUrl(manga: SManga): String = "$baseUrl/series/${manga.url}"

    override fun mangaDetailsRequest(manga: SManga): Request {
        val payload = MangaCode(manga.url).toRequestBody()
        return POST("$apiUrl/api/serie/", headers, payload)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        response.parseAs<MangaDetailsDto>().let {
            title = it.title
            description = it.synopsis
            url = it.code
            genre = it.genres.joinToString()
            artist = it.artist
            author = it.author
            thumbnail_url = it.cover
            status = when (it.status) {
                "completed" -> SManga.COMPLETED
                "ongoing" -> SManga.ONGOING
                else -> SManga.UNKNOWN
            }
        }
    }

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/reader/${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val payload = MangaCode(manga.url).toRequestBody()
        return POST("$apiUrl/api/serie/chapters/", headers, payload)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<List<ChapterDto>>().map {
            SChapter.create().apply {
                name = it.number
                url = it.code
                date_upload = parseDate(it.upload_date)
                chapter_number = it.number.toFloat()
            }
        }.sortedBy { it.chapter_number }.reversed()
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val payload = MangaCode(chapter.url).toRequestBody()
        return POST("$apiUrl/api/chapter_details/", headers, payload)
    }

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<PagesDto>()
        val chapterUrl = "$baseUrl/reader/${dto.chapter_code}"
        return dto.images.mapIndexed { index, image ->
            Page(index, chapterUrl, "$apiUrl/$image")
        }
    }

    override fun imageUrlParse(response: Response) = ""

    private fun findMangaByTitle(query: String, collection: List<SManga> = popularMangaCache): MangasPage {
        val mangas = collection
            .filter { it.title.contains(query, ignoreCase = true) }

        return MangasPage(mangas, false)
    }

    private inline fun <reified T> Response.parseAs(): T =
        json.decodeFromString(body.string())

    private inline fun <reified T : Any> T.toRequestBody(): RequestBody =
        json.encodeToString(this)
            .toRequestBody(JSON_MEDIA_TYPE)

    private fun sMangaParse(dto: MangaDto) = SManga.create().apply {
        title = dto.title
        thumbnail_url = "$apiUrl/${dto.cover}"
        url = dto.code
    }

    private fun parseDate(date: String): Long =
        try { dateFormat.parse(date)!!.time } catch (_: Exception) { parseRelativeDate(date) }

    private fun parseRelativeDate(date: String): Long {
        val number = RELATIVE_DATE_REGEX.find(date)?.value?.toIntOrNull() ?: return 0
        val cal = Calendar.getInstance()
        return when {
            date.contains("dia", ignoreCase = true) -> cal.apply { add(Calendar.DATE, -number) }.timeInMillis
            date.contains("mes", ignoreCase = true) -> cal.apply { add(Calendar.MONTH, -number) }.timeInMillis
            date.contains("ano", ignoreCase = true) -> cal.apply { add(Calendar.YEAR, -number) }.timeInMillis
            else -> 0
        }
    }

    companion object {
        const val apiUrl = "https://api.vaposcans.site"
        const val URL_SEARCH_PREFIX = "slug:"
        val JSON_MEDIA_TYPE = "application/json".toMediaTypeOrNull()
        val RELATIVE_DATE_REGEX = """(\d+)""".toRegex()

        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("pt", "BR"))
    }
}
