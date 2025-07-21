package eu.kanade.tachiyomi.extension.vi.mimihentai

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class MangaDTO(
    val data: List<Manga>,
    val totalPage: Long,
    val currentPage: Long,
)

@Serializable
class Manga(
    private val id: Long,
    @SerialName("title")
    val titles: String,
    val coverUrl: String,
    val description: String,
    val authors: List<Author>,
    val genres: List<Genre>,
) {
    fun toManga(): SManga = SManga.create().apply {
        title = titles
        thumbnail_url = coverUrl
        url = "$id"
    }
}

@Serializable
class Author(
    val name: String,
)

@Serializable
class Genre(
    val name: String,
)

@Serializable
class ChapterDTO(
    private val id: Long,
    private val title: String,
    private val createdAt: String,
) {
    fun toChapterDTO(mangaUrl: String): SChapter = SChapter.create().apply {
        name = title
        date_upload = dateFormat.tryParse(createdAt)
        url = "/g/$mangaUrl/chapter/$id"
    }
}
private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US)

@Serializable
class PageListDTO(
    val pages: List<String>,
)
