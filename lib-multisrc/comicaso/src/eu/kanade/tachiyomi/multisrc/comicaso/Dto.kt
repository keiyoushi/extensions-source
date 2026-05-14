package eu.kanade.tachiyomi.multisrc.comicaso

import eu.kanade.tachiyomi.source.model.SChapter
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
import kotlinx.serialization.json.JsonPrimitive

object SafeStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("SafeString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value)
        }
    }

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder ?: return try {
            decoder.decodeString()
        } catch (_: Exception) {
            null
        }

        return try {
            val element = jsonDecoder.decodeJsonElement()
            if (element is JsonPrimitive && element.isString) {
                element.content
            } else {
                null
            }
        } catch (_: Exception) {
            null
        }
    }
}

@Serializable
class MangaDto(
    val slug: String,
    val title: String,
    @Serializable(SafeStringSerializer::class)
    val thumbnail: String? = null,
    val status: String? = null,
    val type: String? = null,
    val genres: List<String>? = emptyList(),
    @SerialName("manga_date") val mangaDate: Long? = null,
    @SerialName("updated_at") val updatedAt: Long? = null,
) {
    fun toSManga() = SManga.create().apply {
        url = slug
        title = this@MangaDto.title
        thumbnail_url = thumbnail
        genre = genres?.joinToString()
        status = when (this@MangaDto.status) {
            "on-going" -> SManga.ONGOING
            "end" -> SManga.COMPLETED
            else -> SManga.UNKNOWN
        }
    }
}

@Serializable
class MangaDetailDto(
    val slug: String,
    val title: String,
    @Serializable(SafeStringSerializer::class)
    val thumbnail: String? = null,
    val synopsis: String? = null,
    val alternative: String? = null,
    val status: String? = null,
    val author: String? = null,
    val artist: String? = null,
    val genres: List<String>? = emptyList(),
    val chapters: List<ChapterDto>? = emptyList(),
)

@Serializable
class ChapterDto(
    val slug: String,
    val title: String,
    val date: Long? = null,
) {
    fun toSChapter(mangaSlug: String) = SChapter.create().apply {
        url = "/komik/$mangaSlug/$slug/"
        name = title
        date_upload = date?.let { it * 1000L } ?: 0L
    }
}

@Serializable
class ChapterImagesDto(
    val images: List<String>,
)
