package eu.kanade.tachiyomi.extension.all.sxypix

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class GalleryDto(
    @SerialName("r") val blocks: List<String>,
)
