package eu.kanade.tachiyomi.extension.id.storyaboutus

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaDto(
    private val id: String,
    val title: String,
    private val summary: String,
    val status: String,
    private val cover: String,
    val chapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        url = id
        title = this@MangaDto.title
        thumbnail_url = cover
        description = summary.ifEmpty { null }
        status = when (this@MangaDto.status) {
            "ongoing" -> SManga.ONGOING
            "end" -> SManga.COMPLETED
            "end-season", "hiatus" -> SManga.ON_HIATUS
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class ChapterDto(
    val number: Float,
    private val key: String,
    private val label: String? = null,
    @SerialName("uploaded_at") private val uploadedAt: Long = 0L,
) {
    fun toSChapter() = SChapter.create().apply {
        url = key
        name = label ?: "Chapter ${number.toString().removeSuffix(".0")}"
        chapter_number = number
        date_upload = uploadedAt
    }
}
