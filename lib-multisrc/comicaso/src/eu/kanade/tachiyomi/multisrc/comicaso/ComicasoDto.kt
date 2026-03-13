package eu.kanade.tachiyomi.multisrc.comicaso

import eu.kanade.tachiyomi.source.model.SManga
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive

object FlexibleStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        encoder.encodeString(value.orEmpty())
    }

    override fun deserialize(decoder: Decoder): String? {
        if (decoder !is JsonDecoder) return decoder.decodeString()

        val element = decoder.decodeSerializableValue(JsonElement.serializer())

        return when (element) {
            JsonNull -> null
            is JsonPrimitive -> {
                if (element.isString) {
                    element.content
                } else {
                    element.content
                        .takeUnless {
                            it.equals("false", ignoreCase = true) ||
                                it.equals("null", ignoreCase = true) ||
                                it.isBlank()
                        }
                }
            }

            else -> null
        }
    }
}

@Serializable
class MangaDto(
    val slug: String,
    val title: String,
    @Serializable(with = FlexibleStringSerializer::class)
    val thumbnail: String? = null,
    val status: String,
    val alternative: String,
    val artist: String,
    val author: String,
    val genres: List<String> = emptyList(),
    val type: String,
    @SerialName("updated_at")
    val updatedAt: Long? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = "/komik/$slug/"
        title = this@MangaDto.title
        thumbnail_url = thumbnail.orEmpty()
        status = when (this@MangaDto.status.lowercase()) {
            "on-going", "ongoing" -> SManga.ONGOING
            "completed", "end" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class MangaDetailsDto(
    val slug: String,
    val chapters: List<ChapterDto> = emptyList(),
)

@Serializable
class ChapterDto(
    val slug: String,
    val title: String,
    val date: Long,
)
