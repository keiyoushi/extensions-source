package eu.kanade.tachiyomi.extension.en.bbato

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParseDateTime
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.format.DateTimeFormatter

@Serializable
class ChapterListResponse(
    private val data: List<ChapterDto> = emptyList(),
) {
    fun toSChapterList(mangaSlug: String, dateTimeFormatter: DateTimeFormatter): List<SChapter> = data.map { it.toSChapter(mangaSlug, dateTimeFormatter) }
}

@Serializable
class ChapterDto(
    @SerialName("chapter_name") private val chapterName: String,
    @SerialName("chapter_slug") private val chapterSlug: String,
    @SerialName("updated_at") private val updatedAt: String? = null,
) {
    fun toSChapter(mangaSlug: String, dateTimeFormatter: DateTimeFormatter) = SChapter.create().apply {
        url = "/read/$mangaSlug/$chapterSlug"
        name = chapterName
        date_upload = dateTimeFormatter.tryParseDateTime(updatedAt)
    }
}
