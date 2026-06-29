package eu.kanade.tachiyomi.extension.es.mangacrab

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
class MvChaptersDto(
    val success: JsonElement? = null,
    val data: MvChaptersDataDto? = null,
) {
    val isSuccess: Boolean
        get() = success?.jsonPrimitive?.booleanOrNull == true || success?.jsonPrimitive?.content == "true"
}

@Serializable
class MvChaptersDataDto(
    val list: String? = null,
)
