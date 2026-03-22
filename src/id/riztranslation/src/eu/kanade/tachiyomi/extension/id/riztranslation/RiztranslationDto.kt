package eu.kanade.tachiyomi.extension.id.riztranslation

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class BookDto {
    var id: Int? = null
    var judul: String? = null
    var cover: String? = null
    var status: String? = null
    var author: String? = null
    var artist: String? = null
    var synopsis: String? = null
    var genres: List<BookGenreDto>? = null
}

@Serializable
class LatestChapterDto {
    @SerialName("Book")
    var book: BookDto? = null
}

@Serializable
class BookGenreDto {
    var genre: GenreDto? = null
}

@Serializable
class GenreDto {
    var nama: String? = null
}

@Serializable
class ChapterDto {
    var id: Int? = null
    var chapter: Float? = null
    var nama: String? = null

    @SerialName("created_at")
    var createdAt: String? = null
    var isigambar: String? = null
    var bookId: Int? = null
}
