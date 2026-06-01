package eu.kanade.tachiyomi.extension.en.kunmangaonline

import eu.kanade.tachiyomi.source.model.SChapter
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterListResponse(
    val data: ChapterData,
)

@Serializable
class ChapterData(
    val chapters: List<ChapterDto>,
    @SerialName("last_page") val lastPage: Int,
)

@Serializable
class ChapterDto(
    @SerialName("chapter_name") private val chapterName: String,
    @SerialName("chapter_slug") private val chapterSlug: String,
    @SerialName("updated_at") private val updatedAt: String? = null,
) {
    fun toSChapter(slug: String, parseDate: (String?) -> Long) = SChapter.create().apply {
        url = "/manga/$slug/$chapterSlug"
        name = chapterName
        date_upload = parseDate(updatedAt)
    }
}
