package eu.kanade.tachiyomi.extension.en.sacachispa

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import kotlin.time.Clock
import kotlin.time.Instant

private val CDN_URL = "https://cdn.sacachispa.site/".toHttpUrl()

private fun String.toCoverUrl() = CDN_URL.resolve(this)?.toString()

@Serializable
class MangaListResponse(
    val data: List<MangaListDto>,
    val pagination: PaginationDto,
)

@Serializable
class MangaListDto(
    private val id: String,
    private val slug: String,
    private val title: String,
    private val cover: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = id
        memo = buildJsonObject { put("slug", slug) }
        title = this@MangaListDto.title
        thumbnail_url = cover?.toCoverUrl()
    }
}

@Serializable
class MangaResponse(
    val data: MangaDetailDto,
)

@Serializable
class MangaDetailDto(
    val id: String,
    private val slug: String,
    private val title: String,
    private val status: String,
    private val authors: List<NamedDto>,
    private val artists: List<NamedDto>,
    private val genres: List<NamedDto>,
    private val cover: String? = null,
    private val synopses: List<SynopsisDto>,
) {
    // A stored url can't change, so entries from before the site rewrite keep their slug url.
    fun toSManga(storedUrl: String) = SManga.create().apply {
        url = storedUrl
        memo = buildJsonObject {
            put("id", id)
            put("slug", slug)
        }
        title = this@MangaDetailDto.title
        thumbnail_url = cover?.toCoverUrl()
        author = authors.joinToString { it.name }.ifEmpty { null }
        artist = artists.joinToString { it.name }.ifEmpty { null }
        genre = genres.joinToString { it.name }.ifEmpty { null }
        description = synopses.firstOrNull()?.synopsis
        status = when (this@MangaDetailDto.status) {
            "ONGOING" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            "HIATUS" -> SManga.ON_HIATUS
            "DROPPED" -> SManga.CANCELLED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class NamedDto(
    val name: String,
)

@Serializable
class SynopsisDto(
    val synopsis: String,
)

@Serializable
class ReleaseListResponse(
    val data: List<ReleaseDto>,
    val pagination: PaginationDto,
)

@Serializable
class ReleaseDto(
    private val id: String,
    private val chapter: ChapterRefDto,
    private val publishedAt: String,
) {
    fun toSChapter(hideLocked: Boolean): SChapter? {
        val freeTime = Instant.tryParse(chapter.freeAt)
        val isLocked = if (freeTime > 0L) freeTime > Clock.System.now().toEpochMilliseconds() else chapter.patreonOnly

        if (hideLocked && isLocked) return null

        return SChapter.create().apply {
            url = id
            name = buildString {
                if (isLocked) append("🔒 ")
                append("Chapter ", chapter.chapter)
                chapter.title?.takeIf { it.isNotBlank() }?.let { append(" - ", it) }
            }
            chapter_number = chapter.chapter.toFloatOrNull() ?: -1f
            date_upload = Instant.tryParse(publishedAt)
        }
    }
}

@Serializable
class ChapterRefDto(
    val chapter: String,
    val title: String? = null,
    val patreonOnly: Boolean,
    val freeAt: String? = null,
)

@Serializable
class PageListResponse(
    val data: PageListDto,
)

@Serializable
class PageListDto(
    val items: List<PageDto>,
)

@Serializable
class PageDto(
    val url: String,
)

@Serializable
class ErrorResponse(
    val error: ErrorDto,
)

@Serializable
class ErrorDto(
    val message: String,
)

@Serializable
class PaginationDto(
    val page: Int,
    val pages: Int,
)
