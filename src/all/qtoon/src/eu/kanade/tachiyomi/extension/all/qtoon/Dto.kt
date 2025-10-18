package eu.kanade.tachiyomi.extension.all.qtoon

import kotlinx.serialization.Serializable

@Serializable
class EncryptedResponse(
    val ts: Long,
    val data: String,
)

@Serializable
class Comics(
    val comics: List<Comic>,
    val more: Int,
)

@Serializable
class Comic(
    val csid: String,
    val webLinkId: String,
    val title: String,
    val image: Image,
)

@Serializable
class ComicUrl(
    val csid: String,
    val webLinkId: String,
)

@Serializable
class Image(
    val thumb: Thumb,
)

@Serializable
class Thumb(
    val url: String,
)
