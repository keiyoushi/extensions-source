package eu.kanade.tachiyomi.extension.es.catmanhwas

import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull

class SvelteParser(val data: JsonArray) {

    inline fun <reified T> parseAs(): T = parse().parseAs<T>()

    fun parse(): JsonElement = resolve(data[0])

    private fun dereference(index: Int): JsonElement = when (val value = data[index]) {
        is JsonArray, is JsonObject -> resolve(value)
        else -> value
    }

    private fun resolveReference(element: JsonElement): JsonElement {
        val index = (element as? JsonPrimitive)?.intOrNull

        return if (
            index != null &&
            !element.isString &&
            index in data.indices
        ) {
            dereference(index)
        } else {
            resolve(element)
        }
    }

    private fun resolve(element: JsonElement): JsonElement = when (element) {
        is JsonArray -> JsonArray(element.map(::resolveReference))
        is JsonObject -> buildJsonObject {
            element.forEach { (key, value) ->
                put(key, resolveReference(value))
            }
        }
        else -> element
    }
}
