package eu.kanade.tachiyomi.extension.vi.lavibitwindroom

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlin.time.Instant

@Serializable
class SeriesDto(
    val id: Int,
    val slug: String,
    val title: String,
    @SerialName("cover_url") private val coverUrl: String? = null,
    private val author: String? = null,
    private val description: String? = null,
    private val status: String? = null,
    private val tags: List<String>? = null,
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@SeriesDto.title
        thumbnail_url = coverUrl
    }

    fun toSMangaDetails() = SManga.create().apply {
        url = slug
        title = this@SeriesDto.title
        thumbnail_url = coverUrl
        author = this@SeriesDto.author
        description = this@SeriesDto.description
        genre = tags?.joinToString()
        status = when (this@SeriesDto.status?.lowercase()) {
            "đang ra" -> SManga.ONGOING
            "hoàn thành" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }

    fun toChapterList(): List<SChapter> = chapters
        .sortedByDescending { it.chapterNumber }
        .map { it.toSChapter(slug) }
}

@Serializable
class ChapterDto(
    val id: Int,
    @SerialName("chapter_number") val chapterNumber: Float,
    private val title: String? = null,
    @SerialName("created_at") private val createdAt: String? = null,
) {
    fun toSChapter(slug: String) = SChapter.create().apply {
        url = id.toString()
        name = buildString {
            append("Chapter ${chapterNumber.toString().removeSuffix(".0")}")
            this@ChapterDto.title?.takeIf { it.isNotBlank() }?.let { append(": $it") }
        }
        date_upload = Instant.tryParse(createdAt)
        memo = buildJsonObject {
            put("chapter_id", id)
            put("slug", slug)
            put("chapter_number", chapterNumber.toString().removeSuffix(".0"))
        }
        chapter_number = chapterNumber
    }
}

@Serializable
class PageDto(
    @SerialName("image_url") val imageUrl: String,
)
