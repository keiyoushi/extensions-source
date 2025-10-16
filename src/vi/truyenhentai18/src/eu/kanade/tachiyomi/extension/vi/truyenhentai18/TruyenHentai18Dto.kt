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
    private val title: String,
    private val slug: String,
    private val thumbnail: String,
) {
    fun toSManga(cdnUrl: String) = SManga.create().apply {
        title = this@MangaDto.title
        url = "/$slug.html"
        thumbnail_url = "$cdnUrl/uploads/$thumbnail"
    }
}

@Serializable
class ChapterWrapper(
    @SerialName("post_slug")
    private val postSlug: String,
    val data: List<ChapterDto>,
) {
    fun toSChapterList() = data.map { it.toSChapter(postSlug) }
}

@Serializable
class ChapterDto(
    private val title: String,
    val slug: String,
    @SerialName("chapter_number")
    private val chapterNumber: Float,
    @SerialName("created_at")
    private val createdAt: String,
    val content: String,
) {
    fun toSChapter(postSlug: String) = SChapter.create().apply {
        name = title
        chapter_number = chapterNumber
        url = "/$postSlug/$slug.html"
        date_upload = dateFormat.tryParse(createdAt)
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT)
    }
}
