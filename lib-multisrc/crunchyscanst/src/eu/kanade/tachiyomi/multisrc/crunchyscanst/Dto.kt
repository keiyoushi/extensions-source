package eu.kanade.tachiyomi.multisrc.crunchyscanst

import keiyoushi.utils.stringOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
class LdJsonDto(
    @SerialName("@graph") private val graph: List<LdNodeDto>,
) {
    val series get() = graph.firstOrNull { it.id?.endsWith("#book") == true }
}

@Serializable
class LdNodeDto(
    @SerialName("@id") val id: String? = null,
    val description: String? = null,
    private val genre: JsonElement? = null,
) {
    val genres
        get() = when (genre) {
            is JsonArray -> genre.mapNotNull { it.stringOrNull }
            is JsonPrimitive -> listOfNotNull(genre.stringOrNull)
            else -> emptyList()
        }
}

@Serializable
class ReaderHistoryDto(val pages: Int)

@Serializable
class VerifyRequestDto(private val token: String)

@Serializable
class VerifyResponseDto(val ok: Boolean)
