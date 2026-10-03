package eu.kanade.tachiyomi.extension.all.namicomi.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias MangaListDto = PaginatedResponseDto<MangaDataDto>

typealias MangaDto = ResponseDto<MangaDataDto>

@Serializable
@SerialName("title")
class MangaDataDto(override val attributes: MangaAttributesDto? = null) : EntityDto()

@Serializable
class MangaAttributesDto(
    val title: Map<String, String>,
    val description: Map<String, String>,
    val originalLanguage: String?,
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
@SerialName("tag")
class TagDto : AbstractTagDto()

@Serializable
@SerialName("primary_tag")
class PrimaryTagDto : AbstractTagDto()

@Serializable
@SerialName("secondary_tag")
class SecondaryTagDto : AbstractTagDto()

@Serializable
class TagAttributesDto(
    val group: String,
    val name: Map<String, String>,
) : AttributesDto
