package eu.kanade.tachiyomi.extension.zh.toptoon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class PopularResponseDto(val adult: List<MangaDto>)

@Serializable
data class MangaDto(val meta: MetaDto, val thumbnail: ThumbnailDto, val id: String, val lastUpdated: LastUpdatedDto) {
    val url = "/comic/epList/$id"
}

@Serializable
data class MetaDto(val title: String, val author: AuthorDto)

@Serializable
data class AuthorDto(val authorString: String)

@Serializable
// "standard" in json can be either string or array, always decode as string
data class ThumbnailDto(@Serializable(with = StringOrArraySerializer::class) val standard: String) {
    val url = "https://tw-contents-image.toptoon.net$standard"
}

@Serializable
data class LastUpdatedDto(val pubDate: String)

object StringOrArraySerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("StringOrArray", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when (element) {
            is JsonPrimitive -> element.content
            is JsonArray -> element[0].jsonPrimitive.content
            else -> throw Exception("Unexpected JSON type")
        }
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}
