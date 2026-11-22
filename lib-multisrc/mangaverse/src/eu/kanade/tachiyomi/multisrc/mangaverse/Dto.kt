package eu.kanade.tachiyomi.multisrc.mangaverse

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class AjaxConfigDto(val nonce: String)

@Serializable
class LoadMoreDto(val data: LoadMoreDataDto)

@Serializable
class LoadMoreDataDto(
    val html: String,
    @SerialName("has_more") val hasMore: Boolean,
)
