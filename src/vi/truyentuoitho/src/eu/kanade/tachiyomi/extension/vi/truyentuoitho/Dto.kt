package eu.kanade.tachiyomi.extension.vi.truyentuoitho

import kotlinx.serialization.Serializable

@Serializable
class ChapterImagesPayload(
    val images: List<String>,
)

@Serializable
class DriveSessionDto(
    val meta: String,
    val img: String,
)

@Serializable
class DriveMetaDto(
    val pages: Int,
    val maxPageWidth: Int,
)
