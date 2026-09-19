package eu.kanade.tachiyomi.extension.es.ikigaimangas

import eu.kanade.tachiyomi.source.model.SManga
import keiyoushi.utils.parseAs
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

@Serializable
class QwikData(
    @SerialName("_objs") val objects: List<JsonElement>,
) {
    inline fun <reified T> parseAsList(): List<T> {
        val arr = objects
        val results = mutableListOf<T>()
        var i = 0

        while (i < arr.size) {
            var mapIndex = i
            while (mapIndex < arr.size && arr[mapIndex] !is JsonObject) {
                mapIndex++
            }
            if (mapIndex >= arr.size) break

            val map = arr[mapIndex].jsonObject

            val objContent = buildMap {
                for ((key, jsonIndex) in map) {
                    val ref = jsonIndex.jsonPrimitive.content

                    val index = try {
                        ref.toInt(radix = 36)
                    } catch (_: Exception) {
                        throw Exception("Invalid base36 index: $ref")
                    }

                    val rawValue = arr.getOrNull(index)

                    put(key, unwrapJson(rawValue))
                }
            }

            results.add(JsonObject(objContent).parseAs<T>())

            i = mapIndex + 1
        }

        return results
    }

    fun unwrapJson(el: JsonElement?): JsonElement = when (el) {
        is JsonPrimitive -> el
        is JsonObject -> el
        is JsonArray -> JsonArray(el.map { unwrapJson(it) })
        else -> JsonNull
    }
}

@Serializable
class QwikSeriesDto(
    val name: String,
    private val slug: String,
    private val cover: String? = null,
    val type: String? = null,
    @SerialName("is_mature") val isMature: Boolean = false,
) {
    fun toSManga(imageCdnUrl: String) = SManga.create().apply {
        url = slug
        title = name
        thumbnail_url = cover?.let { "$imageCdnUrl/$it" }
    }
}
