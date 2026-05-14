package eu.kanade.tachiyomi.extension.fr.poseidonscans

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull

@Serializable
class LatestApiManga(
    val title: String,
    val slug: String,
)

@Serializable
class LatestApiResponse(
    val data: List<LatestApiManga> = emptyList(),
)

@Serializable
class PopularMangaData(
    val id: String,
    val title: String,
    val slug: String,
)

@Serializable
class MangaPageDetailsData(
    @Serializable(with = LenientBooleanSerializer::class)
    val isPremiumUser: Boolean = false,
    val manga: MangaDetailsData,
)

@Serializable
class MangaDetailsData(
    val title: String,
    val slug: String,
    val description: String = "",
    val status: String? = null,
    val artist: String? = null,
    val author: String? = null,
    val categories: List<CategoryData> = emptyList(),
    val chapters: List<ChapterData> = emptyList(),
)

@Serializable
class CategoryData(val name: String)

@Serializable
class ChapterData(
    val number: Float,
    val title: String? = null,
    val createdAt: String,
    val isPremium: Boolean? = false,
    val premiumUntil: String? = null,
    val isVolume: Boolean? = false,
)

@Serializable
class PageData(
    val currentChapter: CurrentChapterData,
    val initialData: InitialData,
    @Serializable(with = LenientBooleanSerializer::class)
    var isPremiumUser: Boolean = false,
    var sessionStatus: String = "",
)

@Serializable
class CurrentChapterData(
    val isPremium: Boolean,
)

@Serializable
class InitialData(
    val images: List<ImageData>,
)

@Serializable
class ImageData(
    val originalUrl: String,
    val order: Int,
)

// Next.js RSC payloads may emit the literal string "$undefined" for an
// unresolved boolean; treat anything that isn't a real boolean as false.
object LenientBooleanSerializer : KSerializer<Boolean> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LenientBoolean", PrimitiveKind.BOOLEAN)

    override fun deserialize(decoder: Decoder): Boolean {
        val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeBoolean()
        return (jsonDecoder.decodeJsonElement() as? JsonPrimitive)?.booleanOrNull ?: false
    }

    override fun serialize(encoder: Encoder, value: Boolean) = encoder.encodeBoolean(value)
}
