package eu.kanade.tachiyomi.extension.en.bbato

import eu.kanade.tachiyomi.source.model.SChapter
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.time.Instant

@Serializable
class ChapterListResponse(
    private val data: List<ChapterDto> = emptyList(),
) {
    fun toSChapterList(mangaSlug: String): List<SChapter> = data.map { it.toSChapter(mangaSlug) }
}

@Serializable
class ChapterDto(
    @SerialName("chapter_name") private val chapterName: String,
    @SerialName("chapter_slug") private val chapterSlug: String,
    @SerialName("updated_at") private val updatedAt: String? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/read/$mangaSlug/$chapterSlug"
        name = chapterName
        date_upload = Instant.tryParse(updatedAt)
    }
}
