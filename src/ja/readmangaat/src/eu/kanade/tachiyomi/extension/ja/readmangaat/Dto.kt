package eu.kanade.tachiyomi.extension.ja.readmangaat

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LatestDto(
    val mes: String,
    val going: String,
)

@Serializable
class InfoDto(
    val mes: String,
)

@Serializable
class ImageResponseDto(
    val mes: String,
    val going: Int,
    @SerialName("img_index") val imgIndex: Int,
)
