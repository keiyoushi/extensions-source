package eu.kanade.tachiyomi.extension.vi.mimi

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class DataDto(
    val items: List<MangaDto> = emptyList(),
    val page: Int = 1,
    @SerialName("total_pages")
    val totalPage: Int = 1,
    @SerialName("has_next")
    val hasNext: Boolean = false,
)

@Serializable
class MangaDto(
    private val id: Long,
    private val title: String,
    @SerialName("cover_url")
    private val coverUrl: String? = null,
    private val description: String? = null,
    @SerialName("alt_names")
    private val differentNames: List<String> = emptyList(),
    private val authors: List<AuthorAndParodyAndCharacter> = emptyList(),
    private val genres: List<GenreDto> = emptyList(),
    private val parodies: List<AuthorAndParodyAndCharacter> = emptyList(),
    private val characters: List<AuthorAndParodyAndCharacter> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = coverUrl
        url = "$id"
        description = buildString {
            appendIfNotEmpty("Tên khác", differentNames)
            appendIfNotEmpty("Parody", parodies.map { it.name.trim() })
            appendIfNotEmpty("Nhân vật", characters.map { it.name.trim() })
            appendIfNotEmpty("Code author", authors.map { it.id.toString().trim() })
            append("Code manga: $id\n\n")
            append(this@MangaDto.description)
        }
        author = authors.joinToString { it.name }
        genre = genres.joinToString { it.name }
        status = SManga.UNKNOWN
        initialized = true
    }
    fun toSMangaBasic() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = coverUrl
        url = "$id"
    }
}

private fun StringBuilder.appendIfNotEmpty(label: String, list: List<String>) {
    if (list.isNotEmpty()) {
        append("$label: ${list.joinToString()}\n\n")
    }
}

@Serializable
class AuthorAndParodyAndCharacter(
    val id: Int? = null,
    val name: String,
)

@Serializable
class GenreDto(
    val id: Int,
    val name: String,
)

@Serializable
class ChapterDto(
    private val id: Int,
    private val title: String? = null,
    private val order: Int = 0,
    @SerialName("created_at")
    private val createdAt: String? = null,
) {
    fun toSChapter(mangaId: String): SChapter = SChapter.create().apply {
        url = "$mangaId/$id"
        name = title?.takeIf { it.isNotBlank() } ?: "Chapter $order"
        chapter_number = order.toFloat()
        date_upload = dateFormat.tryParse(createdAt)
    }
}

private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("Asia/Ho_Chi_Minh") }

@Serializable
class PageDto(
    private val pages: List<ListPages> = emptyList(),
) {
    fun toPage(): List<Page> = pages.mapIndexed { index, url ->
        Page(index, imageUrl = url.imageUrl)
    }
}

@Serializable
class ListPages(
    @SerialName("image_url")
    val imageUrl: String,
)
