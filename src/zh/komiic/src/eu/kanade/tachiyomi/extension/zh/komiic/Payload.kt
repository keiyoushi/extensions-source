package eu.kanade.tachiyomi.extension.zh.komiic

import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
class Payload<T>(
    val operationName: String,
    val variables: T,
    val query: String,
)

@Serializable
class Pagination(
    val offset: Int,
    val orderBy: String,
    @EncodeDefault
    val limit: Int = Komiic.PAGE_SIZE,
    @EncodeDefault
    val status: String = "",
    @EncodeDefault
    val asc: Boolean = true,
)

class Variables {

    val variableMap = mutableMapOf<String, JsonElement>()

    inline fun <reified T> set(key: String, value: T): Variables {
        variableMap[key] = Json.encodeToJsonElement(value)
        return this
    }

    fun build() = JsonObject(variableMap)
}
