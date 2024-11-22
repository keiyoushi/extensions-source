package eu.kanade.tachiyomi.extension.en.snowmtl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
class PageDto(
    @SerialName("img_url")
    val imageUrl: String,
    @Serializable(with = TranslationsListSerializer::class)
    val translations: List<Translation> = emptyList(),
)

@Serializable
class Translation(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val text: String,
) {
    val width get() = x2 - x1
    val height get() = y2 - y1
    val centerY get() = (y2 + y1) / 2f
    val centerX get() = (x2 + x1) / 2f
}

private object TranslationsListSerializer :
    JsonTransformingSerializer<List<Translation>>(ListSerializer(Translation.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonArray(
            element.jsonArray.map { array ->
                val (coordinates, text) = getCoordinatesAndCaption(array)

                buildJsonObject {
                    put("x1", coordinates[0])
                    put("y1", coordinates[1])
                    put("x2", coordinates[2])
                    put("y2", coordinates[3])
                    put("text", text)
                }
            },
        )
    }

    private fun getCoordinatesAndCaption(element: JsonElement): Pair<JsonArray, JsonElement> {
        return try {
            val arr = element.jsonArray
            arr[0].jsonArray to arr[1]
        } catch (_: Exception) {
            val obj = element.jsonObject
            obj["bbox"]!!.jsonArray to obj["text"]!!
        }
    }
}
