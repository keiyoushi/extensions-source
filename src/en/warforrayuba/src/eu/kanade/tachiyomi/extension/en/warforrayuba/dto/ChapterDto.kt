package eu.kanade.tachiyomi.extension.en.warforrayuba.dto

import kotlinx.serialization.Serializable

@Serializable
data class ChapterDto(
    val title: String,
    val volume: Int,
    val groups: ChapterGroupDto,
    val last_updated: Long,
)

@Serializable
data class ChapterGroupDto(
    val primary: String,
)
