package eu.kanade.tachiyomi.extension.ja.mokuro

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LibraryDto(
    val series: List<SeriesDto>,
)

@Serializable
data class SeriesDto(
    val name: String,
    val path: String,
    val cover: String? = null,
    val volumes: List<VolumeDto>,
)

@Serializable
data class VolumeDto(
    val name: String,
    val cover: String? = null,
)

@Serializable
data class MokuroDto(
    val pages: List<MokuroPageDto>,
)

@Serializable
data class MokuroPageDto(
    @SerialName("img_path") val imgPath: String,
)
