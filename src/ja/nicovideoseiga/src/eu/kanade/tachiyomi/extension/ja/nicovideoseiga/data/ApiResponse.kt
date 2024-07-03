package eu.kanade.tachiyomi.extension.ja.nicovideoseiga.data

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.encodeToJsonElement

@Serializable
data class ApiResponse<T>(
    val meta: Meta,
    val data: Data<T>,
)

@Serializable
data class Meta(
    val status: Int,
)

@Serializable
data class Data<T>(
    @Serializable(with = SingleResultSerializer::class)
    val result: List<T>,
    val extra: Extra? = null,
)

@Serializable
data class Extra(
    @SerialName("has_next")
    val hasNext: Boolean,
)

class SingleResultSerializer<T>(private val serializer: KSerializer<T>) : KSerializer<List<T>> {
    // Wrap single results in a list. Leave multiple results as is.
    override val descriptor = serializer.descriptor

    override fun serialize(encoder: Encoder, value: List<T>) {
        val jsonEncoder = encoder as JsonEncoder
        if (value.size == 1) {
            jsonEncoder.json.encodeToJsonElement(jsonEncoder.json.encodeToJsonElement(serializer, value.first()))
        } else {
            jsonEncoder.json.encodeToJsonElement(JsonArray(value.map { jsonEncoder.json.encodeToJsonElement(serializer, it) }))
        }
    }

    override fun deserialize(decoder: Decoder): List<T> {
        val jsonDecoder = decoder as JsonDecoder
        return when (val jsonElement = jsonDecoder.decodeJsonElement()) {
            is JsonArray -> jsonElement.map { jsonDecoder.json.decodeFromJsonElement(serializer, it) }
            else -> listOf(jsonDecoder.json.decodeFromJsonElement(serializer, jsonElement))
        }
    }
}
