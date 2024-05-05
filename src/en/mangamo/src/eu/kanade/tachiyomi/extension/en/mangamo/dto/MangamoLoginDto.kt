package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.Serializable

@Serializable
data class MangamoLoginDto(
    val accessToken: String,
    val analyticsId: String,
)
