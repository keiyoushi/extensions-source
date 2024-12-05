package eu.kanade.tachiyomi.extension.pt.sussytoons

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimit
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response
import org.jsoup.Jsoup
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat
import java.util.concurrent.TimeUnit

class SussyToons : HttpSource() {

    override val name = "Sussy Toons"

    override val baseUrl = "https://new.sussytoons.site"

    private val apiUrl = "https://api.sussytoons.site"

    override val lang = "pt-BR"

    override val supportsLatest = true

    private val json: Json by injectLazy()

    override val client = network.cloudflareClient.newBuilder()
        .rateLimit(2, 3, TimeUnit.SECONDS) // Avoid a Bad Gateway
        .callTimeout(3, TimeUnit.MINUTES)
        .readTimeout(3, TimeUnit.MINUTES) // Source is too slow, and returns some large JSONs
        .addInterceptor(::imageLocation)
        .build()

    // ============================= Popular ==================================

    override fun popularMangaRequest(page: Int) = GET("$apiUrl/obras/top5", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val dto = response.parseAs<WrapperDto<List<MangaDto>>>()
        val mangas = dto.results.map(::toSManga)
        return MangasPage(mangas, dto.hasNextPage())
    }

    private fun toSManga(dto: MangaDto) = SManga.create().apply {
        title = dto.name
        thumbnail_url = dto.getThumbnailUrl()
        Jsoup.parseBodyFragment(dto.description).let { description = it.text() }
        url = "/obras/${dto.id}/${dto.slug}#${dto.scanId}"
        initialized = true
        status = when (dto.status.lowercase()) {
            "ativo" -> SManga.ONGOING
            "completo" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    private fun MangaDto.getThumbnailUrl(): String {
        val baseImageUrl = when {
            thumbnail.contains("uploads", true) -> "$CDN_URL/wp-content"
            else -> "$CDN_URL/scans/$scanId/obras/$id"
        }
        return "$baseImageUrl/$thumbnail"
    }

    // ============================= Latest ===================================

    override fun latestUpdatesRequest(page: Int): Request {
        val url = "$apiUrl/obras/novo-capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("limite", "24")
            .addQueryParameter("scan_id", "1")
            .build()
        return GET(url, headers)
    }

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================= Search ===================================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        val url = "$apiUrl/obras".toHttpUrl().newBuilder()
            .addQueryParameter("string", query)
            .addQueryParameter("limite", "8")
            .addQueryParameter("pagina", page.toString())
            .addQueryParameter("scan_id", "1")
            .build()
        return GET(url, headers)
    }

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    // ============================= Details ==================================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga) =
        GET("$apiUrl${manga.url.substringBeforeLast("/")}", headers)

    override fun mangaDetailsParse(response: Response) =
        toSManga(response.parseAs<WrapperDto<MangaDto>>().results)

    // ============================= Chapters =================================

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl/${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val scanId = manga.url.substringAfter("#")
        val url = "$apiUrl/obras/${manga.getId()}/capitulos".toHttpUrl().newBuilder()
            .addQueryParameter("limite", "5000")
            .addQueryParameter("scan_id", scanId)
            .build()

        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        return response.parseAs<WrapperDto<List<ChapterDto>>>().results.map {
            SChapter.create().apply {
                name = it.name
                chapter_number = it.chapterNumber
                url = "/capitulos/${it.id}"
                date_upload = it.updateAt.toDate()
            }
        }
    }

    private fun SManga.getId(): String {
        val pathSegment = url.split("/")
        return pathSegment[pathSegment.size - 2]
    }

    // ============================= Pages ====================================

    override fun pageListRequest(chapter: SChapter) = GET("$apiUrl${chapter.url}")

    override fun pageListParse(response: Response): List<Page> {
        val dto = response.parseAs<WrapperPageDto>().result
        return dto.pages.filter { it.src.contains('.') }.mapIndexed { index, image ->
            val imageUrl = if (image.mime == null) {
                "$CDN_URL/scans/${dto.series.scanId}/obras/${dto.series.id}/capitulos/${dto.chapterNumber}/${image.src}"
            } else {
                "$CDN_URL/wp-content/uploads/WP-manga/data/${image.src}"
            }

            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    override fun imageUrlRequest(page: Page): Request {
        val imageHeaders = headers.newBuilder()
            .add("Referer", "$baseUrl/")
            .build()
        return GET(page.url, imageHeaders)
    }

    // ============================= Utilities ====================================

    private fun imageLocation(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        if (response.isSuccessful) {
            return response
        }

        val url = request.url.toString()
        if (url.contains(CDN_URL, ignoreCase = true)) {
            response.close()

            val newRequest = request.newBuilder()
                .url(url.replace(CDN_URL, "$apiUrl/storage", ignoreCase = true))
                .build()

            return chain.proceed(newRequest)
        }
        return response
    }

    private inline fun <reified T> Response.parseAs(): T {
        return json.decodeFromStream(body.byteStream())
    }

    private fun String.toDate() =
        try { dateFormat.parse(this)!!.time } catch (_: Exception) { 0L }

    companion object {
        const val CDN_URL = "https://cdn.sussytoons.site"

        @SuppressLint("SimpleDateFormat")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    }
}
