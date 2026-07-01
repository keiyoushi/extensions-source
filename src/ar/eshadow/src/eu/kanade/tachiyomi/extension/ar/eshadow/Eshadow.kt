package eu.kanade.tachiyomi.extension.ar.eshadow

import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.model.MangasPage
import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import eu.kanade.tachiyomi.source.online.HttpSource
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale

class Eshadow : HttpSource() {

    override val baseUrl = "https://eshadow.net"

    override val name = "Eshadow"

    override val lang = "ar"

    override val supportsLatest = true

    private val json = Json { ignoreUnknownKeys = true }

    override fun popularMangaRequest(page: Int): Request = GET("$baseUrl/api/manga?page=$page", headers)

    override fun popularMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<MangaListResponse>(response.body!!.string())
        val mangas = result.data.map { item ->
            SManga.create().apply {
                url = "/manga/${item.id}"
                title = item.title
                thumbnail_url = item.coverImage
                description = item.description
                author = item.author
                status = when (item.status) {
                    "ONGOING" -> SManga.ONGOING
                    "COMPLETED" -> SManga.COMPLETED
                    "HIATUS" -> SManga.ON_HIATUS
                    "CANCELLED" -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
        }
        val hasNextPage = result.page * result.limit < result.total
        return MangasPage(mangas, hasNextPage)
    }

    override fun latestUpdatesRequest(page: Int): Request = GET("$baseUrl/api/manga?page=$page", headers)

    override fun latestUpdatesParse(response: Response): MangasPage = popularMangaParse(response)

    override fun searchMangaRequest(page: Int, query: String, filters: FilterList): Request = GET("$baseUrl/api/manga?page=$page", headers)

    override fun searchMangaParse(response: Response): MangasPage {
        val result = json.decodeFromString<MangaListResponse>(response.body!!.string())
        val mangas = result.data.map { item ->
            SManga.create().apply {
                url = "/manga/${item.id}"
                title = item.title
                thumbnail_url = item.coverImage
                description = item.description
                author = item.author
                status = when (item.status) {
                    "ONGOING" -> SManga.ONGOING
                    "COMPLETED" -> SManga.COMPLETED
                    "HIATUS" -> SManga.ON_HIATUS
                    "CANCELLED" -> SManga.CANCELLED
                    else -> SManga.UNKNOWN
                }
            }
        }
        return MangasPage(mangas, false)
    }

    override fun mangaDetailsRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/manga/$id", headers)
    }

    override fun mangaDetailsParse(response: Response): SManga {
        val result = json.decodeFromString<MangaDetailResponse>(response.body!!.string())
        return SManga.create().apply {
            url = "/manga/${result.id}"
            title = result.title
            thumbnail_url = result.coverImage
            description = result.description
            author = result.author
            status = when (result.status) {
                "ONGOING" -> SManga.ONGOING
                "COMPLETED" -> SManga.COMPLETED
                "HIATUS" -> SManga.ON_HIATUS
                "CANCELLED" -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
            initialized = true
        }
    }

    override fun chapterListRequest(manga: SManga): Request {
        val id = manga.url.substringAfterLast("/")
        return GET("$baseUrl/api/manga/$id", headers)
    }

    override fun chapterListParse(response: Response): List<SChapter> {
        val result = json.decodeFromString<MangaDetailResponse>(response.body!!.string())
        return result.chapters.map { ch ->
            SChapter.create().apply {
                url = "/manga/${result.id}/chapter/${ch.number}"
                name = "الفصل ${ch.number} - ${ch.title ?: ""}".trimEnd(' ', '-')
                chapter_number = ch.number.toFloat()
                date_upload = parseDate(ch.publishedAt)
            }
        }.sortedByDescending { it.chapter_number }
    }

    override fun pageListRequest(chapter: SChapter): Request {
        val mangaId = chapter.url.split("/")[2]
        return GET("$baseUrl/api/manga/$mangaId", headers)
    }

    override fun pageListParse(response: Response): List<Page> {
        val result = json.decodeFromString<MangaDetailResponse>(response.body!!.string())
        val chapterUrl = response.request.url.encodedPath
        val chapterNum = chapterUrl.substringAfterLast("/").toIntOrNull() ?: 0
        val chapter = result.chapters.find { it.number == chapterNum } ?: result.chapters.lastOrNull()
            ?: return emptyList()
        return chapter.images.mapIndexed { index, imageUrl ->
            Page(index, imageUrl = imageUrl)
        }
    }

    override fun imageUrlParse(response: Response): String = throw UnsupportedOperationException()

    override fun imageRequest(page: Page): Request {
        val newHeaders = headersBuilder()
            .set("Accept", "image/avif,image/webp,image/png,image/jpeg,*/*")
            .set("Referer", "$baseUrl/")
            .build()
        return GET(page.imageUrl!!, newHeaders)
    }

    private fun parseDate(dateStr: String?): Long {
        if (dateStr == null) return 0L
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).parse(dateStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }

    @Serializable
    data class MangaListResponse(
        val data: List<MangaListItem>,
        val page: Int = 1,
        val limit: Int = 30,
        val total: Int = 0,
    )

    @Serializable
    data class MangaListItem(
        val id: String = "",
        val slug: String = "",
        val title: String = "",
        val description: String? = null,
        val coverImage: String? = null,
        val bannerImage: String? = null,
        val author: String? = null,
        val status: String? = null,
        val isFeatured: Boolean = false,
        val releaseYear: Int? = null,
        val views: Int = 0,
        val genres: List<String>? = emptyList(),
        val chapters: List<ChapterSummary>? = emptyList(),
    )

    @Serializable
    data class ChapterSummary(
        val number: Int = 0,
        val publishedAt: String? = null,
    )

    @Serializable
    data class MangaDetailResponse(
        val id: String = "",
        val slug: String = "",
        val title: String = "",
        val description: String? = null,
        val coverImage: String? = null,
        val bannerImage: String? = null,
        val author: String? = null,
        val status: String? = null,
        val chapters: List<ChapterDetail> = emptyList(),
    )

    @Serializable
    data class ChapterDetail(
        val id: String = "",
        val mangaId: String = "",
        val title: String? = null,
        val number: Int = 0,
        val description: String? = null,
        val images: List<String> = emptyList(),
        val publishedAt: String? = null,
    )
}
