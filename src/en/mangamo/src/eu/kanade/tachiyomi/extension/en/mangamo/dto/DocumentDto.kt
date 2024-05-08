package eu.kanade.tachiyomi.extension.en.mangamo.dto

import kotlinx.serialization.Contextual
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
class DocumentWrapper<T>(val document: DocumentDto<T>?)

typealias QueryResultDto<T> = @Serializable List<DocumentWrapper<T>>

val <T>QueryResultDto<T>.documents
    get() = mapNotNull { it.document }

val <T>QueryResultDto<T>.elements
    get() = mapNotNull { it.document?.fields }

typealias DocumentDto<T> = @Contextual DocumentDtoInternal<T>

@Serializable
class DocumentDtoInternal<T>(
    val fields: T,
)

class DocumentSerializer(dataSerializer: KSerializer<out DocumentDto<out Any?>>) :
    JsonTransformingSerializer<DocumentDto<Any>>(dataSerializer as KSerializer<DocumentDto<Any>>) {

    override fun transformDeserialize(element: JsonElement): JsonElement {
        val objMap = element.jsonObject.toMap(HashMap())

        if (objMap.containsKey("fields")) {
            objMap["fields"] = reduceFieldsObject(objMap["fields"]!!)
        } else {
            objMap["fields"] = JsonObject(mapOf())
        }

        return JsonObject(objMap)
    }

    private fun reduceFieldsObject(fields: JsonElement): JsonElement {
        return JsonObject(fields.jsonObject.mapValues { reduceField(it.value) })
    }

    private fun reduceField(element: JsonElement): JsonElement {
        val valueContainer = element.jsonObject.entries.first()

        return when (valueContainer.key) {
            "arrayValue" -> valueContainer.value.jsonObject["values"]?.jsonArray
                ?.map { reduceField(it) }
                .let { JsonArray(it ?: listOf()) }
            "mapValue" -> reduceFieldsObject(valueContainer.value.jsonObject["fields"]!!)
            else -> valueContainer.value
        }
    }
}
