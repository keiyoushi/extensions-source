package eu.kanade.tachiyomi.extension.ar.mangatik

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class MangaJsonLd(
    val name: String,
    val description: String? = null,
    val image: ImageObject? = null,
    val genre: List<String>? = null,
    val author: Author? = null,
)

@Serializable
class ImageObject(
    @SerialName("url") val url: String? = null,
)

@Serializable
class Author(
    val name: String? = null,
)
