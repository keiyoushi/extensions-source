package eu.kanade.tachiyomi.extension.vi.nettruyenviet

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class ChapterListDto(
    @SerialName("data")
    private val data: List<ChapterDto> = emptyList(),
) {
    fun toChapterItems(slug: String): List<ChapterItem> = data.map { chapter ->
        chapter.toChapterItem(slug)
    }
}

@Serializable
class ChapterDto(
    @SerialName("chapter_name")
    private val chapterName: String,
    @SerialName("chapter_slug")
    private val chapterSlug: String,
    @SerialName("updated_at")
    private val updatedAt: String,
    @SerialName("chapter_num")
    private val chapterNum: Float = -1f,
) {
    fun toChapterItem(slug: String): ChapterItem = ChapterItem(
        name = chapterName,
        url = "/truyen-tranh/$slug/$chapterSlug",
        updatedAt = updatedAt,
        chapterNumber = chapterNum,
    )
}

class ChapterItem(
    val name: String,
    val url: String,
    val updatedAt: String,
    val chapterNumber: Float,
)
