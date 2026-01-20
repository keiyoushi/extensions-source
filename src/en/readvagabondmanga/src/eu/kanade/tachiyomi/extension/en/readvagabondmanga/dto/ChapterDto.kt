package eu.kanade.tachiyomi.extension.en.readvagabondmanga.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable()
data class ChapterDto(
    val id: Int = 0,
    val number: Int = 0,
    val title: String = "",
    val volume: Int? = 0,
    @SerialName("release_date")
    val releaseDate: String = "",
    @SerialName("page_count")
    val pageCount: Int = 0,
    @SerialName("created_at")
    val createdAt: String = "",
)
