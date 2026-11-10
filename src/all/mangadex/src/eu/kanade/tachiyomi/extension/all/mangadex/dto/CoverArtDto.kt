package eu.kanade.tachiyomi.extension.all.mangadex.dto

import eu.kanade.tachiyomi.extension.all.mangadex.MDConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias CoverArtListDto = PaginatedResponseDto<CoverArtDto>

@Serializable
@SerialName(MDConstants.COVER_ART)
class CoverArtDto(override val attributes: CoverArtAttributesDto? = null) : EntityDto()

@Serializable
class CoverArtAttributesDto(
    val fileName: String? = null,
    val locale: String? = null,
) : AttributesDto()
