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
data class AuthorSchema(
    val name: String? = null,
)

@Serializable
data class SeriesDetailsDto(
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
data class GenreDto(
    val name: String,
)

@Serializable
data class ChapterDto(
    val number: Float,
    val title: String = "",
    val isLocked: Boolean = false,
    val publishedAt: String? = null,
)

@Serializable
data class ReaderChapterDto(
    val pages: List<ReaderPageDto> = emptyList(),
)

@Serializable
data class ReaderPageDto(
    val pageNumber: Int,
    val imageUrl: String,
)
