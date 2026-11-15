package eu.kanade.tachiyomi.extension.vi.vietmanhwa

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
    val flat = use { it.body.string() }.lineSequence().first().parseAs<JsonArray>()

    val cache = arrayOfNulls<JsonElement>(flat.size)
    fun resolve(i: Int): JsonElement {
        // Negative indices are null, undefined and the like
        if (i < 0) return JsonNull
        cache[i]?.let { return it }
        val result = when (val el = flat[i]) {
            is JsonPrimitive -> el
            // Typed values start with their tag, plain arrays only hold indices
            is JsonArray -> when ((el.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content) {
                null -> JsonArray(el.map { resolve(it.jsonPrimitive.int) })
                // Date, as epoch millis
                "D" -> el[1]
                // MongoDB ids (class instances) and the like, never read
                else -> JsonNull
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
