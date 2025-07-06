package eu.kanade.tachiyomi.extension.vi.truyenhentai18

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class SearchDto(
    val data: List<MangaDto>,
)

@Serializable
class MangaDto(
    val title: String,
    val slug: String,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        url = "/vi/$slug.html"
    }
}

@Serializable
class ChapterWrapper(
    @SerialName("post_slug")
    val postSlug: String,
    val data: List<ChapterDto>,
) {
    fun toSChapterList() = data.map { it.toSChapter(postSlug) }
}

@Serializable
class ChapterDto(
    val slug: String,
    @SerialName("chapter_number")
    val chapterNumber: Float,
    @SerialName("created_at")
    val createdAt: String,
    val content: String,
) {
    fun toSChapter(postSlug: String) = SChapter.create().apply {
        name = chapterNumber.toString()
        chapter_number = chapterNumber
        url = "/vi/$postSlug/$slug.html"
        date_upload = dateFormat.tryParse(createdAt)
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
