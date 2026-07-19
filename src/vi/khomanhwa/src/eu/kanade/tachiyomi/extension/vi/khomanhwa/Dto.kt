package eu.kanade.tachiyomi.extension.vi.khomanhwa

import kotlinx.serialization.Serializable

@Serializable
class ReaderImagesResponse(
    val ok: Boolean = false,
    val images: List<ReaderImage> = emptyList(),
)

@Serializable
class ReaderImage(
    val page: Int = 0,
    val url: String = "",
    val alt: String = "",
)
