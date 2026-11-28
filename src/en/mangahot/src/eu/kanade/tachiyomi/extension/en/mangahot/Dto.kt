package eu.kanade.tachiyomi.extension.en.mangahot

import kotlinx.serialization.Serializable

@Serializable
class ChaptersResponseDto(
    val data: ChaptersDataDto? = null,
)

@Serializable
class ChaptersDataDto(
    val html: String? = null,
)
