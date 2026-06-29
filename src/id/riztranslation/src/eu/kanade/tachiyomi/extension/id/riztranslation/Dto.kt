package eu.kanade.tachiyomi.extension.id.riztranslation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BookDto(
    val id: Int,
    val judul: String,
    val cover: String? = null,
    val status: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val synopsis: String? = null,
    val genres: List<BookGenreDto>? = null,
)

@Serializable
class LatestChapterDto(
    @SerialName("Book")
    val book: BookDto? = null,
)

@Serializable
class BookGenreDto(
    val genre: GenreDto? = null,
)

@Serializable
class GenreDto(
    val nama: String? = null,
)

@Serializable
class ChapterDto(
    val id: Int,
    val bookId: Int,
    val chapter: Float? = null,
    val nama: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
    val isigambar: String? = null,
)
