package eu.kanade.tachiyomi.extension.vi.soaicacomic

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
class SearchResponse(
    val data: List<SearchResultDto> = emptyList(),
)

@Serializable
class SearchResultDto(
    val title: String,
    val link: String,
    val img: JsonElement? = null,
)

fun SearchResultDto.imageUrl(): String? {
    val value = img?.jsonPrimitive?.contentOrNull
    return value?.takeUnless { it.equals("false", ignoreCase = true) || it.isBlank() }
}
