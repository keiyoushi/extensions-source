package eu.kanade.tachiyomi.extension.en.tailspace

import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Response

// React Router single fetch (turbo-stream) `.data` responses
inline fun <reified T> Response.parseRouteData(route: String): T = decodeTurboStream()
    .jsonObject[route]!!.jsonObject["data"]!!
    .parseAs()

fun Response.decodeTurboStream(): JsonElement {
    val lines = use { it.body.string() }.lines()
    val flat = lines.first().parseAs<JsonArray>().toMutableList()

    // Deferred values sent after the first line, by placeholder slot
    val promises = mutableMapOf<Int, Int>()
    for (line in lines.drop(1).filter { it.isNotEmpty() }) {
        val (head, payload) = line.split(':', limit = 2)
        val value = payload.parseAs<JsonElement>()
        val valueIndex = if (value is JsonArray) {
            val start = flat.size
            flat.addAll(value)
            start
        } else {
            value.jsonPrimitive.int
        }
        if (head[0] == 'P') promises[head.drop(1).toInt()] = valueIndex
    }

    val cache = arrayOfNulls<JsonElement>(flat.size)
    fun resolve(i: Int): JsonElement {
        // Negative indices are null, undefined, NaN and the like
        if (i < 0) return JsonNull
        cache[i]?.let { return it }
        val result = when (val el = flat[i]) {
            is JsonPrimitive -> el
            is JsonArray -> when (el.firstOrNull()) {
                JsonPrimitive("P") -> promises[el[1].jsonPrimitive.int]?.let(::resolve) ?: JsonNull
                JsonPrimitive("Z") -> resolve(el[1].jsonPrimitive.int)
                // Date, as epoch millis
                JsonPrimitive("D") -> el[1]
                else -> JsonArray(el.map { resolve(it.jsonPrimitive.int) })
            }
            is JsonObject -> JsonObject(
                el.entries.associate { (k, v) ->
                    flat[k.removePrefix("_").toInt()].jsonPrimitive.content to resolve(v.jsonPrimitive.int)
                },
            )
        }
        cache[i] = result
        return result
    }
    return resolve(0)
}
