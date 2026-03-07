package eu.kanade.tachiyomi.extension.ar.mangapro

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonTransformingSerializer
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Serializable
class MetaData<T>(
    val data: List<T>,
    val meta: Meta,
) {
    @Serializable
    class Meta(
        private val pages: Int,
        private val page: Int,
    ) {
        fun hasNextPage() = pages > page
    }
}

@Serializable
class Data<T>(
    val data: T,
)

@Serializable
class BrowseManga(
    val id: Int,
    val title: String,
    val slug: String,
    val type: String,
    val progress: String? = null,
    val metadata: MetaData,
    val coverImage: String? = null,
    val coverImageApp: CoverImage? = null,
    @SerialName("cdn_path")
    val cdn: String? = null,
) {
    @Serializable
    class MetaData(
        val genres: Set<String> = emptySet(),
        val tags: Set<String> = emptySet(),
    )
}

@Serializable
class CoverImage(
    val desktop: String? = null,
)

@Serializable
class Series(
    val series: Manga,
) {
    @Serializable
    class Manga(
        val id: Int,
        val title: String,
        val slug: String,
        val type: String,
        val description: String? = null,
        val progress: String? = null,
        val metadata: MetaData,
        @SerialName("cdn_path")
        val cdn: String? = null,
        val coverImageApp: CoverImage? = null,
    ) {
        @Serializable
        class MetaData(
            val originalTitle: String? = null,
            val altTitles: List<String> = emptyList(),
            @Serializable(with = StringListSerializer::class)
            val author: List<String> = emptyList(),
            @Serializable(with = StringListSerializer::class)
            val artist: List<String> = emptyList(),
            val year: String? = null,
            val genres: List<String> = emptyList(),
            val tags: List<String> = emptyList(),
            val origin: String? = null,
            val coverImage: String? = null,
        )
    }
}

object StringListSerializer : JsonTransformingSerializer<List<String>>(ListSerializer(String.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = when {
        element is JsonPrimitive -> {
            val elements = element.contentOrNull
                ?.split("\n")
                ?.map { JsonPrimitive(it.trim()) }
                .orEmpty()

            JsonArray(elements)
        }
        else -> element
    }
}

@Serializable
class InitialChapters(
    val initialChapters: List<Chapter>,
    val totalChapters: Int,
)

@Serializable
class Chapter(
    val id: Int,
    @SerialName("chapter_number")
    val number: String,
    val language: String,
    val title: String? = null,
    @SerialName("coins_required")
    val coins: Int? = null,
    @SerialName("uploader_nickname")
    val uploader: String? = null,
    @SerialName("created_at")
    val createdAt: String? = null,
)

@Serializable
class ChapterUrl(
    val url: String,
)

@Serializable
class Images(
    val images: List<String>,
    @Serializable(DeferredMediaSerializer::class)
    val deferredMedia: DeferredMediaToken? = null,
)

@Serializable
class DeferredMediaToken(
    val token: String,
)

object DeferredMediaSerializer : KSerializer<DeferredMediaToken?> {
    override val descriptor = DeferredMediaToken.serializer().descriptor

    override fun deserialize(decoder: Decoder): DeferredMediaToken? {
        val jsonDecoder = decoder as JsonDecoder
        val element = jsonDecoder.decodeJsonElement()
        return when {
            element is JsonPrimitive -> null
            else -> jsonDecoder.json.decodeFromJsonElement(DeferredMediaToken.serializer(), element)
        }
    }

    override fun serialize(encoder: Encoder, value: DeferredMediaToken?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            DeferredMediaToken.serializer().serialize(encoder, value)
        }
    }
}

@Serializable
class DeferredImages(
    val images: List<String>,
    @Serializable(ScrambledDataSerializer::class)
    val maps: List<ScrambledData>,
)

@Serializable
sealed class ScrambledData

@Serializable
@SerialName("direct")
class ScrambledImage(
    val mode: String,
    val order: List<Int>,
    val pieces: List<String>,
    val dim: List<Int>,
) : ScrambledData()

@Serializable
@SerialName("indirect")
class ScrambledImageToken(
    val token: String,
    val method: String,
) : ScrambledData()

object ScrambledDataSerializer : JsonTransformingSerializer<List<ScrambledData>>(ListSerializer(ScrambledData.serializer())) {
    override fun transformDeserialize(element: JsonElement): JsonElement = JsonArray(
        element.jsonArray.map { jsonElement ->
            val jsonObject = jsonElement.jsonObject
            when {
                "method" in jsonObject -> JsonObject(
                    jsonObject + ("type" to JsonPrimitive("indirect")),
                )

                else -> JsonObject(
                    jsonObject + ("type" to JsonPrimitive("direct")),
                )
            }
        },
    )
}

@Serializable
class ScrambledImageTokenValue(
    val cid: Int,
    val data: String,
    val iv: String,
    val m: String,
    val tag: String,
    val v: Int,
)

@Serializable
class Key(
    val key: String,
)

@Serializable
class Coins(
    val coins: Int,
)

@Serializable
class Url(
    val url: String,
)

@Serializable
class Token(
    val token: String,
    val expires: Long,
)

@Serializable
class ViewsDto(
    val chapterId: Int? = null,
    val contentId: Int,
    val deviceType: String,
    val surface: String,
)
