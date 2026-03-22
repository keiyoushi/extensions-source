package eu.kanade.tachiyomi.extension.vi.mimi

import eu.kanade.tachiyomi.source.model.Page
import eu.kanade.tachiyomi.source.model.SChapter
import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class DataDto(
    val data: List<MangaDto> = emptyList(),
    val currentPage: Int = 0,
    val totalPage: Int = 0,
)

@Serializable
class MangaDto(
    private val id: Int,
    private val title: String,
    private val coverUrl: String?,
    private val authors: List<AuthorDto>,
    private val genres: List<GenreDto>,
    private val description: String,
    private val parody: List<String>,
    private val characters: List<String>,
    private val differentNames: List<String>,
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = coverUrl
        url = "$id"
        description = buildString {
            appendIfNotEmpty("Tên khác", differentNames)
            appendIfNotEmpty("Parody", parody)
            appendIfNotEmpty("Nhân vật", characters)
            append("Code: $id\n\n")
            append(this@MangaDto.description)
        }
        author = authors.joinToString { it.name }
        genre = genres.joinToString { it.name }
        status = SManga.UNKNOWN
        initialized = true
    }
}

private fun StringBuilder.appendIfNotEmpty(label: String, list: List<String>) {
    if (list.isNotEmpty()) {
        append("$label: ${list.joinToString()}\n\n")
    }
}

@Serializable
class AuthorDto(
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
        val imageUrl = url.drm
            ?.takeIf { it.isNotBlank() }
            ?.let {
                url.imageUrl.toHttpUrl().newBuilder()
                    .fragment("${MiMiImageInterceptor.FRAGMENT_PREFIX}$it")
                    .build()
                    .toString()
            }
            ?: url.imageUrl
        Page(index, imageUrl = imageUrl)
    }
}

@Serializable
class ListPages(
    val imageUrl: String,
    val drm: String? = null,
)
