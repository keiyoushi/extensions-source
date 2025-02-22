package eu.kanade.tachiyomi.extension.all.mangadex.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.extension.all.mangadex.MDConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias CoverArtListDto = PaginatedResponseDto<CoverArtDto>

@Serializable
@SerialName(MDConstants.coverArt)
data class CoverArtDto(override val attributes: CoverArtAttributesDto? = null) : EntityDto()

@Serializable
data class CoverArtAttributesDto(
    val fileName: String? = null,
    val locale: String? = null,
) : AttributesDto()
