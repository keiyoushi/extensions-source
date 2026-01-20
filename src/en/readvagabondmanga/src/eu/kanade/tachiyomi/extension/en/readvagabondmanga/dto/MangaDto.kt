package eu.kanade.tachiyomi.extension.en.readvagabondmanga.dto

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

@Serializable()
data class MangaDto(
    val id: String = "",
    val title: String = "",
    val author: String = "",
    val artist: String = "",
    val description: String = "",
    val status: MangaStatus = MangaStatus.ONGOING,
    val cover: String = "",
)
