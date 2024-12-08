package eu.kanade.tachiyomi.extension.pt.readmangas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
class WrapperResult<T>(
    val result: Result<T>? = null,
) {
    @Serializable
    class Result<T>(val `data`: Data<T>)

    @Serializable
    class Data<T>(val json: T)
}

@Serializable
class MangaListDto(
    @JsonNames("items")
    val mangas: List<MangaDto>,
    val nextCursor: String?,
)

@Serializable
class MangaDto(
    val author: String,
    @SerialName("coverImage")
    val thumbnailUrl: String,
    val id: String,
    val slug: String,
    val status: String,
    val title: String,
)

@Serializable
class ChapterListDto(
    val currentPage: Int,
    val chapters: List<ChapterDto>,
    val totalPages: Int,
) {
    fun hasNext() = currentPage < totalPages
}

@Serializable
class ChapterDto(
    val id: String,
    val title: String,
    val number: String,
    val createdAt: String,
)
