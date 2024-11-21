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
    val x1: Int,
    val y1: Int,
    val x2: Int,
    val y2: Int,
    val text: String,
) {
    val width get() = x2 - x1
    val height get() = y2 - y1
    val centerY get() = (y2 + y1) / 2f
    val centerX get() = (x2 + x1) / 2f

    fun breakLines(charWidth: Float): List<String> {
        val diameter = width / charWidth
        val radius = diameter / 2
        return breakTextIntoLines(text, diameter + radius)
    }

    private fun breakTextIntoLines(text: String, maxLineLength: Float): List<String> {
        if (text.length <= maxLineLength) {
            return listOf(text)
        }

        val words = text.split(" ")
        val lines = mutableListOf<String>()
        var currentLine = StringBuilder()

        for (word in words) {
            if (currentLine.length + word.length <= maxLineLength) {
                if (currentLine.isNotEmpty()) {
                    currentLine.append(" ")
                }
                currentLine.append(word)
            } else {
                lines.add(currentLine.toString().trim())
                currentLine = StringBuilder(word)
            }
        }

        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }

        return lines
    }
}

private object TranslationsListSerializer :
    JsonTransformingSerializer<List<Translation>>(ListSerializer(Translation.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonArray(
            element.jsonArray.map { array ->
                val (coordinates, text) = getA(array)

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

    private fun getA(element: JsonElement): Pair<JsonArray, JsonElement> {
        return try {
            val arr = element.jsonArray
            arr[0].jsonArray to arr[1]
        } catch (_: Exception) {
            val obj = element.jsonObject
            obj["bbox"]!!.jsonArray to obj["text"]!!
        }
    }
}
