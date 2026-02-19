package eu.kanade.tachiyomi.extension.pt.geasscomics

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaListDto(
    val items: List<MangaDto> = emptyList(),
    val pagination: PaginationDto? = null,
)

@Serializable
class PaginationDto(
    val pages: Int = 1,
    val currentPage: Int = 1,
) {
    val hasNextPage: Boolean
        get() = currentPage < pages
}

@Serializable
class MangaDto(
    val id: Int,
    val title: String,
    @SerialName("cover") val thumbnail: String? = null,
    val synopsis: String? = null,
    val status: String? = null,
    val author: AuthorDto? = null,
    val genres: List<TagDto> = emptyList(),
    val tags: List<TagDto> = emptyList(),
    val lastChapters: List<ChapterDto> = emptyList(),
) {
    fun toSManga() = SManga.create().apply {
        title = this@MangaDto.title
        thumbnail_url = this@MangaDto.thumbnail
        url = "/obra/${this@MangaDto.id}"
        description = this@MangaDto.synopsis
        author = this@MangaDto.author?.toString()
        genre = (genres + tags).joinToString { it.name }
        status = when (this@MangaDto.status) {
            "IN_PROGRESS" -> SManga.ONGOING
            "COMPLETED" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
        initialized = true
    }
}

@Serializable
class AuthorDto(
    val firstName: String? = null,
    val lastName: String? = null,
) {
    override fun toString(): String = listOfNotNull(firstName, lastName).joinToString(" ").trim()
}

@Serializable
class TagDto(
    val id: Int,
    val name: String,
)

@Serializable
class ChapterListDto(
    val items: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val id: Long? = null,
    val chapter: Float? = null,
    val number: Float? = null,
    val title: String,
    val date: String? = null,
) {
    val chapterNumber: Float
        get() = chapter ?: number ?: 0f
}

@Serializable
class AuthRequestDto(
    val email: String,
    val password: String,
)

@Serializable
class AuthResponseDto(
    val jwt: JwtDto,
)

@Serializable
class JwtDto(
    val token: String,
)
