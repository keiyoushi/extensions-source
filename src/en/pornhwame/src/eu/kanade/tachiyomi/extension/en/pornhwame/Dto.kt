package eu.kanade.tachiyomi.extension.en.pornhwame

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class QwikStateDto(
    val objs: List<JsonElement>,
)

@Serializable
class SeriesLdDto(
    @SerialName("@type") val type: String? = null,
    val description: String? = null,
)
