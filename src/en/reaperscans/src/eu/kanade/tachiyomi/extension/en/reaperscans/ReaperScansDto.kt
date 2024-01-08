package eu.kanade.tachiyomi.extension.en.reaperscans

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class LiveWireResponseDto(
    val effects: LiveWireEffectsDto,
    val serverMemo: JsonObject,
)

@Serializable
data class LiveWireEffectsDto(
    val html: String,
)

@Serializable
data class LiveWireDataDto(
    val fingerprint: JsonObject,
    val serverMemo: JsonObject,
)
