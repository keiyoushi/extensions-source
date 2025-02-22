package eu.kanade.tachiyomi.extension.all.namicomi.dto
import keiyoushi.utils.getPreferencesLazy
import keiyoushi.utils.getPreferences
import keiyoushi.utils.parseAs
import keiyoushi.utils.tryParse
import keiyoushi.utils.firstInstance
import keiyoushi.utils.firstInstanceOrNull

import eu.kanade.tachiyomi.extension.all.namicomi.NamiComiConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@SerialName(NamiComiConstants.coverArt)
class CoverArtDto(override val attributes: CoverArtAttributesDto? = null) : EntityDto()

@Serializable
class CoverArtAttributesDto(
    val fileName: String? = null,
    val locale: String? = null,
) : AttributesDto
