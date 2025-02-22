package eu.kanade.tachiyomi.extension.pt.blackscans

import android.annotation.SuppressLint
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.POST
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okio.Buffer
import rx.Observable
import uy.kohesive.injekt.injectLazy
import java.text.SimpleDateFormat

class BlackScans : HttpSource() {

    override val name = "Black Scans"

    override val baseUrl = "https://blackscans.site"

    override val lang = "pt-BR"

    override val supportsLatest = true

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(API_URL.toHttpUrl(), 2)
        .build()

    private val json: Json by injectLazy()

    // ============================== Popular ==============================

    override fun popularMangaRequest(page: Int) = GET("$API_URL/api/series/", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val mangas = response.parseAs<List<MangaDto>>().map { manga ->
            SManga.create().apply {
                title = manga.title
                thumbnail_url = "$API_URL/media/${manga.cover}"
                url = "/series/${manga.code}"
            }
        }
        return MangasPage(mangas, false)
    }

    // ============================== Latest ==============================

    override fun latestUpdatesRequest(page: Int) = GET("$API_URL/api/series/updates/", headers)

    override fun latestUpdatesParse(response: Response) = popularMangaParse(response)

    // ============================== Search ==============================

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList) = popularMangaRequest(page)

    override fun searchMangaParse(response: Response) = popularMangaParse(response)

    override fun fetchSearchManga(page: Int, query: String, filters: FilterList): Observable<MangasPage> {
        if (query.startsWith(PREFIX_SEARCH)) {
            val mangaCode = query.substringAfter(PREFIX_SEARCH)
            return fetchMangaDetails(SManga.create().apply { url = "/series/$mangaCode" })
                .map { manga -> MangasPage(listOf(manga), false) }
        }

        return super.fetchSearchManga(page, query, filters).map { mangasPage ->
            val mangas = mangasPage.mangas.filter { manga -> manga.title.contains(query, true) }
            mangasPage.copy(mangas)
        }
    }

    // ============================== Details =============================

    override fun getMangaUrl(manga: SManga) = "$baseUrl${manga.url}"

    override fun mangaDetailsRequest(manga: SManga) =
        POST("$API_URL/api/serie/", headers, manga.createPostPayload())

    override fun mangaDetailsParse(response: Response): SManga {
        return response.parseAs<MangaDetailsDto>().let { dto ->
            SManga.create().apply {
                title = dto.title
                description = dto.synopsis
                thumbnail_url = "$API_URL/media/${dto.cover}"
                author = dto.author
                artist = dto.artist
                genre = dto.genres.joinToString()
                url = "/series/${dto.code}"
                status = dto.status.toMangaStatus()
            }
        }
    }

    private fun String.toMangaStatus(): Int {
        return when (this.lowercase()) {
            "ongoing" -> SManga.ONGOING
            "completed" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    // ============================== Chapters ============================

    override fun getChapterUrl(chapter: SChapter) = "$baseUrl${chapter.url}"

    override fun chapterListRequest(manga: SManga): Request {
        val payload = manga.createPostPayload("series_code")
        return POST("$API_URL/api/series/chapters/", headers, payload)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val series = response.request.body!!.parseAs<SeriesDto>()

        return response.parseAs<ChapterList>().chapters.map { chapter ->
            SChapter.create().apply {
                name = chapter.name
                date_upload = chapter.uploadAt.toDate()
                url = "/series/${series.code}/${chapter.code}"
            }
        }
    }

    // ============================== Pages ===============================

    override fun imageUrlParse(response: Response) = ""

    override fun pageListRequest(chapter: SChapter): Request {
        val chapterCode = chapter.url.substringAfterLast("/")
        val payload = """{"chapter_code":"$chapterCode"}"""
            .toRequestBody("application/json".toMediaType())
        return POST("$API_URL/api/chapter/info/", headers, payload)
    }

    override fun pageListParse(response: Response): List<Page> {
        return response.parseAs<PagesDto>().images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = "$API_URL//media/$imageUrl")
        }
    }

    // ============================== Utils ===============================

    @Serializable
    private class SeriesDto(@SerialName("series_code") val code: String)

    private fun SManga.createPostPayload(field: String = "code"): RequestBody {
        val mangaCode = url.substringAfterLast("/")
        return """{"$field": "$mangaCode"}""".toRequestBody("application/json".toMediaType())
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }
    private inline fun <reified T> RequestBody.parseAs(): T =
        json.decodeFromString(Buffer().also { writeTo(it) }.readUtf8())

    private fun String.toDate() =
        try { dateFormat.parse(this)!!.time } catch (_: Exception) { 0 }

    companion object {
        const val API_URL = "https://api.blackscans.site"
        const val PREFIX_SEARCH = "id:"

        @SuppressLint("SimpleDateFormat")
        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS'Z'")
    }
}
