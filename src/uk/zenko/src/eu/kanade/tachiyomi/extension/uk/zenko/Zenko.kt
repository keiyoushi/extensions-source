package eu.kanade.tachiyomi.extension.uk.zenko

import eu.kanade.tachiyomi.extension.uk.zenko.dtos.ChapterResponseItem
import eu.kanade.tachiyomi.extension.uk.zenko.dtos.MangaDetailsResponse
import eu.kanade.tachiyomi.extension.uk.zenko.dtos.ZenkoMangaListResponse
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.network.interceptor.rateLimitHost
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import okhttp3.Response
import uy.kohesive.injekt.injectLazy

class Zenko : HttpSource() {
    override val name = "Zenko"
    override val baseUrl = "https://zenko.online"
    override val lang = "uk"
    override val supportsLatest = true

    override fun headersBuilder() = super.headersBuilder()
        .add("Origin", "$baseUrl")
        .add("Referer", "$baseUrl/")

    override val client = network.cloudflareClient.newBuilder()
        .rateLimitHost(API_URL.toHttpUrl(), 10)
        .build()

    override fun getMangaUrl(manga: SManga): String {
        return "$baseUrl${manga.url}"
    }

    override fun getChapterUrl(chapter: SChapter): String {
        return "$baseUrl${chapter.url}"
    }

    // ============================== Popular ===============================
    override fun popularMangaRequest(page: Int): Request {
        val offset = offsetCounter(page)
        return makeZenkoMangaRequest(offset, "viewsCount")
    }

    override fun popularMangaParse(response: Response) = parseAsMangaResponseDto(response)

    // =============================== Latest ===============================
    override fun latestUpdatesRequest(page: Int): Request {
        val offset = offsetCounter(page)
        return makeZenkoMangaRequest(offset, "lastChapterCreatedAt")
    }

    override fun latestUpdatesParse(response: Response) = parseAsMangaResponseDto(response)

    // =============================== Search ===============================
    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request {
        if (query.length >= 2) {
            val offset = offsetCounter(page)
            val url = "$API_URL/titles".toHttpUrl().newBuilder()
                .addQueryParameter("limit", "15")
                .addQueryParameter("offset", offset.toString())
                .addQueryParameter("name", query)
                .build()
            return GET(url, headers)
        } else {
            throw UnsupportedOperationException("Запит має містити щонайменше 2 символи / The query must contain at least 2 characters")
        }
    }

    override fun searchMangaParse(response: Response) = parseAsMangaResponseDto(response)

    // =========================== Manga Details ============================
    override fun mangaDetailsRequest(manga: SManga): Request {
        val mangaId = "$baseUrl${manga.url}".toHttpUrl().pathSegments.last()
        val url = "$API_URL/titles/$mangaId"
        return GET(url, headers)
    }

    override fun mangaDetailsParse(response: Response) = SManga.create().apply {
        val mangaDto = response.parseAs<MangaDetailsResponse>()
        setUrlWithoutDomain("/titles/${mangaDto.id}")
        title = mangaDto.engName ?: mangaDto.name
        thumbnail_url = buildImageUrl(mangaDto.coverImg)
        description = "${mangaDto.name}\n${mangaDto.description}"
        genre = mangaDto.genres!!.joinToString { it.name }
        author = mangaDto.author!!.username
        status = mangaDto.status.toStatus()
    }

    // ============================== Chapters ==============================
    override fun chapterListRequest(manga: SManga): Request {
        val mangaId = "$baseUrl${manga.url}".toHttpUrl().pathSegments.last()
        val url = "$API_URL/titles/$mangaId/chapters"
        return GET(url, headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = response.parseAs<List<ChapterResponseItem>>()
        return result.sortedByDescending { item ->
            val id = StringProcessor.generateId(item.name)
            if (id > 0) id else item.id.toDouble()
        }.map { chapterResponseItem ->
            SChapter.create().apply {
                setUrlWithoutDomain("/titles/${chapterResponseItem.titleId}/${chapterResponseItem.id}")
                name = StringProcessor.format(chapterResponseItem.name)
                date_upload = chapterResponseItem.createdAt!!.secToMs()
                scanlator = chapterResponseItem.publisher!!.name
            }
        }
    }

    // =============================== Pages ================================
    override fun pageListRequest(chapter: SChapter): Request {
        val chapterId = "$baseUrl${chapter.url}".toHttpUrl().pathSegments.last()
        val url = "$API_URL/chapters/$chapterId"
        return GET(url, headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val data = response.parseAs<ChapterResponseItem>()
        return data.pages!!.map { page ->
            Page(page.id, imageUrl = "$IMAGE_STORAGE_URL/${page.content}")
        }
    }

    override fun imageUrlParse(response: Response): String = ""

    // ============================= Utilities ==============================
    private fun parseAsMangaResponseDto(response: Response): MangasPage {
        val zenkoMangaListResponse = response.parseAs<ZenkoMangaListResponse>()
        return makeMangasPage(zenkoMangaListResponse.data, zenkoMangaListResponse.meta.hasNextPage)
    }

    private fun offsetCounter(page: Int) = (page - 1) * 15

    private fun makeZenkoMangaRequest(offset: Int, sortBy: String): Request {
        val url = "$API_URL/titles".toHttpUrl().newBuilder()
            .addQueryParameter("limit", "15")
            .addQueryParameter("offset", offset.toString())
            .addQueryParameter("sortBy", sortBy)
            .addQueryParameter("order", "DESC")
            .build()
        return GET(url, headers)
    }

    private fun makeMangasPage(
        mangaList: List<MangaDetailsResponse>,
        hasNextPage: Boolean = false,
    ): MangasPage {
        return MangasPage(
            mangaList.map(::makeSManga),
            hasNextPage,
        )
    }

    private fun makeSManga(mangaDto: MangaDetailsResponse) = SManga.create().apply {
        setUrlWithoutDomain("/titles/${mangaDto.id}")
        title = mangaDto.engName ?: mangaDto.name
        thumbnail_url = buildImageUrl(mangaDto.coverImg)
        status = mangaDto.status.toStatus()
    }

    private fun String.toStatus(): Int {
        val status = this.lowercase()
        return when (status) {
            "ongoing" -> SManga.ONGOING
            "finished" -> SManga.COMPLETED
            "paused" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }

    private fun Long.secToMs(): Long {
        return this * 1000
    }

    private fun buildImageUrl(imageId: String): String {
        val url = "$IMAGE_STORAGE_URL/$imageId".toHttpUrl().newBuilder()
            .addQueryParameter("optimizer", "image")
            .addQueryParameter("width", "560")
            .addQueryParameter("quality", "70")
            .addQueryParameter("height", "auto")
            .build()
        return url.toString()
    }

    private inline fun <reified T> Response.parseAs(): T = use {
        json.decodeFromStream(it.body.byteStream())
    }

    companion object {
        private const val API_URL = "https://zenko-api.onrender.com"
        private const val IMAGE_STORAGE_URL = "https://zenko.b-cdn.net"

        private val json: Json by injectLazy()
    }
}
