package eu.kanade.tachiyomi.extension.en.vizshonenjump

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
class Dto(
    val ok: Int?,
    val data: JsonElement,
)
