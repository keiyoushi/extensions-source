package eu.kanade.tachiyomi.extension.zh.komiic

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class Payload<T>(
    val operationName: String,
    val variables: T,
    val query: String,
)

@Serializable
data class Pagination(
    val offset: Int,
    val orderBy: String,
    @EncodeDefault
    val status: String = "",
    @EncodeDefault
    val asc: Boolean = true,
    @EncodeDefault
    val limit: Int = Komiic.PAGE_SIZE,
)

class Variables {
    companion object {
        val EMPTY = Variables().build()
    }

    val variableMap = mutableMapOf<String, JsonElement>()

    inline fun <reified T> set(key: String, value: T): Variables {
        variableMap[key] = Json.encodeToJsonElement(value)
        return this
    }

    fun build() = JsonObject(variableMap)
}
