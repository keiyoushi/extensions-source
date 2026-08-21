package eu.kanade.tachiyomi.extension.all.manhuarm

import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

/**
 * Format-agnostic fallback parser for the OCR response. The site has already changed its
 * payload shape three times; instead of failing on a new shape this walks the JSON by its
 * content (image fields, box coordinates, text fields) and normalizes it into [PageDto]s,
 * so future format changes keep working as long as they carry the same meaning.
 */
object OcrNormalizer {

    // MARKER_REGEX is defined in Dto.kt

    fun normalize(raw: String): List<PageDto> = try {
        toPages(raw.parseAs<JsonElement>())
    } catch (_: Exception) {
        emptyList()
    }

    private fun toPages(element: JsonElement): List<PageDto> = when (element) {
        is JsonArray -> element.jsonArray.mapNotNull { toPage(it) }
        is JsonObject -> listOfNotNull(toPage(element))
        else -> emptyList()
    }

    private fun toPage(element: JsonElement): PageDto? = try {
        val obj = element.jsonObject
        val image = obj.stringValue("image")
            ?: obj.stringValue("imageUrl")
            ?: obj.stringValue("img")
            ?: return null

        val dialogues = obj["texts"]
            ?: obj["dialogues"]
            ?: obj["dialogs"]
            ?: obj["lines"]
            ?: obj["boxes"]
            ?: return PageDto(image, emptyList())

        PageDto(image, toDialogues(dialogues))
    } catch (_: Exception) {
        null
    }

    private fun toDialogues(element: JsonElement): List<Dialog> = when (element) {
        is JsonArray -> element.jsonArray.mapNotNull { toDialog(it) }
        is JsonObject -> element.jsonObject.values.mapNotNull { toDialog(it) }
        else -> emptyList()
    }

    private fun toDialog(element: JsonElement): Dialog? = try {
        val box = coordinatesOf(element) ?: return null

        val text = when (element) {
            is JsonArray -> (element.jsonArray.getOrNull(1) as? JsonPrimitive)?.content
            is JsonObject -> {
                val direct = element.jsonObject.stringValue("text")
                    ?: element.jsonObject.stringValue("content")
                    ?: element.jsonObject.stringValue("value")
                if (direct != null) {
                    direct
                } else {
                    element.jsonObject.values.mapNotNull { (it as? JsonPrimitive)?.content }.firstOrNull()
                }
            }
            else -> null
        }

        val cleaned = text?.cleanUp().orEmpty()
        if (cleaned.isBlank()) return null

        Dialog(
            x = box[0],
            y = box[1],
            _width = box[2],
            _height = box[3],
            angle = (element as? JsonObject)?.floatValue("angle") ?: 0f,
            textByLanguage = mapOf("text" to cleaned),
        )
    } catch (_: Exception) {
        null
    }

    private fun coordinatesOf(element: JsonElement): List<Float>? = when (element) {
        is JsonArray -> {
            val first = element.jsonArray.firstOrNull()
            when {
                first is JsonArray -> first.jsonArray.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
                element.jsonArray.size >= 4 && element.jsonArray.take(4).all { (it as? JsonPrimitive)?.floatOrNull != null } ->
                    element.jsonArray.take(4).mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
                else -> null
            }
        }
        is JsonObject -> {
            val box = element.jsonObject["box"]?.jsonArray?.mapNotNull { (it as? JsonPrimitive)?.floatOrNull }
            if (box != null && box.size >= 4) {
                box.take(4)
            } else {
                val x = element.jsonObject.floatValue("x")
                val y = element.jsonObject.floatValue("y")
                val w = element.jsonObject.floatValue("width")
                val h = element.jsonObject.floatValue("height")
                if (x != null && y != null && w != null && h != null) listOf(x, y, w, h) else null
            }
        }
        else -> null
    }

    private fun String.cleanUp(): String = trim().replaceFirst(MARKER_REGEX, "").trim()

    private fun JsonObject.stringValue(key: String): String? = (this[key] as? JsonPrimitive)?.content

    private fun JsonObject.floatValue(key: String): Float? = (this[key] as? JsonPrimitive)?.floatOrNull
}
