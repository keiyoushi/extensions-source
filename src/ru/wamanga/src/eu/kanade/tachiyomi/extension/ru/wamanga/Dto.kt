package eu.kanade.tachiyomi.extension.ru.wamanga

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

internal fun resolveNodeRef(nodes: JsonArray, ref: Int): JsonElement? {
    if (ref < 0 || ref >= nodes.size) return null
    val node = nodes[ref]
    return when {
        node is JsonObject && (node["type"] as? JsonPrimitive)?.content == "skip" -> null
        node is JsonObject && (node["type"] as? JsonPrimitive)?.content == "data" -> node["data"]
        else -> node
    }
}

internal fun resolveValue(data: JsonArray, nodes: JsonArray, ref: Int?): JsonElement? {
    if (ref == null) return null
    if (ref < 0 || ref >= data.size) return null
    val item = data[ref]
    if (item !is JsonObject) return item
    return when ((item["type"] as? JsonPrimitive)?.content) {
        "string" -> item["value"]
        "ref" -> {
            val valueRef = item["value"]?.toRef() ?: return item
            resolveNodeRef(nodes, valueRef)
        }

        else -> item
    }
}

internal fun resolveString(data: JsonArray, nodes: JsonArray, ref: Int?): String? {
    val resolved = resolveValue(data, nodes, ref) ?: return null
    if (resolved !is JsonPrimitive || resolved is JsonNull) return null
    if (!resolved.isString) return null
    return resolved.content
}

internal fun resolveStringList(data: JsonArray, nodes: JsonArray, ref: Int?): List<String> {
    if (ref == null) return emptyList()
    val resolved = resolveValue(data, nodes, ref) ?: return emptyList()
    val arr = when {
        resolved is JsonArray -> resolved
        else -> return emptyList()
    }
    return arr.mapNotNull { elem ->
        when {
            elem is JsonPrimitive && elem !is JsonNull && elem.isString -> elem.content
            elem is JsonPrimitive && elem !is JsonNull && !elem.isString -> {
                val idx = elem.content.toIntOrNull() ?: return@mapNotNull null
                val r = resolveValue(data, nodes, idx)
                if (r is JsonPrimitive && r !is JsonNull) r.content else null
            }

            else -> null
        }
    }
}

internal fun JsonElement?.toRef(): Int? {
    if (this == null) return null
    if (this !is JsonPrimitive || this is JsonNull) return null
    if (this.isString) return null
    return this.content.toIntOrNull()
}
