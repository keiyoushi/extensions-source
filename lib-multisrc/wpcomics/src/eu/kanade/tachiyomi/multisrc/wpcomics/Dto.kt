package eu.kanade.tachiyomi.multisrc.wpcomics

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
class ChapterListDto(
    val data: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    @SerialName("chapter_id") val chapterId: Int = 0,
    @SerialName("chapter_name") private val chapterName: String,
    @SerialName("chapter_slug") val chapterSlug: String,
    @SerialName("updated_at") private val updatedAt: String,
    @SerialName("chapter_num") private val chapterNum: Float = -1f,
) {
    fun toSChapter(url: String, dateFormat: DateTimeFormatter, zone: ZoneId) = SChapter.create().apply {
        this.url = url
        name = chapterName
        date_upload = dateFormat.tryParseDateTime(updatedAt, zone)
        chapter_number = chapterNum
    }
}
