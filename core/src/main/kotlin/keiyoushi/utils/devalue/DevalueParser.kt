package keiyoushi.utils.devalue

import keiyoushi.utils.jsonInstance
import keiyoushi.utils.parseAs
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

fun interface DevalueReviver {
    fun revive(type: String, data: JsonElement): JsonElement?
}

internal class DevalueParser(
    private val revivers: Map<String, DevalueReviver> = emptyMap(),
) {
    companion object {
        const val UNDEFINED = -1
        const val HOLE = -2
        const val NAN = -3
        const val POSITIVE_INFINITY = -4
        const val NEGATIVE_INFINITY = -5
        const val NEGATIVE_ZERO = -6
        const val SPARSE = -7
    }

    fun parse(input: String): JsonElement {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return JsonNull

        val lines = trimmed.lines().map { it.trim() }.filter { it.isNotEmpty() }
        if (lines.isEmpty()) return JsonNull

        val firstElement = lines[0].parseAs<JsonElement>(jsonInstance)
        if (firstElement !is JsonArray) {
            return if (firstElement is JsonPrimitive && firstElement.intOrNull != null) {
                resolveSentinel(firstElement.intOrNull!!)
            } else {
                firstElement
            }
        }

        val flat = firstElement.toMutableList()
        val promises = mutableMapOf<Int, Int>()

        for (i in 1 until lines.size) {
            val line = lines[i]
            val colonIdx = line.indexOf(':')
            if (colonIdx == -1) continue

            val head = line.substring(0, colonIdx).trim()
            val payloadStr = line.substring(colonIdx + 1).trim()
            if (payloadStr.isEmpty()) continue

            val slotId = (if (head.startsWith("P", ignoreCase = true)) head.substring(1) else head).toIntOrNull()
                ?: continue

            val payload = payloadStr.parseAs<JsonElement>(jsonInstance)
            val valueIndex = if (payload is JsonArray) {
                val start = flat.size
                flat.addAll(payload)
                start
            } else if (payload is JsonPrimitive && payload.intOrNull != null) {
                payload.intOrNull!!
            } else {
                val start = flat.size
                flat.add(payload)
                start
            }
            promises[slotId] = valueIndex
        }

        return unflatten(flat, promises)
    }

    fun parse(raw: JsonArray): JsonElement = unflatten(raw.toMutableList(), emptyMap())

    private fun resolveSentinel(code: Int): JsonElement = when (code) {
        UNDEFINED, HOLE, NAN -> JsonNull
        POSITIVE_INFINITY -> JsonPrimitive("Infinity")
        NEGATIVE_INFINITY -> JsonPrimitive("-Infinity")
        NEGATIVE_ZERO -> JsonPrimitive(-0.0)
        else -> if (code < 0) JsonNull else JsonPrimitive(code)
    }

    private fun unflatten(values: List<JsonElement>, promises: Map<Int, Int>): JsonElement {
        val n = values.size
        val cache = arrayOfNulls<JsonElement>(n)
        val cached = BooleanArray(n)
        val visiting = BooleanArray(n)

        fun resolve(index: Int): JsonElement {
            if (index < 0) {
                return resolveSentinel(index)
            }

            if (index >= values.size || visiting[index]) {
                return JsonNull
            }

            if (cached[index]) {
                return cache[index] ?: JsonNull
            }

            visiting[index] = true
            val result = try {
                hydrate(values[index], values, promises, ::resolve)
            } finally {
                visiting[index] = false
            }

            cache[index] = result
            cached[index] = true
            return result
        }

        return resolve(0)
    }

    private fun hydrate(
        item: JsonElement,
        values: List<JsonElement>,
        promises: Map<Int, Int>,
        resolve: (Int) -> JsonElement,
    ): JsonElement = when (item) {
        is JsonNull -> JsonNull
        is JsonPrimitive -> item
        is JsonArray -> hydrateArray(item, values, promises, resolve)
        is JsonObject -> hydrateObject(item, values, resolve)
    }

    private fun hydrateArray(
        array: JsonArray,
        values: List<JsonElement>,
        promises: Map<Int, Int>,
        resolve: (Int) -> JsonElement,
    ): JsonElement {
        if (array.isEmpty()) return JsonArray(emptyList())

        val first = array[0]
        if (first is JsonPrimitive && first.isString) {
            val type = first.content
            val reviver = revivers[type]
            if (reviver != null) {
                val revived = reviver.revive(type, array)
                if (revived != null) return revived
            }

            when (type) {
                "Date", "D", "BigInt", "URL", "URLSearchParams",
                "Temporal.Duration", "Temporal.Instant", "Temporal.PlainDate",
                "Temporal.PlainTime", "Temporal.PlainDateTime", "Temporal.PlainMonthDay",
                "Temporal.PlainYearMonth", "Temporal.ZonedDateTime", "ArrayBuffer",
                -> {
                    return array.getOrNull(1) ?: JsonNull
                }
                "Set" -> {
                    val list = ArrayList<JsonElement>(array.size - 1)
                    for (i in 1 until array.size) {
                        val refIdx = (array[i] as? JsonPrimitive)?.intOrNull ?: continue
                        list.add(resolve(refIdx))
                    }
                    return JsonArray(list)
                }
                "Map" -> {
                    val map = LinkedHashMap<String, JsonElement>()
                    for (i in 1 until array.size step 2) {
                        val kIdx = (array[i] as? JsonPrimitive)?.intOrNull ?: continue
                        val vIdx = (array.getOrNull(i + 1) as? JsonPrimitive)?.intOrNull ?: continue
                        val kElem = resolve(kIdx)
                        val kStr = when (kElem) {
                            is JsonPrimitive -> kElem.content
                            is JsonNull -> "null"
                            else -> kElem.toString()
                        }
                        map[kStr] = resolve(vIdx)
                    }
                    return JsonObject(map)
                }
                "RegExp" -> {
                    val pattern = (array.getOrNull(1) as? JsonPrimitive)?.content ?: ""
                    val flags = (array.getOrNull(2) as? JsonPrimitive)?.content ?: ""
                    return JsonPrimitive("/$pattern/$flags")
                }
                "Object" -> {
                    val wrappedIdx = (array.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: return JsonNull
                    return resolve(wrappedIdx)
                }
                "null" -> {
                    val map = LinkedHashMap<String, JsonElement>()
                    for (i in 1 until array.size step 2) {
                        val key = (array[i] as? JsonPrimitive)?.content ?: continue
                        val vIdx = (array.getOrNull(i + 1) as? JsonPrimitive)?.intOrNull ?: continue
                        map[key] = resolve(vIdx)
                    }
                    return JsonObject(map)
                }
                "P" -> {
                    val slotId = (array.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: return JsonNull
                    val targetIdx = promises[slotId] ?: return JsonNull
                    return resolve(targetIdx)
                }
                "Z" -> {
                    val targetIdx = (array.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: return JsonNull
                    return resolve(targetIdx)
                }
            }
        }

        if (first is JsonPrimitive && first.intOrNull == SPARSE) {
            val len = (array.getOrNull(1) as? JsonPrimitive)?.intOrNull ?: 0
            val safeLen = len.coerceIn(0, 100_000)
            val list = MutableList<JsonElement>(safeLen) { JsonNull }
            for (i in 2 until array.size step 2) {
                val idx = (array[i] as? JsonPrimitive)?.intOrNull ?: continue
                val valIdx = (array.getOrNull(i + 1) as? JsonPrimitive)?.intOrNull ?: continue
                if (idx in 0 until safeLen) {
                    list[idx] = resolve(valIdx)
                }
            }
            return JsonArray(list)
        }

        val list = ArrayList<JsonElement>(array.size)
        for (elem in array) {
            val idx = (elem as? JsonPrimitive)?.intOrNull
            if (idx != null) {
                list.add(resolve(idx))
            } else {
                list.add(elem)
            }
        }
        return JsonArray(list)
    }

    private fun hydrateObject(
        obj: JsonObject,
        values: List<JsonElement>,
        resolve: (Int) -> JsonElement,
    ): JsonObject {
        val map = LinkedHashMap<String, JsonElement>(obj.size)
        for ((k, v) in obj) {
            val keyIdx = if (k.startsWith("_")) k.substring(1).toIntOrNull() else null
            val keyName = if (keyIdx != null && keyIdx in values.indices && values[keyIdx] is JsonPrimitive && (values[keyIdx] as JsonPrimitive).isString) {
                (values[keyIdx] as JsonPrimitive).content
            } else {
                k
            }

            val valIdx = (v as? JsonPrimitive)?.intOrNull
            val resolvedVal = if (valIdx != null) {
                resolve(valIdx)
            } else {
                v
            }
            map[keyName] = resolvedVal
        }
        return JsonObject(map)
    }
}
