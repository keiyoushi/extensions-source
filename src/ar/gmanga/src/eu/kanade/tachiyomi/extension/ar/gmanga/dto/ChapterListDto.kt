package eu.kanade.tachiyomi.extension.ar.gmanga.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChapterListDto(
    val releases: List<ReleaseDto>,
    val teams: List<TeamDto>,
    val chapters: List<ChapterDto>,
)
