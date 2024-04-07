package eu.kanade.tachiyomi.extension.en.comicfans

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull

// Credit: @AntsyLich
class Parser(private val data: JsonArray) {
    private val visited = HashSet<Int>()

    fun parse(): List<JsonElement> {
        return data.mapIndexedNotNull { index, element ->
            if (index !in visited) {
                parseSubScramble(element)
            } else {
                null
            }
        }
    }

    private fun parseSubScramble(data: JsonElement): JsonElement {
        var parsedData = data
        if (data is JsonPrimitive && data.intOrNull != null) {
            visited.add(data.int)
            parsedData = this.data[data.int]
        }

        return when (parsedData) {
            is JsonArray -> parseList(parsedData)
            is JsonObject -> parseDict(parsedData)
            else -> parsedData
        }
    }

    private fun parseList(currentList: JsonArray): JsonArray {
        return JsonArray(currentList.map(::parseSubScramble))
    }

    private fun parseDict(currentDict: JsonObject): JsonObject {
        return buildJsonObject {
            currentDict.entries.forEach {
                put(it.key, parseSubScramble(it.value))
            }
        }
    }
}
