package eu.kanade.tachiyomi.extension.ar.gmanga.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ReleaseDto(
    val id: Int,
    @SerialName("created_at") val createdAt: String,
    @SerialName("timestamp") val timestamp: Long,
    val views: Int,
    @SerialName("chapterization_id") val chapterizationId: Int,
    @SerialName("team_id") val teamId: Int,
    val teams: List<Int>,
)
