package eu.kanade.tachiyomi.extension.ar.gmanga.dto

import kotlinx.serialization.Serializable

@Serializable
data class TeamDto(
    val id: Int,
    val name: String,
)
