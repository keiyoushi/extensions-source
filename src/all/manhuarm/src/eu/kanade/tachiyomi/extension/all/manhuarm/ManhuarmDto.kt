package eu.kanade.tachiyomi.extension.all.manhuarm

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
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException

@Serializable
class PageDto(
    @SerialName("image")
    val imageUrl: String,

    @SerialName("texts")
    @Serializable(with = DialogListSerializer::class)
    val dialogues: List<Dialog> = emptyList(),
)

@Serializable
@RequiresApi(Build.VERSION_CODES.O)
data class Dialog(
    val x: Float,
    val y: Float,
    private val _width: Float,
    private val _height: Float,
    val angle: Float = 0f,
    val textByLanguage: Map<String, String> = emptyMap(),
) {
    var scale: Float = 1F

    val height: Float get() = scale * _height
    val width: Float get() = scale * _width

    val text: String get() = textByLanguage["text"] ?: throw Exception("Dialog not found")
    fun getTextBy(language: Language) = when {
        !language.disableTranslator -> textByLanguage[language.origin]
        else -> textByLanguage[language.target]
    } ?: text
    val centerY get() = height / 2 + y
    val centerX get() = width / 2 + x
}

private object DialogListSerializer :
    JsonTransformingSerializer<List<Dialog>>(ListSerializer(Dialog.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement {
        return JsonArray(
            element.jsonArray.map { jsonElement ->
                val coordinates = getCoordinates(jsonElement)
                val textByLanguage = getDialogs(jsonElement)

                buildJsonObject {
                    put("x", coordinates[0])
                    put("y", coordinates[1])
                    put("_width", coordinates[2])
                    put("_height", coordinates[3])
                    put("textByLanguage", textByLanguage)
                }
            },
        )
    }

    private fun getCoordinates(element: JsonElement): JsonArray {
        return when (element) {
            is JsonArray -> element.jsonArray[0].jsonArray
            else -> element.jsonObject["box"]?.jsonArray
                ?: throw IOException("Dialog box position not found")
        }
    }
    private fun getDialogs(element: JsonElement): JsonObject {
        return buildJsonObject {
            when (element) {
                is JsonArray -> put("text", element.jsonArray[1])
                else -> {
                    element.jsonObject.entries
                        .filter { it.value.isString }
                        .forEach { put(it.key, it.value) }
                }
            }
        }
    }

    private val JsonElement.isArray get() = this is JsonArray
    private val JsonElement.isObject get() = this is JsonObject
    private val JsonElement.isString get() = this.isObject.not() && this.isArray.not() && this.jsonPrimitive.isString
}
