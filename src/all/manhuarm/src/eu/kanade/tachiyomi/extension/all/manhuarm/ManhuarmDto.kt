package eu.kanade.tachiyomi.extension.all.manhuarm

import android.util.Base64
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

@Serializable
class PageDto(
    @SerialName("image")
    val imageUrl: String,
    @SerialName("texts")
    @Serializable(with = DialogListSerializer::class)
    val dialogues: List<Dialog> = emptyList(),
)

@Serializable
class DialogDto(
    private val data: String = "",
) {
    val content: String by lazy {
        try {
            if (data.isBlank()) {
                return@lazy "[]"
            }

            val trimmedData = data.trim()

            // Check if data is a plain text error message instead of Base64
            if (trimmedData.contains("not found", ignoreCase = true) ||
                trimmedData.contains("error", ignoreCase = true) ||
                trimmedData.contains("OCR", ignoreCase = true)
            ) {
                return@lazy "[]"
            }

            // Validate Base64 format (only contains valid Base64 characters)
            if (!trimmedData.matches(Regex("^[A-Za-z0-9+/]*={0,2}$"))) {
                return@lazy "[]"
            }

            val decoded = Base64.decode(trimmedData, Base64.DEFAULT).toString(Charsets.UTF_8)

            // Validate that decoded content is valid JSON
            if (decoded.isBlank() || decoded.trim().isEmpty()) {
                return@lazy "[]"
            }

            decoded
        } catch (e: IllegalArgumentException) {
            "[]"
        } catch (e: Exception) {
            "[]"
        }
    }
}

@Serializable
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
    val text: String get() = textByLanguage["text"] ?: ""

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
        if (element !is JsonArray) {
            return JsonArray(emptyList())
        }

        if (element.jsonArray.isEmpty()) {
            return JsonArray(emptyList())
        }

        return JsonArray(
            element.jsonArray.mapNotNull { jsonElement ->
                try {
                    val coordinates = getCoordinates(jsonElement) ?: return@mapNotNull null
                    val textByLanguage = getDialogs(jsonElement)

                    // Validate coordinates array has at least 4 elements
                    if (coordinates.size < 4) return@mapNotNull null

                    buildJsonObject {
                        put("x", coordinates[0])
                        put("y", coordinates[1])
                        put("_width", coordinates[2])
                        put("_height", coordinates[3])
                        put("textByLanguage", textByLanguage)
                    }
                } catch (e: Exception) {
                    null
                }
            },
        )
    }

    private fun getCoordinates(element: JsonElement): JsonArray? {
        return try {
            when (element) {
                is JsonArray -> {
                    if (element.jsonArray.isNotEmpty()) {
                        val first = element.jsonArray.firstOrNull() ?: return null
                        if (first is JsonArray) first.jsonArray else null
                    } else {
                        null
                    }
                }
                is JsonObject -> {
                    element.jsonObject["box"]?.jsonArray
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getDialogs(element: JsonElement): JsonObject {
        return buildJsonObject {
            try {
                when (element) {
                    is JsonArray -> {
                        if (element.jsonArray.size > 1) {
                            val textElement = element.jsonArray[1]
                            if (textElement.isString) {
                                put("text", textElement)
                            }
                        }
                    }
                    is JsonObject -> {
                        element.jsonObject.entries
                            .filter { it.value.isString }
                            .forEach { put(it.key, it.value) }
                    }
                    else -> {
                        // Handle JsonPrimitive and JsonNull
                    }
                }
            } catch (e: Exception) {
                // Return empty object on error
            }
        }
    }

    private val JsonElement.isArray get() = this is JsonArray
    private val JsonElement.isObject get() = this is JsonObject
    private val JsonElement.isString: Boolean
        get() = try {
            this.isObject.not() && this.isArray.not() && this.jsonPrimitive.isString
        } catch (e: Exception) {
            false
        }
}
