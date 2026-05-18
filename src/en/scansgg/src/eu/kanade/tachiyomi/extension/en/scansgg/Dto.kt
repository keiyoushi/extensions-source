package eu.kanade.tachiyomi.extension.en.scansgg

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

@Serializable
class ResponseDto<T>(
    val data: T,
    val meta: MetaDto? = null,
)

@Serializable
class MetaDto(
    @SerialName("has_more") val hasMore: Boolean = false,
)

@Serializable
class SeriesDto(
    private val id: Int,
    private val title: String,
    private val summary: String? = null,
    private val cover: String? = null,
    private val author: List<String>? = null,
    private val artist: List<String>? = null,
    private val tags: List<Int>? = null,
    private val status: Int? = null,
) {
    fun toSManga(cdnUrl: String, tagsMap: Map<Int, String>? = null) = SManga.create().apply {
        url = id.toString()
        this.title = this@SeriesDto.title
        thumbnail_url = cover?.let { "$cdnUrl/covers/$it" }
        if (tagsMap != null) {
            description = summary
            author = this@SeriesDto.author?.joinToString()
            artist = this@SeriesDto.artist?.joinToString()
            genre = tags?.mapNotNull { tagsMap[it] }?.joinToString()
            this.status = when (this@SeriesDto.status) {
                1 -> SManga.ONGOING
                2 -> SManga.COMPLETED
                3, 4, 5 -> SManga.CANCELLED
                else -> SManga.UNKNOWN
            }
        }
    }
}

@Serializable
class ChapterDto(
    private val id: Int,
    private val number: Float,
    private val title: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
    @SerialName("group_id") private val groupId: Int? = null,
    private val group: GroupDto? = null,
) {
    fun toSChapter(seriesIdStr: String, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        // SChapter.url is used as a full API path to avoid passing multiple fields later
        url = "/chapter-navigation?series_id=$seriesIdStr&chapter_id=$id&group_id=${groupId ?: 0}"
        name = buildString {
            append("Chapter ")
            append(number.toString().removeSuffix(".0"))
            if (!title.isNullOrEmpty()) {
                append(" - ")
                append(title)
            }
        }
        date_upload = dateFormat.tryParse(createdAt)
        scanlator = group?.title
    }
}

@Serializable
class GroupDto(
    val title: String? = null,
)

@Serializable
class PageListDto(
    private val chapter: ChapterDataDto? = null,
) {
    fun toPages(cdnUrl: String): List<Page> {
        val chapterId = chapter?.id ?: return emptyList()
        return chapter.pages?.map {
            Page(it.position, imageUrl = "$cdnUrl/pages/$chapterId/${it.path}")
        } ?: emptyList()
    }
}

@Serializable
class ChapterDataDto(
    val id: Int? = null, // Capturing the chapter id to use it in the image path
    val pages: List<PageDto>? = null,
)

@Serializable
class PageDto(
    val position: Int,
    val path: String,
)
