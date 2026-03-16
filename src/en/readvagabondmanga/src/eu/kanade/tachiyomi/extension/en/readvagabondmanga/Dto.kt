package eu.kanade.tachiyomi.extension.en.readvagabondmanga

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class MangaStatus {
    @SerialName("ongoing")
    ONGOING,

    @SerialName("completed")
    COMPLETED,

    @SerialName("hiatus")
    HIATUS,
}

@Serializable
data class ChapterDto(
    val id: Int,
    val number: Int,
    val title: String,
    val volume: Int?,
    @SerialName("manga_id")
    val mangaId: String,
    @SerialName("release_date")
    val releaseDate: String,
    @SerialName("page_count")
    val pageCount: Int,
    @SerialName("created_at")
    val createdAt: String = "",
)

@Serializable
data class MangaDto(
    val id: String,
    val title: String,
    val author: String,
    val artist: String,
    val description: String,
    val status: MangaStatus = MangaStatus.ONGOING,
    val cover: String,
)
