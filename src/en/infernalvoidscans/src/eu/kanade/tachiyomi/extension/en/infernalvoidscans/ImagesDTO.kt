package eu.kanade.tachiyomi.extension.en.infernalvoidscans

import kotlinx.serialization.Serializable

@Serializable
class Images(
    val images: List<Image>,
)

@Serializable
class Image(
    val url: String,
)
