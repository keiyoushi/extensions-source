package eu.kanade.tachiyomi.extension.all.namicomi.dto

import eu.kanade.tachiyomi.extension.all.namicomi.NamiComiConstants
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.serializer

typealias MangaListDto = PaginatedResponseDto<MangaDataDto>

typealias MangaDto = ResponseDto<MangaDataDto>

@Serializable
@SerialName(NamiComiConstants.manga)
data class MangaDataDto(override val attributes: MangaAttributesDto? = null) : EntityDto()

@Serializable
data class MangaAttributesDto(
    val title: LocalizedString,
    val description: LocalizedString,
    val slug: String,
    val originalLanguage: String?,
    val year: Int?,
    val contentRating: ContentRatingDto? = null,
    val publicationStatus: StatusDto? = null,
) : AttributesDto

@Serializable
enum class ContentRatingDto(val value: String) {
    @SerialName("safe")
    SAFE("safe"),

    @SerialName("restricted")
    RESTRICTED("restricted"),

    @SerialName("mature")
    MATURE("mature"),
}

@Serializable
enum class StatusDto(val value: String) {
    @SerialName("ongoing")
    ONGOING("ongoing"),

    @SerialName("completed")
    COMPLETED("completed"),

    @SerialName("hiatus")
    HIATUS("hiatus"),

    @SerialName("cancelled")
    CANCELLED("cancelled"),
}

@Serializable
sealed class AbstractTagDto(override val attributes: TagAttributesDto? = null) : EntityDto()

@Serializable
@SerialName(NamiComiConstants.tag)
class TagDto : AbstractTagDto()

@Serializable
@SerialName(NamiComiConstants.primaryTag)
class PrimaryTagDto : AbstractTagDto()

@Serializable
@SerialName(NamiComiConstants.secondaryTag)
class SecondaryTagDto : AbstractTagDto()

@Serializable
class TagAttributesDto(val group: String) : AttributesDto

typealias LocalizedString = @Serializable(LocalizedStringSerializer::class)
Map<String, String>

/**
 * Titles and descriptions are dictionaries with language codes as keys and the text as values.
 */
object LocalizedStringSerializer : KSerializer<Map<String, String>> {
    override val descriptor = buildClassSerialDescriptor("LocalizedString")

    override fun deserialize(decoder: Decoder): Map<String, String> {
        require(decoder is JsonDecoder)

        return (decoder.decodeJsonElement() as? JsonObject)
            ?.mapValues { it.value.jsonPrimitive.contentOrNull ?: "" }
            .orEmpty()
    }

    override fun serialize(encoder: Encoder, value: Map<String, String>) {
        encoder.encodeSerializableValue(serializer(), value)
    }
}
