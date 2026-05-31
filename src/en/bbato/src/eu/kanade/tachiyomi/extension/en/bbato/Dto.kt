package eu.kanade.tachiyomi.extension.en.bbato

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat

@Serializable
class ChapterListResponse(
    private val data: List<ChapterDto> = emptyList(),
) {
    fun toSChapterList(mangaSlug: String, dateFormat: SimpleDateFormat): List<SChapter> = data.map { it.toSChapter(mangaSlug, dateFormat) }
}

@Serializable
class ChapterDto(
    @SerialName("chapter_name") private val chapterName: String,
    @SerialName("chapter_slug") private val chapterSlug: String,
    @SerialName("updated_at") private val updatedAt: String,
) {
    fun toSChapter(mangaSlug: String, dateFormat: SimpleDateFormat) = SChapter.create().apply {
        url = "/read/$mangaSlug/$chapterSlug"
        name = chapterName
        date_upload = dateFormat.tryParse(updatedAt)
    }
}
