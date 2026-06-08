package eu.kanade.tachiyomi.multisrc.uzaymanga

import keiyoushi.utils.tryParse
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@Serializable
class SvelteResponse(
    private val type: String,
    private val nodes: List<SvelteNode>? = null,
) {
    fun getData(): JsonArray? = nodes?.lastOrNull { it.getType() == "data" }?.getData()
}

@Serializable
class SvelteNode(
    private val type: String,
    private val data: JsonArray? = null,
) {
    fun getType() = type
    fun getData() = data
}

/**
 * Helper class to navigate SvelteKit's 'devalue' serialized data array.
 * In this format, object properties are represented as integer indices pointing to other elements in the array.
 */
class SvelteData(private val array: JsonArray) {

    fun getObject(index: Int) = array.getOrNull(index)?.let {
        if (it !is JsonPrimitive && it !is JsonNull) it.jsonObject else null
    }

    fun getArray(index: Int) = array.getOrNull(index)?.let {
        if (it !is JsonPrimitive && it !is JsonNull) it.jsonArray else null
    }

    fun getString(index: Int): String? {
        val el = array.getOrNull(index) ?: return null
        if (el !is JsonPrimitive || el is JsonNull) return null
        return el.content
    }

    fun getInt(index: Int): Int? {
        val el = array.getOrNull(index) ?: return null
        if (el !is JsonPrimitive || el is JsonNull) return null
        return el.intOrNull
    }

    fun resolveObject(node: JsonObject, key: String) = node[key]?.jsonPrimitive?.intOrNull?.let { getObject(it) }
    fun resolveArray(node: JsonObject, key: String) = node[key]?.jsonPrimitive?.intOrNull?.let { getArray(it) }
    fun resolveString(node: JsonObject, key: String) = node[key]?.jsonPrimitive?.intOrNull?.let { getString(it) }
    fun resolveInt(node: JsonObject, key: String) = node[key]?.jsonPrimitive?.intOrNull?.let { getInt(it) }

    fun resolveDate(node: JsonObject, key: String): Long {
        val dateArray = resolveArray(node, key) ?: return 0L
        if (dateArray.size < 2) return 0L
        val el = dateArray[1]
        val dateString = if (el is JsonPrimitive && el !is JsonNull) el.content else return 0L
        return dateFormat.tryParse(dateString)
    }

    companion object {
        private val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ROOT).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
    }
}
