package eu.kanade.tachiyomi.extension.all.mangadex.dto

import eu.kanade.tachiyomi.extension.all.mangadex.MDConstants
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
@SerialName(MDConstants.manga)
data class MangaDataDto(override val attributes: MangaAttributesDto? = null) : EntityDto()

@Serializable
data class MangaAttributesDto(
    val title: LocalizedString,
    val altTitles: List<LocalizedString>,
    val description: LocalizedString,
    val originalLanguage: String?,
    val lastVolume: String?,
    val lastChapter: String?,
    val contentRating: ContentRatingDto? = null,
    val publicationDemographic: PublicationDemographicDto? = null,
    val status: StatusDto? = null,
    val tags: List<TagDto>,
) : AttributesDto()

@Serializable
enum class ContentRatingDto(val value: String) {
    @SerialName("safe")
    SAFE("safe"),

    @SerialName("suggestive")
    SUGGESTIVE("suggestive"),

    @SerialName("erotica")
    EROTICA("erotica"),

    @SerialName("pornographic")
    PORNOGRAPHIC("pornographic"),
}

@Serializable
enum class PublicationDemographicDto(val value: String) {
    @SerialName("none")
    NONE("none"),

    @SerialName("shounen")
    SHOUNEN("shounen"),

    @SerialName("shoujo")
    SHOUJO("shoujo"),

    @SerialName("josei")
    JOSEI("josei"),

    @SerialName("seinen")
    SEINEN("seinen"),
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
@SerialName(MDConstants.tag)
data class TagDto(override val attributes: TagAttributesDto? = null) : EntityDto()

@Serializable
data class TagAttributesDto(val group: String) : AttributesDto()

typealias LocalizedString = @Serializable(LocalizedStringSerializer::class)
Map<String, String>

/**
 * Temporary workaround while Dex API still returns arrays instead of objects
 * in the places that uses [LocalizedString].
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
