package eu.kanade.tachiyomi.extension.ar.mangatek

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

class WrappedSerializer<T>(val dataSerializer: KSerializer<T>) : KSerializer<Wrapped<T>> {
    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("Wrapped")

    override fun deserialize(decoder: Decoder): Wrapped<T> {
        val input = decoder as? JsonDecoder ?: throw SerializationException("Expected Json Decoder")
        val array = input.decodeJsonElement().jsonArray

        // array[0] is the index, array[1] is the content
        val index = array[0].jsonPrimitive.int
        val value = input.json.decodeFromJsonElement(dataSerializer, array[1])

        return Wrapped(index, value)
    }

    override fun serialize(encoder: Encoder, value: Wrapped<T>) = throw SerializationException("Serialization is not supported")
}

@Serializable(with = WrappedSerializer::class)
class Wrapped<T>(
    val index: Int,
    val value: T,
)

@Serializable
class MangaWrapper(
    val manga: Wrapped<MangaData>,
)

@Serializable
class MangaData(
    @SerialName("MangaChapters")
    val mangaChapters: Wrapped<List<Wrapped<ChapterItem>>>,
)

@Serializable
class ChapterItem(
    @SerialName("chapter_number") val chapterNumber: Wrapped<String>,
    val title: Wrapped<String?>,
    @SerialName("created_at") val createdAt: Wrapped<String?>,
)
