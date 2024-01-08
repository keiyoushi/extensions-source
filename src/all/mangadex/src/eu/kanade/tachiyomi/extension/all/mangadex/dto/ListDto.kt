package eu.kanade.tachiyomi.extension.all.mangadex.dto

import eu.kanade.tachiyomi.extension.all.mangadex.MDConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

typealias ListDto = ResponseDto<ListDataDto>

@Serializable
@SerialName(MDConstants.list)
data class ListDataDto(override val attributes: ListAttributesDto? = null) : EntityDto()

@Serializable
data class ListAttributesDto(
    val name: String,
    val visibility: String,
    val version: Int,
) : AttributesDto()
