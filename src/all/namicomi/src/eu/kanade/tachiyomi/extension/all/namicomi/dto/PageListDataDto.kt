package eu.kanade.tachiyomi.extension.all.namicomi.dto

import eu.kanade.tachiyomi.extension.all.namicomi.NamicomiConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias PageListDto = ResponseDto<PageListDataDto>

@Serializable
@SerialName(NamicomiConstants.imageData)
data class PageListDataDto(
    override val attributes: AttributesDto? = null,
    val baseUrl: String,
    val hash: String,
    val source: List<PageImageDto>,
    val high: List<PageImageDto>,
    val medium: List<PageImageDto>,
    val low: List<PageImageDto>,
) : EntityDto()

@Serializable
data class PageImageDto(
    val size: Int?,
    val filename: String,
    val resolution: String?,
)
