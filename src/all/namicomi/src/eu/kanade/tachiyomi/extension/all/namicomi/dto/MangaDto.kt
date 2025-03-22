package eu.kanade.tachiyomi.extension.all.namicomi.dto

import eu.kanade.tachiyomi.extension.all.namicomi.NamiComiConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias MangaListDto = PaginatedResponseDto<MangaDataDto>

typealias MangaDto = ResponseDto<MangaDataDto>

@Serializable
@SerialName(NamiComiConstants.manga)
class MangaDataDto(override val attributes: MangaAttributesDto? = null) : EntityDto()

@Serializable
class MangaAttributesDto(
    // Title and description are maps of language codes to localized strings
    val title: Map<String, String>,
    val description: Map<String, String>,
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
