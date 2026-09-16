package eu.kanade.tachiyomi.extension.es.ikigaimangas

import android.util.Base64
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
        thumbnail_url = cover?.let {
            val coverUrl = when {
                it.startsWith("//") -> "https:$it"
                it.startsWith("http://", ignoreCase = true) ||
                    it.startsWith("https://", ignoreCase = true) -> it
                it.startsWith("s3://ikigai-cdn/") ->
                    "https://media.ikigaimangas.cloud/${it.removePrefix("s3://ikigai-cdn/")}"
                it.contains("/f:") || it.startsWith("f:") -> "https://image2.ikigaimangas.cloud/$it"
                it.startsWith("/") -> imageCdnUrl + it
                else -> "$imageCdnUrl/$it"
            }
            normalizeImageUrl(coverUrl)
        }
    }
}

fun normalizeImageUrl(url: String): String {
    val normalizedUrl = if (url.startsWith("http://", ignoreCase = true)) {
        "https://${url.substring(7)}"
    } else {
        url
    }

    if (normalizedUrl.startsWith("https://image3.ikigaimangas.cloud/")) {
        return normalizedUrl.replace(
            "https://image3.ikigaimangas.cloud/",
            "https://media.ikigaimangas.cloud/",
        )
    }

    if (!normalizedUrl.startsWith("https://image2.ikigaimangas.cloud/")) return normalizedUrl

    val encodedPath = normalizedUrl.substringAfterLast('/').substringBeforeLast('.')
    return runCatching {
        val decodedPath = Base64.decode(
            encodedPath,
            Base64.URL_SAFE,
        ).toString(Charsets.UTF_8)

        if (decodedPath.startsWith("s3://ikigai-cdn/")) {
            "https://media.ikigaimangas.cloud/" + decodedPath.removePrefix("s3://ikigai-cdn/")
        } else {
            normalizedUrl
        }
    }.getOrDefault(normalizedUrl)
}
