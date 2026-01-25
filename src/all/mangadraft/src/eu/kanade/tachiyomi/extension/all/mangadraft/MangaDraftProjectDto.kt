package eu.kanade.tachiyomi.extension.all.mangadraft.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MangaDraftProjectDto(
    val name: String,
    val description: String,

    val genres: List<MangaDraftGenreDto> = emptyList(),

    @SerialName("project_status_id")
    val projectStatusId: Int,
)

@Serializable
data class MangaDraftGenreDto(
    val id: Int,
    val name: String,
    val slug: String,
)
