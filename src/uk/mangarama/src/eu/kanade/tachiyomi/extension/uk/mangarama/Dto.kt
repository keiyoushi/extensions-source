package eu.kanade.tachiyomi.extension.uk.mangarama

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer

object ChapterAccessSerializer : JsonTransformingSerializer<Map<String, LockDto>>(
    MapSerializer(String.serializer(), LockDto.serializer()),
) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        if (element is JsonArray) {
            return JsonObject(emptyMap())
        }
        return element
    }
}

@Serializable
class ChapterDatesDto(
    val chapterDates: Map<String, String> = emptyMap(),
    @Serializable(with = ChapterAccessSerializer::class)
    val chapterAccess: Map<String, LockDto> = emptyMap(),
)

@Serializable
class LockDto(
    val locked: Boolean = false,
)

@Serializable
class PageJSON(
    val endpoint: String,
    val postId: Int,
    val chapterSlug: String,
    val token: String,
    val restNonce: String,
)

@Serializable
class Images(
    val pages: List<String>,
)
