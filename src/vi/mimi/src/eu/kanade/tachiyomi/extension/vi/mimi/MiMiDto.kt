package eu.kanade.tachiyomi.extension.vi.mimi

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.Serializable

@Serializable
class SearchResponse(
    val data: List<MangaDto> = emptyList(),
    val currentPage: Int = 0,
    val totalPage: Int = 0,
    val totalElem: Int = 0,
)

@Serializable
class MangaDto(
    val id: Int,
    val title: String,
    val coverUrl: String? = null,
    val authors: List<AuthorDto>? = null,
    val genres: List<GenreDto>? = null,
    val description: String? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/g/$id"
        title = this@MangaDto.title
        thumbnail_url = coverUrl
        author = authors?.joinToString { it.name ?: "" }
        genre = genres?.joinToString { it.name ?: "" }
        description = this@MangaDto.description
    }
}

@Serializable
class MangaInfo(
    val id: Int,
    val title: String,
    val coverUrl: String? = null,
    val authors: List<AuthorDto>? = null,
    val genres: List<GenreDto>? = null,
    val description: String? = null,
    val parody: List<String>? = null,
    val characters: List<String>? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/g/$id"
        title = this@MangaInfo.title
        thumbnail_url = coverUrl
        author = authors?.joinToString { it.name ?: "" }
        genre = genres?.joinToString { it.name ?: "" }
        description = buildString {
            this@MangaInfo.description?.let { append(it) }
            if (parody?.isNotEmpty() == true) {
                if (isNotEmpty()) append("\n\n")
                append("Parody: ${parody.joinToString()}")
            }
            if (characters?.isNotEmpty() == true) {
                if (isNotEmpty()) append("\n\n")
                append("Characters: ${characters.joinToString()}")
            }
        }
        status = SManga.UNKNOWN
    }
}

@Serializable
class AuthorDto(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
class GenreDto(
    val id: Int? = null,
    val name: String? = null,
)

@Serializable
class ChapterDto(
    val id: Int,
    val title: String? = null,
    val order: Int = 0,
    val createdAt: String? = null,
)

@Serializable
class ChapterPages(
    val pages: List<PageDto> = emptyList(),
)

@Serializable
class PageDto(
    val imageUrl: String,
)
