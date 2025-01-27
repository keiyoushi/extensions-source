package eu.kanade.tachiyomi.extension.vi.yurineko.dto

import kotlinx.serialization.Serializable

@Serializable
class ChapterDto(
    val id: Int,
    val name: String,
    val date: String?,
    val mangaID: Int?,
)

@Serializable
class ReadResponseDto(
    val url: List<String>,
)
