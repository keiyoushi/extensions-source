package eu.kanade.tachiyomi.extension.pt.readmangas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonNames

@Serializable
data class WrapperResult<T>(
    val result: Result<T>? = null,
) {
    @Serializable
    data class Result<T>(val `data`: Data<T>)

    @Serializable
    data class Data<T>(val json: T)
}

@Serializable
data class MangaListDto(
    @JsonNames("items")
    val mangas: List<MangaDto>,
    val nextCursor: String?,
)

@Serializable
data class MangaDto(
    val author: String,
    @SerialName("coverImage")
    val thumbnailUrl: String,
    val id: String,
    val slug: String,
    val status: String,
    val title: String,
)

@Serializable
data class ChapterListDto(
    val count: Int,
    val currentPage: Int,
    val chapters: List<ChapterDto>,
    val perPage: Int,
    val totalPages: Int,
) {
    fun hasNext() = currentPage < totalPages
}

@Serializable
data class ChapterDto(
    val id: String,
    val title: String,
    val number: String,
    val createdAt: String,
)
