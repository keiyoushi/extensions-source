package eu.kanade.tachiyomi.extension.vi.mimi

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class PaginatedResponse(
    val items: List<MangaDto> = emptyList(),
    val page: Int = 1,
    val total_pages: Int = 1,
    val has_next: Boolean = false,
)

@Serializable
class MangaDto(
    private val id: Int,
    private val title: String,
    private val cover_url: String? = null,
    private val description: String? = null,
    private val authors: List<AuthorDto> = emptyList(),
    private val genres: List<GenreDto> = emptyList(),
    private val alt_names: List<String> = emptyList(),
    private val parodies: List<ParodyDto> = emptyList(),
    private val characters: List<CharacterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = cover_url
        url = "/manga/$id"
        description = buildString {
            appendIfNotEmpty("Tên khác", alt_names)
            appendIfNotEmpty("Parody", parodies.map { it.name })
            appendIfNotEmpty("Nhân vật", characters.map { it.name })
            if (this@MangaDto.description?.isNotEmpty() == true) {
                append(this@MangaDto.description)
            }
        }
        author = authors.joinToString { it.name }.ifEmpty { null }
        genre = genres.joinToString { it.name }.ifEmpty { null }
        status = SManga.UNKNOWN
        initialized = true
    }

    fun toSMangaSimple() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = cover_url
        url = "/manga/$id"
    }
}

private fun StringBuilder.appendIfNotEmpty(label: String, list: List<String>) {
    if (list.isNotEmpty()) {
        append("$label: ${list.joinToString()}\n\n")
    }
}

@Serializable
class AuthorDto(
    val name: String,
)

@Serializable
class GenreDto(
    val id: Int,
    val name: String,
)

@Serializable
class ParodyDto(
    val name: String,
)

@Serializable
class CharacterDto(
    val name: String,
)

@Serializable
class ChapterDto(
    private val id: Int,
    private val title: String? = null,
    private val order: Int = 0,
    private val created_at: String? = null,
    private val manga_id: Int = 0,
) {
    fun toSChapter(): SChapter = SChapter.create().apply {
        url = "/manga/$manga_id/chapter/$id"
        name = title?.takeIf { it.isNotEmpty() } ?: "Chapter $order"
        chapter_number = order.toFloat()
        date_upload = dateFormat.tryParse(created_at)
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply {
    timeZone = TimeZone.getTimeZone("UTC")
}

@Serializable
class ChapterPageDto(
    val pages: List<PageImageDto> = emptyList(),
) {
    fun toPageList(): List<Page> = pages.mapIndexed { index, page ->
        Page(index, imageUrl = page.image_url)
    }
}

@Serializable
class PageImageDto(
    val image_url: String,
)
