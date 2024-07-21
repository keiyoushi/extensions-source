package eu.kanade.tachiyomi.extension.en.readcomicsbook

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class Data<T>(val data: T)

@Serializable
class Comic(
    val title: String,
    val slug: String,
    @SerialName("img_url") val cover: String? = null,
)
