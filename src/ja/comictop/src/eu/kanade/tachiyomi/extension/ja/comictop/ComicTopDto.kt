package eu.kanade.tachiyomi.extension.ja.comictop

import kotlinx.serialization.Serializable

typealias ChapterDataDto = Map<String, ImageDto>

@Serializable
data class ImageDto(
    val image: String,
)
