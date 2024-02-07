package eu.kanade.tachiyomi.extension.en.mangafun

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * A somewhat direct port of the decoding parts of
 * [compress-json](https://github.com/beenotung/compress-json).
 */
object DecompressJson {
    fun decompress(c: JsonArray): JsonElement {
        val values = c[0].jsonArray
        val key = c[1].jsonPrimitive.content

        return decode(values, key)
    }

    private fun decode(values: JsonArray, key: String): JsonElement {
        if (key.isEmpty() || key == "_") {
            return JsonPrimitive(null)
        }

        val id = sToInt(key)
        val v = values[id]

        try {
            v.jsonNull
            return v
        } catch (_: IllegalArgumentException) {
            // v is not null, we continue on.
        }

        val vNum = v.jsonPrimitive.intOrNull

        if (vNum != null) {
            return v
        }

        if (v.jsonPrimitive.isString) {
            val content = v.jsonPrimitive.content

            if (content.length < 2) {
                return v
            }

            return when (content.substring(0..1)) {
                "b|" -> decodeBool(content)
                "n|" -> decodeNum(content)
                "o|" -> decodeObject(values, content)
                "a|" -> decodeArray(values, content)
                else -> v
            }
        }

        throw IllegalArgumentException("Unknown data type")
    }

    private fun decodeObject(values: JsonArray, s: String): JsonObject {
        if (s == "o|") {
            return JsonObject(emptyMap())
        }

        val vs = s.split("|")
        val keyId = vs[1]
        val keys = decode(values, keyId)
        val n = vs.size

        val keyArray = try {
            keys.jsonArray.map { it.jsonPrimitive.content }
        } catch (_: IllegalArgumentException) {
            // single-key object using existing value as key
            listOf(keys.jsonPrimitive.content)
        }

        return buildJsonObject {
            for (i in 2 until n) {
                val k = keyArray[i - 2]
                val v = decode(values, vs[i])
                put(k, v)
            }
        }
    }

    private fun decodeArray(values: JsonArray, s: String): JsonArray {
        if (s == "a|") {
            return JsonArray(emptyList())
        }

        val vs = s.split("|")
        val n = vs.size - 1
        return buildJsonArray {
            for (i in 0 until n) {
                add(decode(values, vs[i + 1]))
            }
        }
    }

    private fun decodeBool(s: String): JsonPrimitive {
        return when (s) {
            "b|T" -> JsonPrimitive(true)
            "b|F" -> JsonPrimitive(false)
            else -> JsonPrimitive(s.isNotEmpty())
        }
    }

    private fun decodeNum(s: String): JsonPrimitive =
        JsonPrimitive(sToInt(s.substringAfter("n|")))

    private fun sToInt(s: String): Int {
        var acc = 0
        var pow = 1

        s.reversed().forEach {
            acc += stoi[it]!! * pow
            pow *= 62
        }

        return acc
    }

    private val itos = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz"

    private val stoi = itos.associate {
        it to itos.indexOf(it)
    }
}
