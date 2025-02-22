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

typealias PageListDto = ResponseDto<PageListDataDto>

@Serializable
@SerialName(NamiComiConstants.imageData)
class PageListDataDto(
    override val attributes: AttributesDto? = null,
    val baseUrl: String,
    val hash: String,
    val source: List<PageImageDto>,
    val high: List<PageImageDto>,
    val medium: List<PageImageDto>,
    val low: List<PageImageDto>,
) : EntityDto()

@Serializable
class PageImageDto(
    val size: Int?,
    val filename: String,
    val resolution: String?,
)
