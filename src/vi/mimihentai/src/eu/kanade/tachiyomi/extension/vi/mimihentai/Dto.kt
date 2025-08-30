package eu.kanade.tachiyomi.extension.vi.mimihentai

import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale

@Serializable
class ListingDto(
    val data: List<MangaDto>,
    val totalPage: Long,
    val currentPage: Long,
)

@Serializable
class MangaDto(
    private val id: Long,
    private val title: String,
    private val coverUrl: String,
    private val description: String,
    private val authors: List<Author>,
    private val genres: List<Genre>,
) {
    fun toSManga(): SManga = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = coverUrl
        url = "$id"
        description = this@MangaDto.description
        author = authors.joinToString { i -> i.name }
        genre = genres.joinToString { i -> i.name }
        initialized = true
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
class ChapterDto(
    private val id: Long,
    private val title: String,
    private val createdAt: String,
) {
    fun toSChapter(mangaId: String): SChapter = SChapter.create().apply {
        name = title
        date_upload = dateFormat.tryParse(createdAt)
        url = "$mangaId/$id"
    }
}
private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.US)

@Serializable
class PageListDto(
    val pages: List<ListPages>,
)

@Serializable
class ListPages(
    val imageUrl: String,
    val drm: String? = null,
)

@Serializable
class Genres(
    val id: Long,
    val name: String,
)
