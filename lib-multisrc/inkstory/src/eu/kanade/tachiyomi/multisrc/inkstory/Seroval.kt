package eu.kanade.tachiyomi.multisrc.inkstory

import keiyoushi.utils.array
import keiyoushi.utils.boolean
import keiyoushi.utils.booleanOrNull
import keiyoushi.utils.int
import keiyoushi.utils.obj
import keiyoushi.utils.string
import keiyoushi.utils.toJsonString
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

// TanStack Start server function payloads are seroval node trees

/** The `payload` query value for server function input [data]. */
internal fun serovalPayload(data: JsonElement): String {
    var nextId = 0
    fun node(element: JsonElement): JsonElement = when (element) {
        is JsonObject -> buildJsonObject {
            put("t", 10)
            put("i", nextId++)
            put(
                "p",
                buildJsonObject {
                    put("k", JsonArray(element.keys.map(::JsonPrimitive)))
                    put("v", JsonArray(element.values.map(::node)))
                },
            )
            put("o", 0)
        }
        is JsonArray -> buildJsonObject {
            put("t", 9)
            put("i", nextId++)
            put("a", JsonArray(element.map(::node)))
            put("o", 0)
        }
        JsonNull -> buildJsonObject {
            put("t", 2)
            put("s", 0)
        }
        is JsonPrimitive -> when {
            element.isString -> buildJsonObject {
                put("t", 1)
                put("s", serializeString(element.content))
            }
            element.booleanOrNull != null -> buildJsonObject {
                put("t", 2)
                put("s", if (element.boolean) 2 else 3)
            }
            else -> buildJsonObject {
                put("t", 0)
                put("s", element)
            }
        }
    }
    return buildJsonObject {
        put("t", node(buildJsonObject { put("data", data) }))
        put("f", 127)
        put("m", JsonArray(emptyList()))
    }.toJsonString()
}

/** The `result` of a server function response as plain JSON, null when the site reports the item as not found. */
internal fun JsonObject.serverFnResult(): JsonElement? {
    val refs = HashMap<Int, JsonElement>()
    fun decode(node: JsonObject): JsonElement {
        val value = when (val type = node["t"]!!.int) {
            0 -> node["s"]!!.jsonPrimitive
            1 -> JsonPrimitive(deserializeString(node["s"]!!.string))
            2 -> when (node["s"]!!.int) {
                2 -> JsonPrimitive(true)
                3 -> JsonPrimitive(false)
                else -> JsonNull
            }
            4 -> return refs[node["i"]!!.int]!!
            9 -> buildJsonArray { node["a"]!!.array.forEach { add(decode(it.obj)) } }
            10, 11 -> {
                val properties = node["p"]!!.obj
                val keys = properties["k"]!!.array
                val values = properties["v"]!!.array
                JsonObject(keys.indices.associate { keys[it].string to decode(values[it].obj) })
            }
            else -> error("Unsupported seroval node $type")
        }
        node["i"]?.let { refs[it.int] = value }
        return value
    }
    val properties = this["p"]!!.obj
    val keys = properties["k"]!!.array.map { it.string }
    val values = properties["v"]!!.array
    val result = decode(values[keys.indexOf("result")].obj)
    if (result != JsonNull) return result
    val error = values.getOrNull(keys.indexOf("error"))?.obj
    val notFound = error?.get("t")?.int == 10 && error["p"]!!.obj["k"]!!.array.any { it.string == "isNotFound" }
    return if (notFound) null else throw Exception("Server error")
}

// seroval escapes strings for embedding in a script
private val ESCAPES = mapOf(
    '"' to "\\\"",
    '\\' to "\\\\",
    '\n' to "\\n",
    '\r' to "\\r",
    '\b' to "\\b",
    '\t' to "\\t",
    '\u000C' to "\\f",
    '<' to "\\x3C",
    '\u2028' to "\\u2028",
    '\u2029' to "\\u2029",
)

private val UNESCAPES = ESCAPES.entries.associate { (char, escape) -> escape to char.toString() }

private val ESCAPE_REGEX = Regex(ESCAPES.values.joinToString("|", transform = Regex::escape))

private fun serializeString(value: String): String = buildString {
    value.forEach { append(ESCAPES[it] ?: it) }
}

private fun deserializeString(value: String): String = value.replace(ESCAPE_REGEX) { UNESCAPES[it.value]!! }
