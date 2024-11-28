package eu.kanade.tachiyomi.extension.en.snowmtl

import android.graphics.Color
import android.os.Build
import androidx.annotation.RequiresApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put

@Serializable
class PageDto(
    @SerialName("img_url")
    val imageUrl: String,
    @Serializable(with = TranslationsListSerializer::class)
    val translations: List<Translation> = emptyList(),
)

@Serializable
@RequiresApi(Build.VERSION_CODES.O)
class Translation(
    val x1: Float,
    val y1: Float,
    val x2: Float,
    val y2: Float,
    val text: String,
    val angle: Float = 0f,
    val isBold: Boolean = false,
    val isNewApi: Boolean = false,
    val type: String = "sub",
    private val fbColor: List<Int> = emptyList(),
    private val bgColor: List<Int> = emptyList(),
) {
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

private object TranslationsListSerializer :
    JsonTransformingSerializer<List<Translation>>(ListSerializer(Translation.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonArray(
            element.jsonArray.map { jsonElement ->
                val (coordinates, text) = getCoordinatesAndCaption(jsonElement)

                buildJsonObject {
                    put("x1", coordinates[0])
                    put("y1", coordinates[1])
                    put("x2", coordinates[2])
                    put("y2", coordinates[3])
                    put("text", text)

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
