package eu.kanade.tachiyomi.multisrc.machinetranslations

import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
class PageDto(
    @SerialName("img_url")
    val imageUrl: String,

    @SerialName("translations")
    @Serializable(with = DialogListSerializer::class)
    val dialogues: List<Dialog> = emptyList(),
)

@Serializable
@RequiresApi(Build.VERSION_CODES.O)
data class Dialog(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val angle: Float = 0f,
    val isBold: Boolean = false,
    val isNewApi: Boolean = false,
    val textByLanguage: Map<String, String> = emptyMap(),
    val type: String = "normal",
    private val fbColor: List<Int> = emptyList(),
    private val bgColor: List<Int> = emptyList(),
) {
    val text: String get() = textByLanguage["text"] ?: throw Exception("Dialog not found")
    fun getTextBy(language: Language) = textByLanguage[language.target] ?: text

    val width get() = x2 - x1
    val height get() = y2 - y1
    val centerY get() = (y2 + y1) / 2f
    val centerX get() = (x2 + x1) / 2f

    val foregroundColor: Int get() {
        val color = fbColor.takeIf { it.isNotEmpty() }
            ?: return Color.BLACK
        return Color.rgb(color[0], color[1], color[2])
    }

    val backgroundColor: Int get() {
        val color = bgColor.takeIf { it.isNotEmpty() }
            ?: return Color.WHITE
        return Color.rgb(color[0], color[1], color[2])
    }
}

private object DialogListSerializer :
    JsonTransformingSerializer<List<Dialog>>(ListSerializer(Dialog.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonArray(
            element.jsonArray.map { jsonElement ->
                val coordinates = getCoordinates(jsonElement)
                val textByLanguage = getDialogs(jsonElement)

                buildJsonObject {
                    put("x1", coordinates[0])
                    put("y1", coordinates[1])
                    put("x2", coordinates[2])
                    put("y2", coordinates[3])
                    put("textByLanguage", textByLanguage)

                    try {
                        val obj = jsonElement.jsonObject
                        obj["fg_color"]?.let { put("fbColor", it) }
                        obj["bg_color"]?.let { put("bgColor", it) }
                        obj["angle"]?.let { put("angle", it) }
                        obj["type"]?.let { put("type", it) }
                        obj["is_bold"]?.let { put("isBold", it) }
                        put("isNewApi", true)
                    } catch (_: Exception) { }
                }
            },
        )
    }

    private fun getCoordinates(element: JsonElement): JsonArray {
        return try {
            element.jsonArray[0].jsonArray
        } catch (_: Exception) {
            element.jsonObject["bbox"]!!.jsonArray
        }
    }
    private fun getDialogs(element: JsonElement): JsonObject {
        return try {
            buildJsonObject {
                put("text", element.jsonArray[1])
            }
        } catch (_: Exception) {
            buildJsonObject {
                // There is a problem when the "angle" is processed
                element.jsonObject.entries.forEach {
                    if (it.key in listOf("angle", "bbox")) {
                        return@forEach
                    }
                    put(it.key, it.value)
                }
            }
        }
    }
}
