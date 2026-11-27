package eu.kanade.tachiyomi.extension.tr.piescans

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
class LatestDto(val data: LatestDataDto)

@Serializable
class LatestDataDto(
    val html: String,
    @SerialName("max_pages") val maxPages: Int,
)
