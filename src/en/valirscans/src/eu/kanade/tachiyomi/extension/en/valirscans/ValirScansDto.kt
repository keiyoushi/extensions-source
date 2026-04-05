package eu.kanade.tachiyomi.extension.en.valirscans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BookSchema(
    @SerialName("@type") val type: String? = null,
    val name: String = "",
    val author: AuthorSchema? = null,
    val image: String? = null,
    val description: String? = null,
    val genre: List<String> = emptyList(),
)

@Serializable
class AuthorSchema(
    val name: String? = null,
)

@Serializable
class SeriesDetailsDto(
    val title: String,
    val slug: String,
    val author: String? = null,
    val artist: String? = null,
    val description: String? = null,
    val coverImage: String? = null,
    val status: String? = null,
    val type: String? = null,
    val genres: List<GenreDto> = emptyList(),
)

@Serializable
class SeriesPageDto(
    val series: SeriesDetailsDto,
    val chapters: List<ChapterDto> = emptyList(),
    val currentPage: Int = 1,
    val totalPages: Int = 1,
)

@Serializable
class GenreDto(
    val name: String,
)

@Serializable
class ChapterDto(
    val number: Float,
    val title: String = "",
    val isLocked: Boolean = false,
    val publishedAt: String? = null,
)

@Serializable
class ReaderChapterDto(
    val pages: List<ReaderPageDto> = emptyList(),
)

@Serializable
class ChapterPageDto(
    val chapter: ReaderChapterDto,
)

@Serializable
class ReaderPageDto(
    val pageNumber: Int,
    val imageUrl: String,
)
